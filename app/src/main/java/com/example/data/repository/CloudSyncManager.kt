package com.example.data.repository

import android.content.Context
import com.example.data.model.CloudConversationRecord
import com.example.data.model.CloudMessageRecord
import com.example.data.model.ConflictResolutionStrategy
import com.example.data.model.PendingSyncOperation
import com.example.data.model.SyncConflictItem
import com.example.data.model.SyncConsentDecision
import com.example.data.model.SyncOperationKind
import com.example.data.model.SyncStateSnapshot
import com.example.data.model.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Multi-device synchronization coordinator between local Room persistence ([ConversationRepository])
 * and user-scoped Cloud Firestore ([CloudConversationRepository]).
 *
 * Key guarantees:
 * 1. Never uploads pre-existing local conversations without explicit user consent ([SyncConsentDecision.APPROVED]).
 * 2. Preserves local conversations in Guest mode and when signing out.
 * 3. Enforces strict account isolation when switching between different user accounts.
 * 4. Queues offline mutations and retries safely without creating duplicate conversations or messages.
 * 5. Never overwrites newer local messages with stale cloud data; surfaces concurrent edit conflicts
 *    for explicit user confirmation ([resolveConflict]).
 */
class CloudSyncManager(
    private val localRepository: ConversationRepository,
    private val cloudRepository: CloudConversationRepository,
    context: Context? = null,
    private val pageSize: Int = 50
) {
    private val prefs = try {
        context?.applicationContext?.getSharedPreferences("minova_cloud_sync_prefs", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(SyncStateSnapshot())
    val syncState: StateFlow<SyncStateSnapshot> = _syncState.asStateFlow()

    // Maps conversationId -> owner uid (or LOCAL_GUEST_OWNER for pre-existing local chats)
    private val conversationOwners = mutableMapOf<String, String>()
    // Tracks which conversations originated in local guest mode prior to sign-in
    private val originallyLocalConversationIds = mutableSetOf<String>()
    // Tracks last known synced updatedAt timestamp per (userId:conversationId)
    private val lastSyncedTimestampByConv = mutableMapOf<String, Long>()
    // Per-user consent decision
    private val consentByUser = mutableMapOf<String, SyncConsentDecision>()
    // Offline pending operation queue per user
    private val pendingOperations = mutableListOf<PendingSyncOperation>()
    // Detected conflicts awaiting explicit user resolution
    private val activeConflicts = mutableMapOf<String, SyncConflictItem>()
    // Stashed per-user local cache when switching between different authenticated accounts
    private val stashedUserConversations = mutableMapOf<String, List<Pair<CloudConversationRecord, List<CloudMessageRecord>>>>()

    private var activeUserId: String? = null
    private var previousAuthenticatedUserId: String? = null

    /**
     * Records a conversation created locally. If no user is signed in, marks it as a pre-existing
     * Guest/local conversation so it will never be uploaded without explicit consent.
     */
    suspend fun registerLocalConversationCreated(conversationId: String) {
        syncMutex.withLock {
            val uid = activeUserId
            if (uid == null) {
                conversationOwners[conversationId] = LOCAL_GUEST_OWNER
                originallyLocalConversationIds.add(conversationId)
            } else {
                conversationOwners[conversationId] = uid
            }
            refreshPendingCountsLocked()
        }
    }

    /**
     * Called when a user signs in or when active auth state changes to Authenticated.
     * Isolates any previous user's cloud-cached data, checks consent for pre-existing local chats,
     * and syncs the authenticated user's cloud conversations.
     */
    suspend fun onUserAuthenticated(userId: String): Result<SyncStateSnapshot> {
        syncMutex.withLock {
            val prevUid = previousAuthenticatedUserId
            if (prevUid != null && prevUid != userId) {
                isolatePreviousUserDataLocked(prevUid)
            }

            activeUserId = userId
            previousAuthenticatedUserId = userId
            activeConflicts.clear()

            // Restore stashed conversations for this user if they were previously stashed offline
            val stashed = stashedUserConversations.remove(userId)
            if (stashed != null) {
                for ((conv, msgs) in stashed) {
                    localRepository.upsertSyncedConversation(conv)
                    for (msg in msgs) {
                        localRepository.upsertSyncedMessage(msg)
                    }
                    conversationOwners[conv.id] = userId
                }
            }

            // Inspect current Room conversations: any conversation without an owner is a pre-existing local chat
            val allLocal = localRepository.getAllCloudConversationRecords(userId)
            for (conv in allLocal) {
                if (!conversationOwners.containsKey(conv.id)) {
                    conversationOwners[conv.id] = LOCAL_GUEST_OWNER
                    originallyLocalConversationIds.add(conv.id)
                }
            }

            val consent = getConsentDecisionLocked(userId)
            if (consent == SyncConsentDecision.APPROVED) {
                claimLocalConversationsForUserLocked(userId)
            }
        }

        return syncWithCloud(userId)
    }

    /**
     * Called when the user signs out to Guest / Local-Only mode.
     * Local conversations remain preserved on device.
     */
    suspend fun onUserSignedOut() {
        syncMutex.withLock {
            activeUserId = null
            activeConflicts.clear()
            _syncState.value = SyncStateSnapshot(
                status = SyncStatus.LOCAL_ONLY,
                consentDecision = SyncConsentDecision.UNDECIDED,
                pendingLocalConversationsCount = countUnclaimedLocalConversationsLocked(),
                syncedConversationsCount = 0,
                queuedOfflineOperationsCount = 0,
                activeConflicts = emptyList(),
                activeUserId = null
            )
        }
    }

    /**
     * Sets explicit user consent for uploading pre-existing local conversations.
     */
    suspend fun setSyncConsent(userId: String, decision: SyncConsentDecision): Result<SyncStateSnapshot> {
        syncMutex.withLock {
            consentByUser[userId] = decision
            prefs?.edit()?.putString("consent_$userId", decision.name)?.apply()

            if (decision == SyncConsentDecision.APPROVED) {
                claimLocalConversationsForUserLocked(userId)
            }
        }
        return if (activeUserId == userId) {
            syncWithCloud(userId)
        } else {
            Result.success(_syncState.value)
        }
    }

    fun getSyncConsent(userId: String): SyncConsentDecision {
        return consentByUser[userId] ?: run {
            val saved = prefs?.getString("consent_$userId", null)
            saved?.let {
                try {
                    SyncConsentDecision.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            } ?: SyncConsentDecision.UNDECIDED
        }
    }

    /**
     * Synchronizes a newly created or updated local conversation & its messages to Cloud Firestore
     * IF the user is signed in AND the conversation is authorized to sync.
     */
    suspend fun syncLocalConversationIfAuthorized(conversationId: String) {
        val uid = activeUserId ?: return
        val shouldSync = syncMutex.withLock {
            val owner = conversationOwners[conversationId]
            when {
                owner == uid -> true
                owner == null -> {
                    // Created while authenticated
                    conversationOwners[conversationId] = uid
                    true
                }
                owner == LOCAL_GUEST_OWNER && getConsentDecisionLocked(uid) == SyncConsentDecision.APPROVED -> {
                    conversationOwners[conversationId] = uid
                    true
                }
                else -> false
            }
        }
        if (!shouldSync) {
            syncMutex.withLock { refreshPendingCountsLocked() }
            return
        }

        val convRecord = localRepository.getCloudConversationRecordById(conversationId, uid) ?: return
        val messages = localRepository.getCloudMessageRecordsSync(conversationId)

        val convResult = cloudRepository.upsertConversation(uid, convRecord)
        if (convResult.isFailure) {
            enqueueOfflineOperation(
                PendingSyncOperation(
                    operationId = "upsert_conv_${uid}_$conversationId",
                    userId = uid,
                    kind = SyncOperationKind.UPSERT_CONVERSATION,
                    conversationId = conversationId,
                    conversationPayload = convRecord
                )
            )
            for (msg in messages) {
                enqueueOfflineOperation(
                    PendingSyncOperation(
                        operationId = "upsert_msg_${uid}_${conversationId}_${msg.id}",
                        userId = uid,
                        kind = SyncOperationKind.UPSERT_MESSAGE,
                        conversationId = conversationId,
                        messageId = msg.id,
                        messagePayload = msg
                    )
                )
            }
            return
        }

        var messageFailed = false
        for (msg in messages) {
            val msgRes = cloudRepository.upsertMessage(uid, conversationId, msg)
            if (msgRes.isFailure) {
                messageFailed = true
                enqueueOfflineOperation(
                    PendingSyncOperation(
                        operationId = "upsert_msg_${uid}_${conversationId}_${msg.id}",
                        userId = uid,
                        kind = SyncOperationKind.UPSERT_MESSAGE,
                        conversationId = conversationId,
                        messageId = msg.id,
                        messagePayload = msg
                    )
                )
            }
        }

        syncMutex.withLock {
            if (!messageFailed) {
                lastSyncedTimestampByConv["$uid:$conversationId"] = convRecord.updatedAtMillis
            }
            updateStateSnapshotLocked(uid, lastError = null)
        }
    }

    /**
     * Propagates a local conversation deletion to Cloud Firestore if the user is signed in
     * and owns the conversation.
     */
    suspend fun onLocalConversationDeleted(conversationId: String) {
        val uid = activeUserId
        val wasOwnedByActiveUser = syncMutex.withLock {
            val owner = conversationOwners.remove(conversationId)
            originallyLocalConversationIds.remove(conversationId)
            activeConflicts.remove(conversationId)
            lastSyncedTimestampByConv.remove("$uid:$conversationId")
            uid != null && owner == uid
        }
        if (uid == null || !wasOwnedByActiveUser) {
            syncMutex.withLock { refreshPendingCountsLocked() }
            return
        }

        val res = cloudRepository.deleteConversation(uid, conversationId)
        if (res.isFailure) {
            enqueueOfflineOperation(
                PendingSyncOperation(
                    operationId = "del_conv_${uid}_$conversationId",
                    userId = uid,
                    kind = SyncOperationKind.DELETE_CONVERSATION,
                    conversationId = conversationId
                )
            )
        } else {
            syncMutex.withLock { updateStateSnapshotLocked(uid, lastError = null) }
        }
    }

    /**
     * Propagates a single message deletion to Cloud Firestore if the conversation is synced.
     */
    suspend fun onLocalMessageDeleted(conversationId: String, messageId: String) {
        val uid = activeUserId ?: return
        val isOwned = syncMutex.withLock { conversationOwners[conversationId] == uid }
        if (!isOwned) return

        val res = cloudRepository.deleteMessage(uid, conversationId, messageId)
        if (res.isFailure) {
            enqueueOfflineOperation(
                PendingSyncOperation(
                    operationId = "del_msg_${uid}_${conversationId}_$messageId",
                    userId = uid,
                    kind = SyncOperationKind.DELETE_MESSAGE,
                    conversationId = conversationId,
                    messageId = messageId
                )
            )
        }
    }

    /**
     * Performs full bidirectional incremental synchronization for [userId]:
     * 1. Flushes queued offline operations with retry handling.
     * 2. Uploads consented/user-owned local conversations that are newer than cloud.
     * 3. Fetches all cloud conversations using pagination and updates local Room cache.
     * 4. Detects concurrent modification conflicts without silently overwriting newer messages.
     */
    suspend fun syncWithCloud(userId: String): Result<SyncStateSnapshot> {
        syncMutex.withLock {
            if (activeUserId == null) {
                activeUserId = userId
                previousAuthenticatedUserId = userId
            }
            if (activeUserId != userId) {
                return Result.failure(SecurityException("Cannot sync cloud data for non-active user $userId"))
            }

            // Ensure any unindexed Room conversations are tagged prior to sync
            val currentLocal = localRepository.getAllCloudConversationRecords(userId)
            for (conv in currentLocal) {
                if (!conversationOwners.containsKey(conv.id)) {
                    conversationOwners[conv.id] = LOCAL_GUEST_OWNER
                    originallyLocalConversationIds.add(conv.id)
                }
            }
            if (getConsentDecisionLocked(userId) == SyncConsentDecision.APPROVED) {
                claimLocalConversationsForUserLocked(userId)
            }

            _syncState.value = _syncState.value.copy(
                status = SyncStatus.SYNCING,
                activeUserId = userId,
                lastErrorMessage = null
            )
        }

        return try {
            // Step 1: Flush pending offline queue for userId
            val flushFailure = flushPendingQueue(userId)
            if (flushFailure != null) {
                return syncMutex.withLock {
                    updateStateSnapshotLocked(userId, lastError = flushFailure.message ?: "Offline: changes queued")
                    Result.failure(flushFailure)
                }
            }

            // Step 2: Fetch all remote conversations across pages
            val remoteConversations = mutableListOf<CloudConversationRecord>()
            var offset = 0
            while (true) {
                val pageResult = cloudRepository.fetchUserConversations(
                    userId = userId,
                    limit = pageSize,
                    offset = offset
                )
                if (pageResult.isFailure) {
                    val ex = pageResult.exceptionOrNull() ?: Exception("Failed to fetch cloud conversations")
                    return syncMutex.withLock {
                        updateStateSnapshotLocked(userId, lastError = ex.message)
                        Result.failure(ex)
                    }
                }
                val page = pageResult.getOrDefault(emptyList())
                remoteConversations.addAll(page)
                if (page.size < pageSize) break
                offset += pageSize
            }

            val remoteById = remoteConversations.associateBy { it.id }

            // Step 3: Reconcile local conversations with remote conversations
            val allLocal = localRepository.getAllCloudConversationRecords(userId)
            val localById = allLocal.associateBy { it.id }

            // 3A. Process remote conversations (download new or reconcile existing)
            for (remoteConv in remoteConversations) {
                val localConv = localById[remoteConv.id]
                val remoteMessages = fetchAllMessagesPaginated(userId, remoteConv.id)

                if (localConv == null) {
                    // New conversation from another device -> save into local Room cache
                    localRepository.upsertSyncedConversation(remoteConv)
                    for (msg in remoteMessages) {
                        localRepository.upsertSyncedMessage(msg)
                    }
                    syncMutex.withLock {
                        conversationOwners[remoteConv.id] = userId
                        lastSyncedTimestampByConv["$userId:${remoteConv.id}"] = remoteConv.updatedAtMillis
                    }
                } else {
                    val localMessages = localRepository.getCloudMessageRecordsSync(localConv.id)
                    val syncKey = "$userId:${localConv.id}"
                    val lastSyncedAt = syncMutex.withLock { lastSyncedTimestampByConv[syncKey] }
                    val localOwner = syncMutex.withLock { conversationOwners[localConv.id] }
                    val consent = syncMutex.withLock { getConsentDecisionLocked(userId) }

                    val hasDivergentContent = localConv.title != remoteConv.title ||
                        localMessages.map { it.id to it.content } != remoteMessages.map { it.id to it.content }

                    // Case 1: Pre-existing local conversation collides with a cloud conversation ID before consent
                    if (localOwner == LOCAL_GUEST_OWNER && consent != SyncConsentDecision.APPROVED && hasDivergentContent) {
                        syncMutex.withLock {
                            activeConflicts[localConv.id] = SyncConflictItem(
                                conversationId = localConv.id,
                                localConversation = localConv,
                                cloudConversation = remoteConv,
                                localMessages = localMessages,
                                cloudMessages = remoteMessages
                            )
                        }
                        continue
                    }

                    // Case 2: Both local and cloud were modified independently since last sync
                    val bothModifiedSinceSync = lastSyncedAt != null &&
                        localConv.updatedAtMillis > lastSyncedAt &&
                        remoteConv.updatedAtMillis > lastSyncedAt &&
                        hasDivergentContent

                    // Or initial sync where both have different messages/titles and same or conflicting history
                    val initialDivergentConflict = lastSyncedAt == null &&
                        hasDivergentContent &&
                        localMessages.isNotEmpty() &&
                        remoteMessages.isNotEmpty() &&
                        (localMessages.map { it.id }.toSet() != remoteMessages.map { it.id }.toSet() ||
                            localMessages.any { lm -> remoteMessages.find { it.id == lm.id }?.content != lm.content })

                    if (bothModifiedSinceSync || initialDivergentConflict) {
                        syncMutex.withLock {
                            activeConflicts[localConv.id] = SyncConflictItem(
                                conversationId = localConv.id,
                                localConversation = localConv,
                                cloudConversation = remoteConv,
                                localMessages = localMessages,
                                cloudMessages = remoteMessages
                            )
                        }
                        continue
                    }

                    // Case 3: Cloud is strictly newer and local was not modified since last sync
                    if (remoteConv.updatedAtMillis > localConv.updatedAtMillis) {
                        localRepository.upsertSyncedConversation(remoteConv)
                        // Merge messages by ID without duplicating, keeping newer timestamp
                        val mergedMessages = mergeMessagesWithoutDuplicates(localMessages, remoteMessages)
                        for (msg in mergedMessages) {
                            localRepository.upsertSyncedMessage(msg)
                        }
                        syncMutex.withLock {
                            conversationOwners[remoteConv.id] = userId
                            lastSyncedTimestampByConv[syncKey] = remoteConv.updatedAtMillis
                        }
                    } else if (localConv.updatedAtMillis > remoteConv.updatedAtMillis) {
                        // Case 4: Local is newer -> DO NOT overwrite local with stale cloud data!
                        // Push newer local state to cloud if owned by userId
                        val isAllowedToUpload = syncMutex.withLock {
                            conversationOwners[localConv.id] == userId
                        }
                        if (isAllowedToUpload) {
                            pushConversationAndMessagesToCloud(userId, localConv, localMessages)
                        }
                    } else {
                        // Timestamps match; ensure any missing messages are merged without duplicates
                        val mergedMessages = mergeMessagesWithoutDuplicates(localMessages, remoteMessages)
                        if (mergedMessages.size > localMessages.size) {
                            for (msg in mergedMessages) {
                                localRepository.upsertSyncedMessage(msg)
                            }
                        }
                        syncMutex.withLock {
                            conversationOwners[remoteConv.id] = userId
                            lastSyncedTimestampByConv[syncKey] = remoteConv.updatedAtMillis
                        }
                    }
                }
            }

            // 3B. Upload local conversations owned by userId that do not yet exist in Cloud
            for (localConv in allLocal) {
                if (remoteById.containsKey(localConv.id)) continue
                val isOwnedByUser = syncMutex.withLock {
                    conversationOwners[localConv.id] == userId
                }
                if (isOwnedByUser) {
                    val localMessages = localRepository.getCloudMessageRecordsSync(localConv.id)
                    pushConversationAndMessagesToCloud(userId, localConv, localMessages)
                }
            }

            syncMutex.withLock {
                val snapshot = updateStateSnapshotLocked(
                    userId = userId,
                    lastError = null,
                    lastSyncedAtMillis = System.currentTimeMillis()
                )
                Result.success(snapshot)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            syncMutex.withLock {
                updateStateSnapshotLocked(userId, lastError = e.message ?: "Sync failed")
                Result.failure(e)
            }
        }
    }

    /**
     * Resolves a detected conflict only after explicit user confirmation.
     */
    suspend fun resolveConflict(
        userId: String,
        conversationId: String,
        strategy: ConflictResolutionStrategy
    ): Result<SyncStateSnapshot> {
        val conflict = syncMutex.withLock {
            activeConflicts[conversationId]
        } ?: return Result.failure(IllegalArgumentException("No active conflict found for conversation $conversationId"))

        return try {
            when (strategy) {
                ConflictResolutionStrategy.KEEP_CLOUD -> {
                    localRepository.deleteConversation(conversationId)
                    localRepository.upsertSyncedConversation(conflict.cloudConversation)
                    for (msg in conflict.cloudMessages) {
                        localRepository.upsertSyncedMessage(msg)
                    }
                    syncMutex.withLock {
                        conversationOwners[conversationId] = userId
                        lastSyncedTimestampByConv["$userId:$conversationId"] = conflict.cloudConversation.updatedAtMillis
                        activeConflicts.remove(conversationId)
                    }
                }

                ConflictResolutionStrategy.KEEP_LOCAL -> {
                    val updatedLocal = conflict.localConversation.copy(
                        userId = userId,
                        updatedAtMillis = maxOf(
                            conflict.localConversation.updatedAtMillis,
                            conflict.cloudConversation.updatedAtMillis + 1L
                        )
                    )
                    localRepository.upsertSyncedConversation(updatedLocal)
                    pushConversationAndMessagesToCloud(userId, updatedLocal, conflict.localMessages)
                    // Also remove any cloud-only messages that were not in local
                    val localIds = conflict.localMessages.map { it.id }.toSet()
                    for (cloudMsg in conflict.cloudMessages) {
                        if (cloudMsg.id !in localIds) {
                            cloudRepository.deleteMessage(userId, conversationId, cloudMsg.id)
                        }
                    }
                    syncMutex.withLock {
                        conversationOwners[conversationId] = userId
                        lastSyncedTimestampByConv["$userId:$conversationId"] = updatedLocal.updatedAtMillis
                        activeConflicts.remove(conversationId)
                    }
                }

                ConflictResolutionStrategy.MERGE_BOTH -> {
                    val mergedMessages = mergeMessagesWithoutDuplicates(
                        conflict.localMessages,
                        conflict.cloudMessages
                    )
                    val newestUpdatedAt = maxOf(
                        conflict.localConversation.updatedAtMillis,
                        conflict.cloudConversation.updatedAtMillis
                    ) + 1L
                    val chosenTitle = if (conflict.localConversation.updatedAtMillis >= conflict.cloudConversation.updatedAtMillis) {
                        conflict.localConversation.title
                    } else {
                        conflict.cloudConversation.title
                    }
                    val lastMsgText = mergedMessages.lastOrNull()?.content?.take(1000)
                        ?: conflict.localConversation.lastMessage

                    val mergedConv = conflict.localConversation.copy(
                        userId = userId,
                        title = chosenTitle,
                        updatedAtMillis = newestUpdatedAt,
                        isPinned = conflict.localConversation.isPinned || conflict.cloudConversation.isPinned,
                        lastMessage = lastMsgText
                    )

                    localRepository.upsertSyncedConversation(mergedConv)
                    for (msg in mergedMessages) {
                        localRepository.upsertSyncedMessage(msg)
                    }
                    pushConversationAndMessagesToCloud(userId, mergedConv, mergedMessages)
                    syncMutex.withLock {
                        conversationOwners[conversationId] = userId
                        lastSyncedTimestampByConv["$userId:$conversationId"] = mergedConv.updatedAtMillis
                        activeConflicts.remove(conversationId)
                    }
                }
            }

            syncMutex.withLock {
                val snap = updateStateSnapshotLocked(
                    userId = userId,
                    lastError = null,
                    lastSyncedAtMillis = System.currentTimeMillis()
                )
                Result.success(snap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            syncMutex.withLock {
                updateStateSnapshotLocked(userId, lastError = e.message)
                Result.failure(e)
            }
        }
    }

    /**
     * Deletes all Cloud Firestore conversations for [userId].
     * If [alsoDeleteLocalSynced] is true, removes synced cloud conversations from local cache as well.
     */
    suspend fun deleteAllCloudData(userId: String, alsoDeleteLocalSynced: Boolean = false): Result<Unit> {
        val res = cloudRepository.deleteAllUserCloudData(userId)
        if (res.isFailure) {
            return res
        }
        syncMutex.withLock {
            pendingOperations.removeAll { it.userId == userId }
            activeConflicts.clear()
            lastSyncedTimestampByConv.keys.removeAll { it.startsWith("$userId:") }

            if (alsoDeleteLocalSynced) {
                val ownedIds = conversationOwners.filterValues { it == userId }.keys.toList()
                for (id in ownedIds) {
                    localRepository.deleteConversation(id)
                    conversationOwners.remove(id)
                }
            } else {
                // Demote synced conversations back to local-only
                val ownedIds = conversationOwners.filterValues { it == userId }.keys.toList()
                for (id in ownedIds) {
                    conversationOwners[id] = LOCAL_GUEST_OWNER
                    originallyLocalConversationIds.add(id)
                }
            }
            updateStateSnapshotLocked(userId, lastError = null, lastSyncedAtMillis = System.currentTimeMillis())
        }
        return Result.success(Unit)
    }

    private suspend fun fetchAllMessagesPaginated(
        userId: String,
        conversationId: String
    ): List<CloudMessageRecord> {
        val all = mutableListOf<CloudMessageRecord>()
        var offset = 0
        val msgPageSize = 100
        while (true) {
            val res = cloudRepository.fetchMessagesForConversation(
                userId = userId,
                conversationId = conversationId,
                limit = msgPageSize,
                offset = offset
            )
            if (res.isFailure) throw (res.exceptionOrNull() ?: Exception("Failed to fetch messages"))
            val page = res.getOrDefault(emptyList())
            all.addAll(page)
            if (page.size < msgPageSize) break
            offset += msgPageSize
        }
        return all
    }

    private suspend fun pushConversationAndMessagesToCloud(
        userId: String,
        conversation: CloudConversationRecord,
        messages: List<CloudMessageRecord>
    ) {
        val convWithUser = conversation.copy(userId = userId)
        val convRes = cloudRepository.upsertConversation(userId, convWithUser)
        if (convRes.isFailure) {
            enqueueOfflineOperation(
                PendingSyncOperation(
                    operationId = "upsert_conv_${userId}_${conversation.id}",
                    userId = userId,
                    kind = SyncOperationKind.UPSERT_CONVERSATION,
                    conversationId = conversation.id,
                    conversationPayload = convWithUser
                )
            )
            for (msg in messages) {
                enqueueOfflineOperation(
                    PendingSyncOperation(
                        operationId = "upsert_msg_${userId}_${conversation.id}_${msg.id}",
                        userId = userId,
                        kind = SyncOperationKind.UPSERT_MESSAGE,
                        conversationId = conversation.id,
                        messageId = msg.id,
                        messagePayload = msg
                    )
                )
            }
            throw (convRes.exceptionOrNull() ?: Exception("Failed to upload conversation"))
        }

        for (msg in messages) {
            val msgRes = cloudRepository.upsertMessage(userId, conversation.id, msg)
            if (msgRes.isFailure) {
                enqueueOfflineOperation(
                    PendingSyncOperation(
                        operationId = "upsert_msg_${userId}_${conversation.id}_${msg.id}",
                        userId = userId,
                        kind = SyncOperationKind.UPSERT_MESSAGE,
                        conversationId = conversation.id,
                        messageId = msg.id,
                        messagePayload = msg
                    )
                )
                throw (msgRes.exceptionOrNull() ?: Exception("Failed to upload message"))
            }
        }

        syncMutex.withLock {
            lastSyncedTimestampByConv["$userId:${conversation.id}"] = conversation.updatedAtMillis
        }
    }

    private fun mergeMessagesWithoutDuplicates(
        localMessages: List<CloudMessageRecord>,
        cloudMessages: List<CloudMessageRecord>
    ): List<CloudMessageRecord> {
        val byId = linkedMapOf<String, CloudMessageRecord>()
        for (msg in localMessages) {
            byId[msg.id] = msg
        }
        for (cloudMsg in cloudMessages) {
            val existing = byId[cloudMsg.id]
            if (existing == null) {
                byId[cloudMsg.id] = cloudMsg
            } else {
                // Keep the version with newer timestamp or non-empty completed content
                val preferCloud = cloudMsg.timestampMillis > existing.timestampMillis ||
                    (cloudMsg.timestampMillis == existing.timestampMillis &&
                        cloudMsg.content.length > existing.content.length)
                if (preferCloud) {
                    byId[cloudMsg.id] = cloudMsg
                }
            }
        }
        return byId.values.sortedWith(compareBy<CloudMessageRecord> { it.timestampMillis }.thenBy { it.id })
    }

    private suspend fun enqueueOfflineOperation(operation: PendingSyncOperation) {
        syncMutex.withLock {
            // Deduplicate by operationId so retries never produce duplicate writes
            val existingIdx = pendingOperations.indexOfFirst { it.operationId == operation.operationId }
            if (existingIdx != -1) {
                val prev = pendingOperations[existingIdx]
                pendingOperations[existingIdx] = operation.copy(retryCount = prev.retryCount + 1)
            } else {
                pendingOperations.add(operation)
            }
            updateStateSnapshotLocked(
                userId = operation.userId,
                lastError = "Offline • Changes queued for sync"
            )
        }
    }

    private suspend fun flushPendingQueue(userId: String): Throwable? {
        val opsForUser = syncMutex.withLock {
            pendingOperations.filter { it.userId == userId }.toList()
        }
        for (op in opsForUser) {
            val result: Result<Unit> = when (op.kind) {
                SyncOperationKind.UPSERT_CONVERSATION -> {
                    val payload = op.conversationPayload
                        ?: localRepository.getCloudConversationRecordById(op.conversationId, userId)
                    if (payload != null) {
                        cloudRepository.upsertConversation(userId, payload)
                    } else {
                        Result.success(Unit)
                    }
                }
                SyncOperationKind.DELETE_CONVERSATION -> {
                    cloudRepository.deleteConversation(userId, op.conversationId)
                }
                SyncOperationKind.UPSERT_MESSAGE -> {
                    val payload = op.messagePayload
                    if (payload != null) {
                        cloudRepository.upsertMessage(userId, op.conversationId, payload)
                    } else {
                        Result.success(Unit)
                    }
                }
                SyncOperationKind.DELETE_MESSAGE -> {
                    val msgId = op.messageId
                    if (msgId != null) {
                        cloudRepository.deleteMessage(userId, op.conversationId, msgId)
                    } else {
                        Result.success(Unit)
                    }
                }
            }

            if (result.isFailure) {
                syncMutex.withLock {
                    val idx = pendingOperations.indexOfFirst { it.operationId == op.operationId }
                    if (idx != -1) {
                        val current = pendingOperations[idx]
                        pendingOperations[idx] = current.copy(retryCount = current.retryCount + 1)
                    }
                }
                return result.exceptionOrNull() ?: Exception("Offline queue flush failed")
            } else {
                syncMutex.withLock {
                    pendingOperations.removeAll { it.operationId == op.operationId }
                }
            }
        }
        return null
    }

    private suspend fun isolatePreviousUserDataLocked(previousUid: String) {
        val ownedByPrev = conversationOwners.filterValues { it == previousUid }.keys.toList()
        val stashList = mutableListOf<Pair<CloudConversationRecord, List<CloudMessageRecord>>>()
        for (convId in ownedByPrev) {
            val conv = localRepository.getCloudConversationRecordById(convId, previousUid)
            val msgs = localRepository.getCloudMessageRecordsSync(convId)
            if (conv != null) {
                stashList.add(conv to msgs)
            }
            localRepository.deleteConversation(convId)
            conversationOwners.remove(convId)
        }
        if (stashList.isNotEmpty()) {
            stashedUserConversations[previousUid] = stashList
        }
    }

    private fun claimLocalConversationsForUserLocked(userId: String) {
        val localIds = conversationOwners.filterValues { it == LOCAL_GUEST_OWNER }.keys.toList()
        for (id in localIds) {
            conversationOwners[id] = userId
        }
    }

    private fun getConsentDecisionLocked(userId: String): SyncConsentDecision {
        return consentByUser[userId] ?: run {
            val saved = prefs?.getString("consent_$userId", null)
            val parsed = saved?.let {
                try {
                    SyncConsentDecision.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            } ?: SyncConsentDecision.UNDECIDED
            consentByUser[userId] = parsed
            parsed
        }
    }

    private fun countUnclaimedLocalConversationsLocked(): Int {
        return conversationOwners.count { it.value == LOCAL_GUEST_OWNER }
    }

    private suspend fun refreshPendingCountsLocked() {
        val allLocal = localRepository.getAllCloudConversationRecords(activeUserId ?: "")
        for (conv in allLocal) {
            if (!conversationOwners.containsKey(conv.id)) {
                conversationOwners[conv.id] = activeUserId ?: LOCAL_GUEST_OWNER
                if (activeUserId == null) {
                    originallyLocalConversationIds.add(conv.id)
                }
            }
        }
        val uid = activeUserId
        if (uid == null) {
            _syncState.value = SyncStateSnapshot(
                status = SyncStatus.LOCAL_ONLY,
                consentDecision = SyncConsentDecision.UNDECIDED,
                pendingLocalConversationsCount = countUnclaimedLocalConversationsLocked(),
                syncedConversationsCount = 0,
                queuedOfflineOperationsCount = 0,
                activeConflicts = emptyList(),
                activeUserId = null
            )
        } else {
            updateStateSnapshotLocked(uid, _syncState.value.lastErrorMessage, _syncState.value.lastSyncedAtMillis)
        }
    }

    private fun updateStateSnapshotLocked(
        userId: String,
        lastError: String?,
        lastSyncedAtMillis: Long? = _syncState.value.lastSyncedAtMillis
    ): SyncStateSnapshot {
        val consent = getConsentDecisionLocked(userId)
        val pendingLocalCount = countUnclaimedLocalConversationsLocked()
        val syncedCount = conversationOwners.count { it.value == userId }
        val queuedCount = pendingOperations.count { it.userId == userId }
        val conflicts = activeConflicts.values.toList()

        val resolvedStatus = when {
            conflicts.isNotEmpty() -> SyncStatus.CONFLICT_DETECTED
            queuedCount > 0 -> SyncStatus.OFFLINE_PENDING
            lastError != null -> SyncStatus.ERROR
            pendingLocalCount > 0 && consent == SyncConsentDecision.UNDECIDED -> SyncStatus.AWAITING_CONSENT
            else -> SyncStatus.SYNCED
        }

        val snapshot = SyncStateSnapshot(
            status = resolvedStatus,
            consentDecision = consent,
            pendingLocalConversationsCount = pendingLocalCount,
            syncedConversationsCount = syncedCount,
            queuedOfflineOperationsCount = queuedCount,
            activeConflicts = conflicts,
            lastSyncedAtMillis = lastSyncedAtMillis,
            lastErrorMessage = lastError,
            activeUserId = userId
        )
        _syncState.value = snapshot
        return snapshot
    }

    companion object {
        private const val LOCAL_GUEST_OWNER = "__LOCAL_GUEST__"
    }
}
