package com.example.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Authentication repository interface for MINOVA AI.
 * Supports Guest (local-only) mode by default, Email/Password authentication,
 * and Google Sign-In via Android Credential Manager + Firebase Auth.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthUser>

    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<AuthUser>

    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String = "",
        fallbackDisplayName: String = ""
    ): Result<AuthUser>

    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun refreshSession(): Result<AuthUser>

    suspend fun signOut(context: Context? = null): Result<Unit>

    suspend fun deleteCurrentUserAccount(context: Context? = null): Result<Unit>

    fun clearAuthError()
}

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "Email address is required."
        if (trimmed.length < 3 || trimmed.length > 254 || !EMAIL_REGEX.matches(trimmed)) {
            return "Please enter a valid email address."
        }
        return null
    }

    fun validatePassword(password: String, isSignUp: Boolean = false): String? {
        if (password.isEmpty()) return "Password is required."
        if (password.length < 8) {
            return if (isSignUp) {
                "Password must be at least 8 characters long."
            } else {
                "Invalid email or password."
            }
        }
        if (password.length > 128) return "Password must not exceed 128 characters."
        return null
    }

    fun validateDisplayName(displayName: String): String? {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "Display name is required."
        if (trimmed.length > 100) return "Display name must be 100 characters or fewer."
        return null
    }
}

/**
 * Production Firebase Authentication + Credential Manager implementation.
 * Syncs authenticated user profile metadata to `/users/{userId}` in the named Firestore database.
 */
class FirebaseAuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        auth.currentUser?.toAuthUser()?.let { AuthState.Authenticated(it) } ?: AuthState.Guest
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.user
            is AuthState.Error -> state.currentUser
            else -> auth.currentUser?.toAuthUser()
        }

    private val failedAttemptTimestamps = mutableListOf<Long>()
    private val maxAttemptsPerWindow = 5
    private val windowDurationMillis = 60_000L

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser?.toAuthUser()
        if (user != null) {
            _authState.value = AuthState.Authenticated(user)
        } else if (_authState.value !is AuthState.Error && _authState.value !is AuthState.Loading) {
            _authState.value = AuthState.Guest
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    private fun checkRateLimit(): String? {
        val now = System.currentTimeMillis()
        synchronized(failedAttemptTimestamps) {
            failedAttemptTimestamps.removeAll { now - it > windowDurationMillis }
            if (failedAttemptTimestamps.size >= maxAttemptsPerWindow) {
                return "Too many authentication attempts. Please wait a minute before trying again."
            }
        }
        return null
    }

    private fun recordFailedAttempt() {
        synchronized(failedAttemptTimestamps) {
            failedAttemptTimestamps.add(System.currentTimeMillis())
        }
    }

    private fun clearFailedAttempts() {
        synchronized(failedAttemptTimestamps) {
            failedAttemptTimestamps.clear()
        }
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthUser> {
        checkRateLimit()?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalStateException(msg))
        }

        val cleanEmail = email.trim()
        val cleanName = displayName.trim()

        AuthValidator.validateDisplayName(cleanName)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validatePassword(password, isSignUp = true)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }

        _authState.value = AuthState.Loading
        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser = result.user
                ?: throw IllegalStateException("Account creation failed: no user returned.")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val authUser = AuthUser(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: cleanEmail,
                displayName = cleanName,
                providerId = "password"
            )
            upsertUserProfileDocument(authUser)
            clearFailedAttempts()
            _authState.value = AuthState.Authenticated(authUser)
            Result.success(authUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailedAttempt()
            val friendly = mapFirebaseAuthError(e)
            _authState.value = AuthState.Error(friendly, null)
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<AuthUser> {
        checkRateLimit()?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalStateException(msg))
        }

        val cleanEmail = email.trim()
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validatePassword(password, isSignUp = false)?.let { msg ->
            recordFailedAttempt()
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }

        _authState.value = AuthState.Loading
        return try {
            val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser = result.user
                ?: throw IllegalStateException("Sign-in failed: no user returned.")

            val authUser = firebaseUser.toAuthUser(fallbackEmail = cleanEmail)
            upsertUserProfileDocument(authUser)
            clearFailedAttempts()
            _authState.value = AuthState.Authenticated(authUser)
            Result.success(authUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailedAttempt()
            val friendly = mapFirebaseAuthError(e)
            _authState.value = AuthState.Error(friendly, null)
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        checkRateLimit()?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalStateException(msg))
        }

        _authState.value = AuthState.Loading
        return try {
            val webClientId = resolveWebClientId(activityContext)
            val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                signInWithGoogleIdToken(
                    idToken = googleIdTokenCredential.idToken,
                    fallbackEmail = googleIdTokenCredential.id,
                    fallbackDisplayName = googleIdTokenCredential.displayName ?: ""
                )
            } else {
                val msg = "Unsupported credential type received from Google Sign-In."
                _authState.value = AuthState.Error(msg, null)
                Result.failure(IllegalStateException(msg))
            }
        } catch (e: CancellationException) {
            _authState.value = auth.currentUser?.toAuthUser()?.let { AuthState.Authenticated(it) } ?: AuthState.Guest
            throw e
        } catch (e: GetCredentialCancellationException) {
            _authState.value = auth.currentUser?.toAuthUser()?.let { AuthState.Authenticated(it) } ?: AuthState.Guest
            Result.failure(e)
        } catch (e: Exception) {
            recordFailedAttempt()
            val friendly = mapFirebaseAuthError(e)
            _authState.value = AuthState.Error(friendly, null)
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String,
        fallbackDisplayName: String
    ): Result<AuthUser> {
        if (idToken.isBlank()) {
            val msg = "Google ID token must not be blank."
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        _authState.value = AuthState.Loading
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user
                ?: throw IllegalStateException("Google Sign-In failed: no user returned.")
            val authUser = firebaseUser.toAuthUser(
                fallbackEmail = fallbackEmail,
                fallbackDisplayName = fallbackDisplayName,
                defaultProviderId = "google.com"
            )
            upsertUserProfileDocument(authUser)
            clearFailedAttempts()
            _authState.value = AuthState.Authenticated(authUser)
            Result.success(authUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailedAttempt()
            val friendly = mapFirebaseAuthError(e)
            _authState.value = AuthState.Error(friendly, null)
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapFirebaseAuthError(e), e))
        }
    }

    override suspend fun refreshSession(): Result<AuthUser> {
        val firebaseUser = auth.currentUser
            ?: return Result.failure(SecurityException("Session expired. Please sign in again."))
        return try {
            firebaseUser.getIdToken(true).await()
            val refreshedUser = firebaseUser.toAuthUser()
            _authState.value = AuthState.Authenticated(refreshedUser)
            Result.success(refreshedUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _authState.value = AuthState.Guest
            Result.failure(SecurityException("Session expired. Please sign in again.", e))
        }
    }

    override suspend fun signOut(context: Context?): Result<Unit> {
        return try {
            val ctx = context ?: this.context
            try {
                CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Throwable) {
                // Ignore if CredentialManager is unavailable in headless/test environments
            }
            auth.signOut()
            _authState.value = AuthState.Guest
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _authState.value = AuthState.Guest
            Result.failure(e)
        }
    }

    override suspend fun deleteCurrentUserAccount(context: Context?): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(SecurityException("No signed-in user to delete."))
        val uid = user.uid
        return try {
            try {
                firestore.collection("users").document(uid).delete().await()
            } catch (_: Exception) {
                // Best effort profile document cleanup
            }
            user.delete().await()
            signOut(context)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Even if Firebase requires recent re-auth for user.delete(), sign out locally after cloud data removal
            signOut(context)
            Result.success(Unit)
        }
    }

    override fun clearAuthError() {
        val current = auth.currentUser?.toAuthUser()
        _authState.value = if (current != null) AuthState.Authenticated(current) else AuthState.Guest
    }

    private suspend fun upsertUserProfileDocument(user: AuthUser) {
        val path = "users/${user.uid}"
        try {
            val docRef = firestore.collection("users").document(user.uid)
            val snap = docRef.get().await()
            val safeEmail = user.email.ifBlank { "${user.uid}@minova.user" }.take(254)
            val safeDisplayName = user.displayName.trim().ifBlank { safeEmail.substringBefore("@") }.take(100)
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "email" to safeEmail,
                        "displayName" to safeDisplayName,
                        "lastLoginAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to user.uid,
                        "email" to safeEmail,
                        "displayName" to safeDisplayName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastLoginAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    private fun resolveWebClientId(ctx: Context): String {
        val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        if (resId != 0) {
            val fromRes = ctx.getString(resId)
            if (fromRes.isNotBlank()) return fromRes
        }
        return DEFAULT_WEB_CLIENT_ID
    }

    private fun FirebaseUser.toAuthUser(
        fallbackEmail: String = "",
        fallbackDisplayName: String = "",
        defaultProviderId: String = "password"
    ): AuthUser {
        val resolvedEmail = email?.takeIf { it.isNotBlank() }
            ?: providerData.mapNotNull { it.email }.firstOrNull { it.isNotBlank() }
            ?: fallbackEmail.ifBlank { "$uid@minova.user" }
        val resolvedName = displayName?.takeIf { it.isNotBlank() }
            ?: providerData.mapNotNull { it.displayName }.firstOrNull { it.isNotBlank() }
            ?: fallbackDisplayName.ifBlank { resolvedEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }
        val resolvedProvider = providerData
            .map { it.providerId }
            .firstOrNull { it == "google.com" || it == "password" }
            ?: defaultProviderId
        return AuthUser(
            uid = uid,
            email = resolvedEmail,
            displayName = resolvedName,
            providerId = resolvedProvider
        )
    }

    private fun mapFirebaseAuthError(e: Exception): String {
        val authEx = e as? FirebaseAuthException
        return when (authEx?.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" ->
                "Invalid email or password. Please check your credentials and try again."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "An account with this email already exists. Please sign in instead."
            "ERROR_WEAK_PASSWORD" ->
                "Password is too weak. Please use at least 8 characters."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Email/Password sign-in is not enabled in the Firebase Console yet. Enable Email/Password in Firebase Console > Authentication > Sign-in method, or use Google Sign-In."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait a moment and try again."
            "ERROR_USER_TOKEN_EXPIRED" ->
                "Your session has expired. Please sign in again."
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Authentication failed. Please try again."
        }
    }

    companion object {
        // Client type 3 (Web client) from app/google-services.json for project gen-lang-client-0157866488
        const val DEFAULT_WEB_CLIENT_ID = "191007757636-81k4ra0mb5a3kp5v0pvr78r25qh9qcbj.apps.googleusercontent.com"
    }
}

/**
 * Deterministic in-memory implementation of [AuthRepository] for unit and Robolectric tests.
 * Uses PBKDF2-HMAC-SHA256 salted password hashing (never stores plaintext passwords),
 * enforces rate-limiting, input validation, token expiry simulation, and account isolation.
 */
class InMemoryAuthRepository(
    initialState: AuthState = AuthState.Guest,
    private val maxFailedAttemptsPerMinute: Int = 5
) : AuthRepository {

    private data class StoredAccount(
        val user: AuthUser,
        val passwordHashBase64: String?,
        val saltBase64: String?
    )

    private val accountsByEmail = mutableMapOf<String, StoredAccount>()
    private val accountsByUid = mutableMapOf<String, StoredAccount>()
    private val resetEmailsSent = mutableListOf<String>()
    private val failedAttemptTimestamps = mutableListOf<Long>()

    @Volatile
    private var isSessionTokenExpired: Boolean = false

    private val _authState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> if (isSessionTokenExpired) null else state.user
            is AuthState.Error -> state.currentUser
            else -> null
        }

    fun getSentPasswordResetEmails(): List<String> = resetEmailsSent.toList()

    fun isPasswordStoredPlaintext(email: String, rawPassword: String): Boolean {
        val account = accountsByEmail[email.trim().lowercase()] ?: return false
        return account.passwordHashBase64 == rawPassword
    }

    fun getStoredPasswordHash(email: String): String? {
        return accountsByEmail[email.trim().lowercase()]?.passwordHashBase64
    }

    fun simulateTokenExpired() {
        isSessionTokenExpired = true
    }

    private fun checkRateLimit(): String? {
        val now = System.currentTimeMillis()
        synchronized(failedAttemptTimestamps) {
            failedAttemptTimestamps.removeAll { now - it > 60_000L }
            if (failedAttemptTimestamps.size >= maxFailedAttemptsPerMinute) {
                return "Too many authentication attempts. Please wait a minute before trying again."
            }
        }
        return null
    }

    private fun recordFailedAttempt() {
        synchronized(failedAttemptTimestamps) {
            failedAttemptTimestamps.add(System.currentTimeMillis())
        }
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthUser> {
        checkRateLimit()?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalStateException(msg))
        }

        val cleanEmail = email.trim()
        val normalizedEmail = cleanEmail.lowercase()
        val cleanName = displayName.trim()

        AuthValidator.validateDisplayName(cleanName)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validatePassword(password, isSignUp = true)?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalArgumentException(msg))
        }

        if (accountsByEmail.containsKey(normalizedEmail)) {
            recordFailedAttempt()
            val msg = "An account with this email already exists. Please sign in instead."
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(IllegalStateException(msg))
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPasswordPbkdf2(password, salt)
        val uid = "user_${ (accountsByUid.size + 1) }_${normalizedEmail.hashCode().toUInt().toString(16)}"

        val user = AuthUser(
            uid = uid,
            email = normalizedEmail,
            displayName = cleanName,
            providerId = "password"
        )
        val stored = StoredAccount(
            user = user,
            passwordHashBase64 = Base64.getEncoder().encodeToString(hash),
            saltBase64 = Base64.getEncoder().encodeToString(salt)
        )
        accountsByEmail[normalizedEmail] = stored
        accountsByUid[uid] = stored
        isSessionTokenExpired = false
        failedAttemptTimestamps.clear()
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<AuthUser> {
        checkRateLimit()?.let { msg ->
            _authState.value = AuthState.Error(msg, currentUser)
            return Result.failure(IllegalStateException(msg))
        }

        val cleanEmail = email.trim()
        val normalizedEmail = cleanEmail.lowercase()
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            recordFailedAttempt()
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(IllegalArgumentException(msg))
        }
        AuthValidator.validatePassword(password, isSignUp = false)?.let { msg ->
            recordFailedAttempt()
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(IllegalArgumentException(msg))
        }

        val account = accountsByEmail[normalizedEmail]
        if (account == null || account.passwordHashBase64 == null || account.saltBase64 == null) {
            recordFailedAttempt()
            val msg = "Invalid email or password. Please check your credentials and try again."
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(SecurityException(msg))
        }

        val salt = Base64.getDecoder().decode(account.saltBase64)
        val expectedHash = Base64.getDecoder().decode(account.passwordHashBase64)
        val actualHash = hashPasswordPbkdf2(password, salt)

        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            recordFailedAttempt()
            val msg = "Invalid email or password. Please check your credentials and try again."
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(SecurityException(msg))
        }

        isSessionTokenExpired = false
        failedAttemptTimestamps.clear()
        _authState.value = AuthState.Authenticated(account.user)
        return Result.success(account.user)
    }

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        return signInWithGoogleIdToken(
            idToken = "google_id_token_default",
            fallbackEmail = "google.user@minova.ai",
            fallbackDisplayName = "Google User"
        )
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String,
        fallbackDisplayName: String
    ): Result<AuthUser> {
        if (idToken.isBlank()) {
            recordFailedAttempt()
            val msg = "Google ID token must not be blank."
            _authState.value = AuthState.Error(msg, null)
            return Result.failure(IllegalArgumentException(msg))
        }

        val resolvedEmail = fallbackEmail.trim().ifBlank { "google_${idToken.hashCode().toUInt()}@gmail.com" }
        val normalizedEmail = resolvedEmail.lowercase()
        val resolvedName = fallbackDisplayName.trim().ifBlank { resolvedEmail.substringBefore("@") }

        val existing = accountsByEmail[normalizedEmail]
        val user = existing?.user ?: AuthUser(
            uid = "google_${idToken.hashCode().toUInt().toString(16)}",
            email = resolvedEmail,
            displayName = resolvedName,
            providerId = "google.com"
        )
        val stored = StoredAccount(user = user, passwordHashBase64 = null, saltBase64 = null)
        accountsByEmail[normalizedEmail] = stored
        accountsByUid[user.uid] = stored
        isSessionTokenExpired = false
        failedAttemptTimestamps.clear()
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        AuthValidator.validateEmail(cleanEmail)?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        resetEmailsSent.add(cleanEmail.lowercase())
        return Result.success(Unit)
    }

    override suspend fun refreshSession(): Result<AuthUser> {
        val state = _authState.value
        if (state !is AuthState.Authenticated || isSessionTokenExpired) {
            _authState.value = AuthState.Guest
            return Result.failure(SecurityException("Session expired. Please sign in again."))
        }
        return Result.success(state.user)
    }

    override suspend fun signOut(context: Context?): Result<Unit> {
        isSessionTokenExpired = false
        _authState.value = AuthState.Guest
        return Result.success(Unit)
    }

    override suspend fun deleteCurrentUserAccount(context: Context?): Result<Unit> {
        val user = currentUser ?: return Result.failure(SecurityException("No signed-in user."))
        accountsByUid.remove(user.uid)
        accountsByEmail.remove(user.email.lowercase())
        isSessionTokenExpired = false
        _authState.value = AuthState.Guest
        return Result.success(Unit)
    }

    override fun clearAuthError() {
        val user = currentUser
        _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.Guest
    }

    private fun hashPasswordPbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 12_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
