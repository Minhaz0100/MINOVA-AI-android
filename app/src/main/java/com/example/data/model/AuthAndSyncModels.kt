package com.example.data.model

/**
 * Represents an authenticated MINOVA AI user account.
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val providerId: String = "password", // "password" or "google.com"
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Authentication state of the application.
 * Defaults to [Guest] so local-only mode is always available without requiring sign-in.
 */
sealed class AuthState {
    data object Guest : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String, val currentUser: AuthUser? = null) : AuthState()
}

/**
 * User consent state for uploading pre-existing local conversations to Cloud Firestore.
 * Local conversations are NEVER uploaded automatically without explicit user consent ([APPROVED]).
 */
enum class SyncConsentDecision {
    UNDECIDED,
    APPROVED,
    DECLINED_KEEP_LOCAL_ONLY
}

/**
 * High-level cloud synchronization status surfaced in the Account and Drawer UI.
 */
enum class SyncStatus(val displayLabel: String) {
    LOCAL_ONLY("Local-only mode • Device storage"),
    AWAITING_CONSENT("Waiting for consent to upload local chats"),
    SYNCING("Syncing conversations with cloud..."),
    SYNCED("Cloud sync active • Up to date"),
    CONFLICT_DETECTED("Conflict detected • Review required"),
    OFFLINE_PENDING("Offline • Changes queued for sync"),
    ERROR("Sync paused • Tap to retry")
}

/**
 * Resolution choices when a local conversation and a cloud conversation have conflicting edits.
 */
enum class ConflictResolutionStrategy {
    MERGE_BOTH,
    KEEP_CLOUD,
    KEEP_LOCAL
}

/**
 * Represents a detected synchronization conflict between local Room cache and Cloud Firestore
 * that requires explicit user confirmation before overwriting or merging.
 */
data class SyncConflictItem(
    val conversationId: String,
    val localConversation: CloudConversationRecord,
    val cloudConversation: CloudConversationRecord,
    val localMessages: List<CloudMessageRecord> = emptyList(),
    val cloudMessages: List<CloudMessageRecord> = emptyList()
)

/**
 * Snapshot of synchronization state for UI display and diagnostics.
 */
data class SyncStateSnapshot(
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val consentDecision: SyncConsentDecision = SyncConsentDecision.UNDECIDED,
    val pendingLocalConversationsCount: Int = 0,
    val syncedConversationsCount: Int = 0,
    val queuedOfflineOperationsCount: Int = 0,
    val activeConflicts: List<SyncConflictItem> = emptyList(),
    val lastSyncedAtMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val activeUserId: String? = null
)

/**
 * Queued offline operation waiting for network connectivity or retry.
 */
enum class SyncOperationKind {
    UPSERT_CONVERSATION,
    DELETE_CONVERSATION,
    UPSERT_MESSAGE,
    DELETE_MESSAGE
}

data class PendingSyncOperation(
    val operationId: String,
    val userId: String,
    val kind: SyncOperationKind,
    val conversationId: String,
    val messageId: String? = null,
    val conversationPayload: CloudConversationRecord? = null,
    val messagePayload: CloudMessageRecord? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

/**
 * Cloud representation of a user-scoped conversation stored at
 * `/users/{userId}/conversations/{conversationId}`.
 */
data class CloudConversationRecord(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val lastMessage: String = "",
    val responseStyle: String? = null,
    val responseLanguage: String? = null,
    val responseFormat: String? = null
)

/**
 * Cloud representation of a user-scoped message stored at
 * `/users/{userId}/conversations/{conversationId}/messages/{messageId}`.
 */
data class CloudMessageRecord(
    val id: String = "",
    val conversationId: String = "",
    val sender: String = "USER", // "USER" or "ASSISTANT"
    val content: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENT", "STREAMING", "ERROR"
    val errorMessage: String? = null,
    val attachmentType: String? = null,
    val attachmentName: String? = null,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val isPinned: Boolean = false
)
