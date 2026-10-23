package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.CloudConversationRecord
import com.example.data.model.CloudMessageRecord
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Structured error logger for Firestore operations as required by platform security guidelines.
 * Never logs user passwords, tokens, or private message contents.
 */
fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Throwable) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    try {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } catch (_: Throwable) {
        // Ignore Android Log stub exceptions in pure JVM tests
    }
    return jsonString
}

/**
 * Repository interface for user-scoped Cloud Firestore conversations and messages.
 * Every operation requires an authenticated `userId` matching the active session.
 */
interface CloudConversationRepository {
    fun observeUserConversations(userId: String): Flow<List<CloudConversationRecord>>
    suspend fun fetchUserConversations(
        userId: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<CloudConversationRecord>>
    suspend fun fetchMessagesForConversation(
        userId: String,
        conversationId: String,
        limit: Int = 200,
        offset: Int = 0
    ): Result<List<CloudMessageRecord>>
    suspend fun upsertConversation(userId: String, conversation: CloudConversationRecord): Result<Unit>
    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit>
    suspend fun upsertMessage(
        userId: String,
        conversationId: String,
        message: CloudMessageRecord
    ): Result<Unit>
    suspend fun deleteMessage(userId: String, conversationId: String, messageId: String): Result<Unit>
    suspend fun deleteAllUserCloudData(userId: String): Result<Unit>
}

/**
 * Production Cloud Firestore implementation scoped strictly to `/users/{userId}/conversations`.
 * Always uses the named Firestore database ID from `R.string.firestore_database_id`.
 */
class FirestoreCloudConversationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : CloudConversationRepository {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireAuthenticatedOwner(expectedUserId: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw SecurityException("User must be signed in before accessing cloud conversations.")
        if (currentUid != expectedUserId) {
            throw SecurityException("Access denied: cannot access another user's cloud data.")
        }
        return currentUid
    }

    override fun observeUserConversations(userId: String): Flow<List<CloudConversationRecord>> = flow {
        val uid = requireAuthenticatedOwner(userId)
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toCloudConversation(uid) }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun fetchUserConversations(
        userId: String,
        limit: Int,
        offset: Int
    ): Result<List<CloudConversationRecord>> {
        val path = "users/$userId/conversations"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            val safeOffset = offset.coerceAtLeast(0)
            val safeLimit = limit.coerceIn(1, 200)
            val snapshot = db.collection("users")
                .document(uid)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit((safeOffset + safeLimit).toLong().coerceAtMost(500L))
                .get()
                .await()
            val records = snapshot.documents
                .drop(safeOffset)
                .take(safeLimit)
                .mapNotNull { it.toCloudConversation(uid) }
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    override suspend fun fetchMessagesForConversation(
        userId: String,
        conversationId: String,
        limit: Int,
        offset: Int
    ): Result<List<CloudMessageRecord>> {
        val path = "users/$userId/conversations/$conversationId/messages"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            require(conversationId.isNotBlank()) { "conversationId must not be blank" }
            val safeOffset = offset.coerceAtLeast(0)
            val safeLimit = limit.coerceIn(1, 500)
            val snapshot = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit((safeOffset + safeLimit).toLong().coerceAtMost(1000L))
                .get()
                .await()
            val messages = snapshot.documents
                .drop(safeOffset)
                .take(safeLimit)
                .mapNotNull { it.toCloudMessage(conversationId) }
            Result.success(messages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertConversation(
        userId: String,
        conversation: CloudConversationRecord
    ): Result<Unit> {
        val path = "users/$userId/conversations/${conversation.id}"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            require(conversation.id.isNotBlank()) { "Conversation ID must not be blank" }
            val safeTitle = conversation.title.trim().ifBlank { "New Conversation" }.take(200)
            val docRef = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversation.id)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updateData = mutableMapOf<String, Any>(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isPinned" to conversation.isPinned,
                    "isArchived" to conversation.isArchived,
                    "lastMessage" to conversation.lastMessage.take(1000)
                )
                conversation.responseStyle?.take(50)?.let { updateData["responseStyle"] = it }
                conversation.responseLanguage?.take(50)?.let { updateData["responseLanguage"] = it }
                conversation.responseFormat?.take(50)?.let { updateData["responseFormat"] = it }
                docRef.update(updateData).await()
            } else {
                val createData = mutableMapOf<String, Any>(
                    "id" to conversation.id,
                    "userId" to uid,
                    "title" to safeTitle,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isPinned" to conversation.isPinned,
                    "isArchived" to conversation.isArchived,
                    "lastMessage" to conversation.lastMessage.take(1000)
                )
                conversation.responseStyle?.take(50)?.let { createData["responseStyle"] = it }
                conversation.responseLanguage?.take(50)?.let { createData["responseLanguage"] = it }
                conversation.responseFormat?.take(50)?.let { createData["responseFormat"] = it }
                docRef.set(createData).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            val convRef = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)

            val messagesSnap = convRef.collection("messages").get().await()
            for (msgDoc in messagesSnap.documents) {
                msgDoc.reference.delete().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertMessage(
        userId: String,
        conversationId: String,
        message: CloudMessageRecord
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/${message.id}"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
            require(message.id.isNotBlank()) { "Message ID must not be blank" }

            val normalizedSender = if (message.sender.equals("USER", ignoreCase = true)) "USER" else "ASSISTANT"
            val normalizedStatus = when (message.status.uppercase()) {
                "SENT", "STREAMING", "ERROR" -> message.status.uppercase()
                else -> "SENT"
            }

            val msgRef = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(message.id)

            val existingSnap = msgRef.get().await()
            if (existingSnap.exists()) {
                val updateData = mutableMapOf<String, Any>(
                    "content" to message.content.take(50000),
                    "status" to normalizedStatus,
                    "isPinned" to message.isPinned
                )
                message.errorMessage?.take(1000)?.let { updateData["errorMessage"] = it }
                message.codeSnippet?.take(30000)?.let { updateData["codeSnippet"] = it }
                message.codeLanguage?.take(50)?.let { updateData["codeLanguage"] = it }
                msgRef.update(updateData).await()
            } else {
                val safePastMillis = minOf(message.timestampMillis, System.currentTimeMillis() - 500L).coerceAtLeast(1000L)
                val createData = mutableMapOf<String, Any>(
                    "id" to message.id,
                    "conversationId" to conversationId,
                    "sender" to normalizedSender,
                    "content" to message.content.take(50000),
                    "timestamp" to Timestamp(Date(safePastMillis)),
                    "status" to normalizedStatus,
                    "isPinned" to message.isPinned
                )
                message.errorMessage?.take(1000)?.let { createData["errorMessage"] = it }
                message.attachmentType?.take(100)?.let { createData["attachmentType"] = it }
                message.attachmentName?.take(255)?.let { createData["attachmentName"] = it }
                message.codeSnippet?.take(30000)?.let { createData["codeSnippet"] = it }
                message.codeLanguage?.take(50)?.let { createData["codeLanguage"] = it }
                msgRef.set(createData).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(
        userId: String,
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        val path = "users/$userId/conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteAllUserCloudData(userId: String): Result<Unit> {
        val path = "users/$userId/conversations"
        return try {
            val uid = requireAuthenticatedOwner(userId)
            val convsSnap = db.collection("users")
                .document(uid)
                .collection("conversations")
                .get()
                .await()
            for (convDoc in convsSnap.documents) {
                val msgsSnap = convDoc.reference.collection("messages").get().await()
                for (msgDoc in msgsSnap.documents) {
                    msgDoc.reference.delete().await()
                }
                convDoc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toCloudConversation(fallbackUserId: String): CloudConversationRecord? {
        if (!exists()) return null
        val idVal = getString("id") ?: id
        val userIdVal = getString("userId") ?: fallbackUserId
        val titleVal = getString("title") ?: return null
        val createdAtTs = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val updatedAtTs = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return CloudConversationRecord(
            id = idVal,
            userId = userIdVal,
            title = titleVal,
            createdAtMillis = createdAtTs?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAtMillis = updatedAtTs?.toDate()?.time ?: System.currentTimeMillis(),
            isPinned = getBoolean("isPinned") ?: false,
            isArchived = getBoolean("isArchived") ?: false,
            lastMessage = getString("lastMessage") ?: "",
            responseStyle = getString("responseStyle"),
            responseLanguage = getString("responseLanguage"),
            responseFormat = getString("responseFormat")
        )
    }

    private fun DocumentSnapshot.toCloudMessage(fallbackConversationId: String): CloudMessageRecord? {
        if (!exists()) return null
        val idVal = getString("id") ?: id
        val convIdVal = getString("conversationId") ?: fallbackConversationId
        val senderVal = getString("sender") ?: "ASSISTANT"
        val contentVal = getString("content") ?: ""
        val ts = getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return CloudMessageRecord(
            id = idVal,
            conversationId = convIdVal,
            sender = senderVal,
            content = contentVal,
            timestampMillis = ts?.toDate()?.time ?: System.currentTimeMillis(),
            status = getString("status") ?: "SENT",
            errorMessage = getString("errorMessage"),
            attachmentType = getString("attachmentType"),
            attachmentName = getString("attachmentName"),
            codeSnippet = getString("codeSnippet"),
            codeLanguage = getString("codeLanguage"),
            isPinned = getBoolean("isPinned") ?: false
        )
    }
}

/**
 * Deterministic in-memory implementation of [CloudConversationRepository] for unit and Robolectric tests.
 * Enforces strict per-user isolation, offline simulation, and pagination.
 */
class InMemoryCloudConversationRepository(
    private val activeUserIdProvider: () -> String? = { null }
) : CloudConversationRepository {

    private val conversationsByUser = MutableStateFlow<Map<String, Map<String, CloudConversationRecord>>>(emptyMap())
    private val messagesByUserAndConv = MutableStateFlow<Map<String, Map<String, CloudMessageRecord>>>(emptyMap())

    @Volatile
    var isSimulatedOffline: Boolean = false

    private fun verifyAccess(requestedUserId: String) {
        if (isSimulatedOffline) {
            throw IOException("Network unavailable: device is offline.")
        }
        val activeUid = activeUserIdProvider()
            ?: throw SecurityException("Unauthenticated access denied.")
        if (activeUid != requestedUserId) {
            throw SecurityException("Cross-user access denied: user $activeUid cannot access $requestedUserId.")
        }
    }

    override fun observeUserConversations(userId: String): Flow<List<CloudConversationRecord>> =
        conversationsByUser.map { byUser ->
            verifyAccess(userId)
            (byUser[userId]?.values?.toList() ?: emptyList())
                .sortedWith(compareByDescending<CloudConversationRecord> { it.isPinned }.thenByDescending { it.updatedAtMillis })
        }

    fun seedCloudConversationForTest(
        userId: String,
        conversation: CloudConversationRecord,
        messages: List<CloudMessageRecord> = emptyList()
    ) {
        val normalized = conversation.copy(userId = userId)
        conversationsByUser.update { current ->
            val userMap = current[userId] ?: emptyMap()
            current + (userId to (userMap + (conversation.id to normalized)))
        }
        if (messages.isNotEmpty()) {
            val key = "$userId:${conversation.id}"
            messagesByUserAndConv.update { current ->
                val msgMap = current[key] ?: emptyMap()
                val additions = messages.associate { it.id to it.copy(conversationId = conversation.id) }
                current + (key to (msgMap + additions))
            }
        }
    }

    override suspend fun fetchUserConversations(
        userId: String,
        limit: Int,
        offset: Int
    ): Result<List<CloudConversationRecord>> = runCatching {
        verifyAccess(userId)
        (conversationsByUser.value[userId]?.values?.toList() ?: emptyList())
            .sortedByDescending { it.updatedAtMillis }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 200))
    }

    override suspend fun fetchMessagesForConversation(
        userId: String,
        conversationId: String,
        limit: Int,
        offset: Int
    ): Result<List<CloudMessageRecord>> = runCatching {
        verifyAccess(userId)
        val key = "$userId:$conversationId"
        (messagesByUserAndConv.value[key]?.values?.toList() ?: emptyList())
            .sortedBy { it.timestampMillis }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 500))
    }

    override suspend fun upsertConversation(
        userId: String,
        conversation: CloudConversationRecord
    ): Result<Unit> = runCatching {
        verifyAccess(userId)
        require(conversation.id.isNotBlank()) { "Conversation ID required" }
        require(conversation.title.isNotBlank()) { "Conversation title required" }
        val normalized = conversation.copy(userId = userId)
        conversationsByUser.update { current ->
            val userMap = current[userId] ?: emptyMap()
            current + (userId to (userMap + (conversation.id to normalized)))
        }
    }

    override suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> = runCatching {
        verifyAccess(userId)
        conversationsByUser.update { current ->
            val userMap = current[userId] ?: emptyMap()
            current + (userId to (userMap - conversationId))
        }
        val key = "$userId:$conversationId"
        messagesByUserAndConv.update { it - key }
    }

    override suspend fun upsertMessage(
        userId: String,
        conversationId: String,
        message: CloudMessageRecord
    ): Result<Unit> = runCatching {
        verifyAccess(userId)
        require(message.id.isNotBlank()) { "Message ID required" }
        val key = "$userId:$conversationId"
        val normalized = message.copy(conversationId = conversationId)
        messagesByUserAndConv.update { current ->
            val msgMap = current[key] ?: emptyMap()
            current + (key to (msgMap + (message.id to normalized)))
        }
    }

    override suspend fun deleteMessage(
        userId: String,
        conversationId: String,
        messageId: String
    ): Result<Unit> = runCatching {
        verifyAccess(userId)
        val key = "$userId:$conversationId"
        messagesByUserAndConv.update { current ->
            val msgMap = current[key] ?: emptyMap()
            current + (key to (msgMap - messageId))
        }
    }

    override suspend fun deleteAllUserCloudData(userId: String): Result<Unit> = runCatching {
        verifyAccess(userId)
        conversationsByUser.update { it - userId }
        messagesByUserAndConv.update { current ->
            current.filterKeys { !it.startsWith("$userId:") }
        }
    }
}
