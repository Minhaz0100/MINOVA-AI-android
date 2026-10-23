package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthState
import com.example.data.model.ConflictResolutionStrategy
import com.example.data.model.SyncConsentDecision
import com.example.data.model.SyncStateSnapshot
import com.example.data.model.SyncStatus
import com.example.data.model.UserProfile
import com.example.data.util.DateTimeFormatterHelper
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaError
import com.example.ui.theme.MinovaIndigoSecondary

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    authState: AuthState = AuthState.Guest,
    syncState: SyncStateSnapshot = SyncStateSnapshot(),
    feedbackMessage: String? = null,
    onSignInWithEmail: (email: String, password: String) -> Unit = { _, _ -> },
    onSignUpWithEmail: (email: String, password: String, displayName: String) -> Unit = { _, _, _ -> },
    onGoogleSignIn: (Context) -> Unit = {},
    onSendPasswordReset: (email: String) -> Unit = {},
    onSignOut: (Context) -> Unit = {},
    onApproveSyncConsent: () -> Unit = {},
    onDeclineSyncConsent: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onResolveConflict: (conversationId: String, strategy: ConflictResolutionStrategy) -> Unit = { _, _ -> },
    onDeleteCloudData: (alsoDeleteLocalSynced: Boolean) -> Unit = {},
    onDeleteAccount: (Context) -> Unit = {},
    onClearFeedback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteCloudDataDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val authenticatedUser = when (authState) {
        is AuthState.Authenticated -> authState.user
        is AuthState.Error -> authState.currentUser
        else -> null
    }
    val isLoading = authState is AuthState.Loading || syncState.status == SyncStatus.SYNCING
    val authErrorMessage = (authState as? AuthState.Error)?.message

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("profile_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Account & Cloud Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar + Identity Header
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 2.dp,
                            color = if (authenticatedUser != null) MinovaCyanPrimary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (authenticatedUser != null) Icons.Default.CloudDone else Icons.Default.Person,
                        contentDescription = "Profile Avatar",
                        tint = MinovaAzureLight,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = authenticatedUser?.displayName ?: profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("profile_display_name")
                )

                Text(
                    text = authenticatedUser?.email ?: profile.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("profile_email_text")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Status / Tier Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MinovaIndigoSecondary.copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = MinovaCyanPrimary.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                        .testTag("profile_sync_status_badge")
                ) {
                    Text(
                        text = if (authenticatedUser != null) syncState.status.displayLabel else "Guest Mode • Local-Only Storage",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MinovaCyanPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Banner
                if (!authErrorMessage.isNullOrBlank()) {
                    Surface(
                        color = MinovaError.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MinovaError.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("account_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MinovaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearFeedback) {
                                Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Info / Success Feedback Banner
                if (!feedbackMessage.isNullOrBlank()) {
                    Surface(
                        color = MinovaCyanPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MinovaCyanPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .testTag("account_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Status",
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearFeedback) {
                                Text("OK", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (authenticatedUser == null) {
                    // =========================================================================
                    // GUEST / SIGNED-OUT VIEW: Local-Only Explanation + Sign In / Sign Up Forms
                    // =========================================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                            .testTag("guest_local_explanation_card")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How Local Chats & Cloud Sync Work",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• You can use MINOVA AI in local-only mode without signing in.\n" +
                                "• Existing local chats (${profile.conversationsCount}) are stored in on-device Room storage and are NEVER uploaded automatically.\n" +
                                "• After signing in, you will be asked for explicit consent before any existing local conversations are synced to the cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auth Form Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        // Mode Toggle Tabs (Sign In vs Create Account)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AuthModeTabButton(
                                label = "Sign In",
                                selected = !isSignUpMode,
                                onClick = {
                                    isSignUpMode = false
                                    onClearFeedback()
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "auth_tab_sign_in"
                            )
                            AuthModeTabButton(
                                label = "Create Account",
                                selected = isSignUpMode,
                                onClick = {
                                    isSignUpMode = true
                                    onClearFeedback()
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "auth_tab_sign_up"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("account_name_input")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_email_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(if (isSignUpMode) "Password (8+ characters)" else "Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_password_input")
                        )

                        if (!isSignUpMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { onSendPasswordReset(emailInput) },
                                    modifier = Modifier.testTag("account_forgot_password_button")
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MinovaCyanPrimary
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Button(
                            onClick = {
                                if (isSignUpMode) {
                                    onSignUpWithEmail(emailInput, passwordInput, displayNameInput)
                                } else {
                                    onSignInWithEmail(emailInput, passwordInput)
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("account_submit_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isSignUpMode) "Create Account" else "Sign In",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { onGoogleSignIn(context) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("account_google_signin_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("account_continue_guest_button")
                    ) {
                        Text(
                            text = "Continue in Guest / Local-Only Mode",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // =========================================================================
                    // AUTHENTICATED VIEW: Consent Prompt, Conflict Resolution, Sync & Privacy
                    // =========================================================================

                    // 1. Explicit Consent Card for Pre-Existing Local Conversations
                    if (syncState.consentDecision == SyncConsentDecision.UNDECIDED ||
                        (syncState.consentDecision == SyncConsentDecision.DECLINED_KEEP_LOCAL_ONLY &&
                            syncState.pendingLocalConversationsCount > 0)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MinovaIndigoSecondary.copy(alpha = 0.18f))
                                .border(
                                    width = 1.dp,
                                    color = MinovaCyanPrimary.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                                .testTag("consent_prompt_card")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upload Existing Local Chats?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You have ${syncState.pendingLocalConversationsCount} local conversation(s) created on this device before signing in. " +
                                    "MINOVA AI never uploads pre-existing local chats without your explicit consent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onApproveSyncConsent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("consent_approve_sync_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Upload & Sync",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (syncState.consentDecision == SyncConsentDecision.UNDECIDED) {
                                    OutlinedButton(
                                        onClick = onDeclineSyncConsent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("consent_decline_local_only_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Keep Local-Only",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 2. Conflict Resolution Card (if any conflicts detected)
                    if (syncState.activeConflicts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MinovaError.copy(alpha = 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = MinovaError.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp)
                                .testTag("conflict_resolution_card")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MinovaError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sync Conflict Detected (${syncState.activeConflicts.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Both this device and the cloud have different edits for the conversation(s) below. Choose how to resolve:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            syncState.activeConflicts.forEach { conflict ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Local: \"${conflict.localConversation.title}\" (${conflict.localMessages.size} msgs)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Cloud: \"${conflict.cloudConversation.title}\" (${conflict.cloudMessages.size} msgs)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MinovaAzureLight
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onResolveConflict(
                                                    conflict.conversationId,
                                                    ConflictResolutionStrategy.MERGE_BOTH
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("conflict_merge_button_${conflict.conversationId}")
                                        ) {
                                            Text("Merge", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onResolveConflict(
                                                    conflict.conversationId,
                                                    ConflictResolutionStrategy.KEEP_CLOUD
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("conflict_keep_cloud_button_${conflict.conversationId}")
                                        ) {
                                            Text("Cloud", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onResolveConflict(
                                                    conflict.conversationId,
                                                    ConflictResolutionStrategy.KEEP_LOCAL
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("conflict_keep_local_button_${conflict.conversationId}")
                                        ) {
                                            Text("Local", fontSize = 11.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 3. Usage & Sync Metrics Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricItem(label = "Total Chats", value = profile.conversationsCount.toString())
                        MetricItem(label = "Cloud Synced", value = syncState.syncedConversationsCount.toString())
                        MetricItem(label = "Offline Queue", value = syncState.queuedOfflineOperationsCount.toString())
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. Cloud Synchronization Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (syncState.status) {
                                        SyncStatus.OFFLINE_PENDING, SyncStatus.ERROR -> Icons.Default.CloudOff
                                        SyncStatus.SYNCING -> Icons.Default.CloudSync
                                        else -> Icons.Default.CloudDone
                                    },
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Cloud Firestore Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val lastSyncFormatted = syncState.lastSyncedAtMillis?.let {
                                        "Last synced: ${DateTimeFormatterHelper.formatTime(it)}"
                                    } ?: "Ready to synchronize"
                                    Text(
                                        text = lastSyncFormatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onSyncNow,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("account_sync_now_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Now",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. Privacy & Cloud Data Deletion Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Privacy & Cloud Data Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showDeleteCloudDataDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("account_delete_cloud_data_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MinovaError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete Cloud Conversations",
                                color = MinovaError,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("account_delete_account_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MinovaError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete Account & Cloud Data",
                                color = MinovaError,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 6. Sign Out Button
                    Button(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_sign_out_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinovaError.copy(alpha = 0.15f),
                            contentColor = MinovaError
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = {
                Text(
                    "Are you sure you want to sign out? Your local conversations will remain safely stored on this device in Local-Only mode."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut(context)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MinovaError),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteCloudDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudDataDialog = false },
            title = { Text("Delete Cloud Conversations?") },
            text = {
                Text(
                    "This will permanently delete all conversations and messages stored in your Cloud Firestore account. Your local device conversations will remain intact."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCloudDataDialog = false
                        onDeleteCloudData(false)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MinovaError),
                    modifier = Modifier.testTag("confirm_delete_cloud_data_button")
                ) {
                    Text("Delete Cloud Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCloudDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Cloud Data?") },
            text = {
                Text(
                    "This will permanently delete your cloud conversations, user profile, and account from MINOVA AI Cloud. Local conversations on this device will not be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount(context)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MinovaError),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AuthModeTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val bg = if (selected) MinovaIndigoSecondary.copy(alpha = 0.28f) else Color.Transparent
    val border = if (selected) MinovaCyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MinovaCyanPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
