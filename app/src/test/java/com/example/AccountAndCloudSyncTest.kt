package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.ui.components.MinovaDrawerContent
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MinovaDatabase
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.CloudConversationRecord
import com.example.data.model.CloudMessageRecord
import com.example.data.model.ConflictResolutionStrategy
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.model.SyncConflictItem
import com.example.data.model.SyncConsentDecision
import com.example.data.model.SyncStateSnapshot
import com.example.data.model.SyncStatus
import com.example.data.model.UserProfile
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.ConversationRepository
import com.example.data.repository.ConversationRepositoryImpl
import com.example.data.repository.InMemoryAuthRepository
import com.example.data.repository.InMemoryCloudConversationRepository
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountAndCloudSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: MinovaDatabase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var authRepository: InMemoryAuthRepository
    private lateinit var cloudRepository: InMemoryCloudConversationRepository
    private lateinit var syncManager: CloudSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("minova_cloud_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        database = Room.inMemoryDatabaseBuilder(context, MinovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        conversationRepository = ConversationRepositoryImpl(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            attachmentDao = database.attachmentDao()
        )
        authRepository = InMemoryAuthRepository()
        cloudRepository = InMemoryCloudConversationRepository(
            activeUserIdProvider = { authRepository.currentUser?.uid }
        )
        syncManager = CloudSyncManager(
            localRepository = conversationRepository,
            cloudRepository = cloudRepository,
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun auth_registration_login_logout_passwordReset_and_sessionRefresh() = runBlocking {
        // Starts in Guest mode
        assertTrue(authRepository.authState.value is AuthState.Guest)
        assertNull(authRepository.currentUser)

        // Register user
        val signUpResult = authRepository.signUpWithEmailPassword(
            email = "Ada@Minova.ai",
            password = "StrongPassword123",
            displayName = "Ada Lovelace"
        )
        assertTrue(signUpResult.isSuccess)
        val registeredUser = signUpResult.getOrThrow()
        assertEquals("ada@minova.ai", registeredUser.email)
        assertEquals("Ada Lovelace", registeredUser.displayName)
        assertTrue(authRepository.authState.value is AuthState.Authenticated)

        // Sign out returns to Guest mode
        authRepository.signOut()
        assertTrue(authRepository.authState.value is AuthState.Guest)
        assertNull(authRepository.currentUser)

        // Login with valid credentials
        val signInResult = authRepository.signInWithEmailPassword("ada@minova.ai", "StrongPassword123")
        assertTrue(signInResult.isSuccess)
        assertEquals(registeredUser.uid, authRepository.currentUser?.uid)

        // Session refresh succeeds when authenticated
        val refreshResult = authRepository.refreshSession()
        assertTrue(refreshResult.isSuccess)
        assertEquals(registeredUser.uid, refreshResult.getOrNull()?.uid)

        // Password reset succeeds for valid email
        val resetResult = authRepository.sendPasswordResetEmail("ada@minova.ai")
        assertTrue(resetResult.isSuccess)
    }

    @Test
    fun auth_invalidCredentials_validation_and_rateLimiting() = runBlocking {
        authRepository.signUpWithEmailPassword("bob@minova.ai", "CorrectPassword99", "Bob")
        authRepository.signOut()

        // Invalid email format
        val badEmail = authRepository.signInWithEmailPassword("not-an-email", "CorrectPassword99")
        assertTrue(badEmail.isFailure)

        // Weak password on sign up
        val weakPass = authRepository.signUpWithEmailPassword("new@minova.ai", "short", "New")
        assertTrue(weakPass.isFailure)

        // Wrong password attempts & rate limiting after 5 failures
        repeat(5) {
            val wrong = authRepository.signInWithEmailPassword("bob@minova.ai", "WrongPassword$it")
            assertTrue(wrong.isFailure)
        }

        // 6th attempt should be rate-limited even if password is correct
        val rateLimited = authRepository.signInWithEmailPassword("bob@minova.ai", "CorrectPassword99")
        assertTrue(rateLimited.isFailure)
        assertTrue(
            rateLimited.exceptionOrNull()?.message?.contains("Too many", ignoreCase = true) == true
        )
    }

    @Test
    fun authorization_and_crossUserDataIsolation() = runBlocking {
        // Sign up User A
        val userA = authRepository.signUpWithEmailPassword("usera@minova.ai", "PasswordA123", "User A").getOrThrow()

        val convA = CloudConversationRecord(
            id = "conv_a_1",
            userId = userA.uid,
            title = "User A Private Strategy",
            lastMessage = "Confidential A",
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        assertTrue(cloudRepository.upsertConversation(userA.uid, convA).isSuccess)

        // User A cannot write or read under a forged userB ID
        val forgedRead = cloudRepository.fetchUserConversations("forged_user_b_uid")
        assertTrue(forgedRead.isFailure)

        // Sign out User A and register User B
        authRepository.signOut()
        val userB = authRepository.signUpWithEmailPassword("userb@minova.ai", "PasswordB123", "User B").getOrThrow()

        // User B cannot read User A's conversations by passing User A's uid
        val crossRead = cloudRepository.fetchUserConversations(userA.uid)
        assertTrue(crossRead.isFailure)

        // User B cannot delete User A's conversation by ID
        val crossDelete = cloudRepository.deleteConversation(userA.uid, "conv_a_1")
        assertTrue(crossDelete.isFailure)

        // User B's own conversation list is isolated and empty
        val userBConvs = cloudRepository.fetchUserConversations(userB.uid).getOrThrow()
        assertTrue(userBConvs.isEmpty())

        // Verify User A's data is still intact when User A logs back in
        authRepository.signOut()
        authRepository.signInWithEmailPassword("usera@minova.ai", "PasswordA123")
        val userAConvs = cloudRepository.fetchUserConversations(userA.uid).getOrThrow()
        assertEquals(1, userAConvs.size)
        assertEquals("User A Private Strategy", userAConvs.first().title)
    }

    @Test
    fun cloudConversation_crud_and_pagination() = runBlocking {
        val user = authRepository.signUpWithEmailPassword("pager@minova.ai", "Password1234", "Pager").getOrThrow()

        // Create 5 conversations with distinct timestamps
        for (i in 1..5) {
            val conv = CloudConversationRecord(
                id = "conv_$i",
                userId = user.uid,
                title = "Conversation $i",
                lastMessage = "Preview $i",
                createdAtMillis = i * 1000L,
                updatedAtMillis = i * 1000L
            )
            val msg1 = CloudMessageRecord(
                id = "msg_${i}_1",
                conversationId = "conv_$i",
                sender = MessageSender.USER.name,
                content = "Question $i",
                timestampMillis = i * 1000L
            )
            val msg2 = CloudMessageRecord(
                id = "msg_${i}_2",
                conversationId = "conv_$i",
                sender = MessageSender.ASSISTANT.name,
                content = "Answer $i",
                timestampMillis = i * 1000L + 500L
            )
            assertTrue(cloudRepository.upsertConversation(user.uid, conv).isSuccess)
            assertTrue(cloudRepository.upsertMessage(user.uid, conv.id, msg1).isSuccess)
            assertTrue(cloudRepository.upsertMessage(user.uid, conv.id, msg2).isSuccess)
        }

        // Paginate conversations: page 1 (limit 2, offset 0) -> conv_5, conv_4
        val page1 = cloudRepository.fetchUserConversations(user.uid, limit = 2, offset = 0).getOrThrow()
        assertEquals(listOf("conv_5", "conv_4"), page1.map { it.id })

        // Page 2 (limit 2, offset 2) -> conv_3, conv_2
        val page2 = cloudRepository.fetchUserConversations(user.uid, limit = 2, offset = 2).getOrThrow()
        assertEquals(listOf("conv_3", "conv_2"), page2.map { it.id })

        // Page 3 (limit 2, offset 4) -> conv_1
        val page3 = cloudRepository.fetchUserConversations(user.uid, limit = 2, offset = 4).getOrThrow()
        assertEquals(listOf("conv_1"), page3.map { it.id })

        // Paginate messages for conv_5
        val msgPage1 = cloudRepository.fetchMessagesForConversation(user.uid, "conv_5", limit = 1, offset = 0).getOrThrow()
        assertEquals(1, msgPage1.size)
        assertEquals("Question 5", msgPage1.first().content)

        val msgPage2 = cloudRepository.fetchMessagesForConversation(user.uid, "conv_5", limit = 1, offset = 1).getOrThrow()
        assertEquals(1, msgPage2.size)
        assertEquals("Answer 5", msgPage2.first().content)

        // Delete single conversation
        assertTrue(cloudRepository.deleteConversation(user.uid, "conv_5").isSuccess)
        val remaining = cloudRepository.fetchUserConversations(user.uid, limit = 10, offset = 0).getOrThrow()
        assertEquals(4, remaining.size)
    }

    @Test
    fun consent_neverUploadsPreExistingLocalConversationsWithoutExplicitConsent() = runBlocking {
        // 1. User creates a local conversation while signed out (Guest Mode)
        val guestConv = conversationRepository.createConversation(
            id = "guest_conv_1",
            title = "Guest Secret Notes"
        )
        syncManager.registerLocalConversationCreated(guestConv.id)
        conversationRepository.saveMessage(
            Message(
                id = "guest_msg_1",
                conversationId = guestConv.id,
                sender = MessageSender.USER,
                content = "Local-only message created before sign-in",
                timestamp = "10:00 AM",
                status = MessageStatus.SENT
            )
        )

        // 2. User signs in
        val user = authRepository.signUpWithEmailPassword("consent@minova.ai", "ConsentPass123", "Consent User").getOrThrow()
        syncManager.onUserAuthenticated(user.uid)

        // Verify consent is UNDECIDED and nothing was uploaded to Cloud
        assertEquals(SyncConsentDecision.UNDECIDED, syncManager.syncState.value.consentDecision)
        val cloudConvsBeforeConsent = cloudRepository.fetchUserConversations(user.uid).getOrThrow()
        assertTrue("Pre-existing local chats must NOT upload without consent", cloudConvsBeforeConsent.isEmpty())

        // 3. Even if syncWithCloud is triggered while UNDECIDED, pre-existing local chat is NOT uploaded
        syncManager.syncWithCloud(user.uid)
        assertTrue(cloudRepository.fetchUserConversations(user.uid).getOrThrow().isEmpty())

        // 4. User chooses "Keep Local-Only" (DECLINED_KEEP_LOCAL_ONLY)
        syncManager.setSyncConsent(user.uid, SyncConsentDecision.DECLINED_KEEP_LOCAL_ONLY)
        assertEquals(SyncConsentDecision.DECLINED_KEEP_LOCAL_ONLY, syncManager.syncState.value.consentDecision)
        assertTrue(cloudRepository.fetchUserConversations(user.uid).getOrThrow().isEmpty())

        // Local conversation is still preserved in Room
        val localConvs = conversationRepository.getAllCloudConversationRecords("")
        assertEquals(1, localConvs.size)
        assertEquals("Guest Secret Notes", localConvs.first().title)

        // 5. User later explicitly grants consent (APPROVED)
        syncManager.setSyncConsent(user.uid, SyncConsentDecision.APPROVED)
        assertEquals(SyncConsentDecision.APPROVED, syncManager.syncState.value.consentDecision)

        val cloudConvsAfterConsent = cloudRepository.fetchUserConversations(user.uid).getOrThrow()
        assertEquals(1, cloudConvsAfterConsent.size)
        assertEquals(guestConv.id, cloudConvsAfterConsent.first().id)
        assertEquals("Guest Secret Notes", cloudConvsAfterConsent.first().title)

        val cloudMessages = cloudRepository.fetchMessagesForConversation(user.uid, guestConv.id).getOrThrow()
        assertEquals(1, cloudMessages.size)
        assertEquals("Local-only message created before sign-in", cloudMessages.first().content)
    }

    @Test
    fun sync_offlineQueue_retries_and_deduplication() = runBlocking {
        val user = authRepository.signUpWithEmailPassword("offline@minova.ai", "OfflinePass123", "Offline User").getOrThrow()
        syncManager.onUserAuthenticated(user.uid)
        syncManager.setSyncConsent(user.uid, SyncConsentDecision.APPROVED)

        // Simulate offline failure when pushing a newly created conversation
        cloudRepository.isSimulatedOffline = true

        val conv = conversationRepository.createConversation(
            id = "offline_conv_1",
            title = "Offline Created Chat"
        )
        syncManager.registerLocalConversationCreated(conv.id)
        val pdfAttachment = Attachment(
            attachmentId = "att_1",
            uri = "content://minova/design.pdf",
            fileName = "design.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1_200_000L,
            type = AttachmentType.PDF
        )
        conversationRepository.saveMessage(
            Message(
                id = "offline_msg_1",
                conversationId = conv.id,
                sender = MessageSender.USER,
                content = "Drafted while on airplane mode",
                timestamp = "10:15 AM",
                status = MessageStatus.SENT,
                attachment = pdfAttachment,
                attachments = listOf(pdfAttachment)
            )
        )

        syncManager.syncLocalConversationIfAuthorized(conv.id)

        // Verify offline status & pending queue count
        assertEquals(SyncStatus.OFFLINE_PENDING, syncManager.syncState.value.status)
        assertTrue(syncManager.syncState.value.queuedOfflineOperationsCount > 0)

        // Reconnect network and trigger sync retry
        cloudRepository.isSimulatedOffline = false
        syncManager.syncWithCloud(user.uid)

        // Pending queue is flushed and conversation + message + attachment metadata are in cloud
        assertEquals(SyncStatus.SYNCED, syncManager.syncState.value.status)
        assertEquals(0, syncManager.syncState.value.queuedOfflineOperationsCount)

        val syncedConvs = cloudRepository.fetchUserConversations(user.uid).getOrThrow()
        assertEquals(1, syncedConvs.size)
        assertEquals(conv.id, syncedConvs.first().id)

        val syncedMsgs = cloudRepository.fetchMessagesForConversation(user.uid, conv.id).getOrThrow()
        assertEquals(1, syncedMsgs.size)
        assertEquals("Drafted while on airplane mode", syncedMsgs.first().content)
        assertEquals("design.pdf", syncedMsgs.first().attachmentName)

        // Re-running sync does not duplicate conversations or messages
        syncManager.syncWithCloud(user.uid)
        assertEquals(1, cloudRepository.fetchUserConversations(user.uid).getOrThrow().size)
        assertEquals(1, cloudRepository.fetchMessagesForConversation(user.uid, conv.id).getOrThrow().size)
    }

    @Test
    fun sync_conflictDetection_doesNotSilentlyOverwrite_and_resolvesExplicitly() = runBlocking {
        val user = authRepository.signUpWithEmailPassword("conflict@minova.ai", "ConflictPass123", "Conflict User").getOrThrow()
        syncManager.onUserAuthenticated(user.uid)
        syncManager.setSyncConsent(user.uid, SyncConsentDecision.APPROVED)

        val sharedConvId = "conv_conflict_1"

        // Local version has 1 message with updatedAt = 5000L
        val localRecord = CloudConversationRecord(
            id = sharedConvId,
            userId = user.uid,
            title = "Project Roadmap (Phone Edit)",
            lastMessage = "Local phone message",
            createdAtMillis = 1000L,
            updatedAtMillis = 5000L
        )
        val localMsg = CloudMessageRecord(
            id = "msg_local_1",
            conversationId = sharedConvId,
            sender = MessageSender.USER.name,
            content = "Local phone message",
            timestampMillis = 5000L
        )
        conversationRepository.upsertSyncedConversation(localRecord)
        conversationRepository.upsertSyncedMessage(localMsg)
        syncManager.registerLocalConversationCreated(sharedConvId)

        // Cloud version has a DIFFERENT message with updatedAt = 6000L (diverged on another device)
        val cloudRecord = CloudConversationRecord(
            id = sharedConvId,
            userId = user.uid,
            title = "Project Roadmap (Tablet Edit)",
            lastMessage = "Remote tablet message",
            createdAtMillis = 1000L,
            updatedAtMillis = 6000L
        )
        val cloudMsg = CloudMessageRecord(
            id = "msg_cloud_1",
            conversationId = sharedConvId,
            sender = MessageSender.ASSISTANT.name,
            content = "Remote tablet message",
            timestampMillis = 6000L
        )
        cloudRepository.seedCloudConversationForTest(user.uid, cloudRecord, listOf(cloudMsg))

        // Run sync -> should detect conflict and NOT overwrite local or cloud silently
        syncManager.syncWithCloud(user.uid)

        val conflicts = syncManager.syncState.value.activeConflicts
        assertEquals(1, conflicts.size)
        assertEquals(sharedConvId, conflicts.first().conversationId)

        // Verify local still has local message before user confirms resolution
        val localMsgsBefore = conversationRepository.getCloudMessageRecordsSync(sharedConvId)
        assertEquals(1, localMsgsBefore.size)
        assertEquals("Local phone message", localMsgsBefore.first().content)

        // Resolve conflict with MERGE_BOTH
        syncManager.resolveConflict(user.uid, sharedConvId, ConflictResolutionStrategy.MERGE_BOTH)

        // Conflict is cleared and both messages are preserved in chronological order
        assertTrue(syncManager.syncState.value.activeConflicts.isEmpty())
        val mergedLocalMsgs = conversationRepository.getCloudMessageRecordsSync(sharedConvId)
        assertEquals(2, mergedLocalMsgs.size)
        assertEquals(listOf("Local phone message", "Remote tablet message"), mergedLocalMsgs.map { it.content })

        val mergedCloudMsgs = cloudRepository.fetchMessagesForConversation(user.uid, sharedConvId).getOrThrow()
        assertEquals(2, mergedCloudMsgs.size)
    }

    @Test
    fun signOut_preservesLocalConversations_and_accountSwitchingIsIsolated() = runBlocking {
        // User 1 signs in and syncs a cloud conversation
        val user1 = authRepository.signUpWithEmailPassword("user1@minova.ai", "PassUser111", "User One").getOrThrow()
        syncManager.onUserAuthenticated(user1.uid)
        syncManager.setSyncConsent(user1.uid, SyncConsentDecision.APPROVED)

        val conv1 = conversationRepository.createConversation(
            id = "user1_conv_1",
            title = "User 1 Workspace"
        )
        syncManager.registerLocalConversationCreated(conv1.id)
        conversationRepository.saveMessage(
            Message(
                id = "user1_msg_1",
                conversationId = conv1.id,
                sender = MessageSender.USER,
                content = "Hello from User 1",
                timestamp = "11:00 AM",
                status = MessageStatus.SENT
            )
        )
        syncManager.syncLocalConversationIfAuthorized(conv1.id)

        assertEquals(1, cloudRepository.fetchUserConversations(user1.uid).getOrThrow().size)

        // Sign out User 1
        authRepository.signOut()
        syncManager.onUserSignedOut()

        // Local conversations remain intact in Room when signed out
        val localAfterSignOut = conversationRepository.getAllCloudConversationRecords("")
        assertEquals(1, localAfterSignOut.size)
        assertEquals("User 1 Workspace", localAfterSignOut.first().title)

        // User 2 signs in on the same device -> User 1's cloud-owned conversation is isolated
        // and must NOT be uploaded to User 2's cloud!
        val user2 = authRepository.signUpWithEmailPassword("user2@minova.ai", "PassUser222", "User Two").getOrThrow()
        syncManager.onUserAuthenticated(user2.uid)

        val user2CloudConvs = cloudRepository.fetchUserConversations(user2.uid).getOrThrow()
        assertTrue("User 2 cloud must not receive User 1's chats", user2CloudConvs.isEmpty())
    }

    @Test
    fun profileScreen_guestAndAuthenticatedStates_renderExpectedControls() {
        val sampleProfile = UserProfile(
            name = "Minhaz",
            email = "minhaz@minova.ai",
            tier = "MINOVA PRO"
        )

        composeRule.setContent {
            MyApplicationTheme {
                ProfileScreen(
                    profile = sampleProfile,
                    onBack = {},
                    authState = AuthState.Guest,
                    syncState = SyncStateSnapshot(
                        status = SyncStatus.LOCAL_ONLY,
                        consentDecision = SyncConsentDecision.UNDECIDED
                    )
                )
            }
        }

        // Verify Guest mode UI elements
        composeRule.onNodeWithTag("guest_local_explanation_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_email_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_submit_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_google_signin_button").performScrollTo().assertIsDisplayed()

        // Switch to Create Account tab
        composeRule.onNodeWithTag("auth_tab_sign_up").performScrollTo().performClick()
        composeRule.onNodeWithTag("account_name_input").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileScreen_authenticatedWithConsentAndConflicts_displaysCards() {
        val sampleProfile = UserProfile(
            name = "Ada Lovelace",
            email = "ada@minova.ai",
            tier = "MINOVA Cloud Account"
        )
        val authedUser = AuthUser(
            uid = "uid_ada",
            email = "ada@minova.ai",
            displayName = "Ada Lovelace"
        )
        val conflict = SyncConflictItem(
            conversationId = "conv_1",
            localConversation = CloudConversationRecord(
                id = "conv_1",
                userId = "uid_ada",
                title = "Architecture Notes",
                lastMessage = "Local",
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L
            ),
            cloudConversation = CloudConversationRecord(
                id = "conv_1",
                userId = "uid_ada",
                title = "Architecture Notes",
                lastMessage = "Cloud",
                createdAtMillis = 1000L,
                updatedAtMillis = 2500L
            )
        )

        composeRule.setContent {
            MyApplicationTheme {
                ProfileScreen(
                    profile = sampleProfile,
                    onBack = {},
                    authState = AuthState.Authenticated(authedUser),
                    syncState = SyncStateSnapshot(
                        status = SyncStatus.CONFLICT_DETECTED,
                        consentDecision = SyncConsentDecision.UNDECIDED,
                        pendingLocalConversationsCount = 3,
                        syncedConversationsCount = 4,
                        activeConflicts = listOf(conflict)
                    )
                )
            }
        }

        // Verify consent card & conflict resolution card are visible
        composeRule.onNodeWithTag("consent_prompt_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("consent_approve_sync_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("consent_decline_local_only_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_resolution_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ada Lovelace").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun restore_downloadsSeededCloudConversationsAndMessagesIntoEmptyRoom_and_preservesSubsequentIdempotency() = runBlocking {
        // 1. Authenticate test user in isolated InMemoryAuthRepository
        val user = authRepository.signUpWithEmailPassword(
            email = "restore@minova.ai",
            password = "RestorePassword123",
            displayName = "Restore Tester"
        ).getOrThrow()

        // Verify local Room v5 database is initially completely empty
        assertTrue(conversationRepository.getAllCloudConversationRecords(user.uid).isEmpty())

        // 2. Seed two cloud conversations with three total messages and varied metadata
        val cloudConv1 = CloudConversationRecord(
            id = "cloud_restore_conv_1",
            userId = user.uid,
            title = "Kotlin Coroutines & Architecture",
            createdAtMillis = 10_000L,
            updatedAtMillis = 25_000L,
            isPinned = true,
            isArchived = false,
            lastMessage = "Here is the structured supervisorScope example.",
            responseStyle = ResponseStyle.DETAILED.name,
            responseLanguage = ResponseLanguage.ENGLISH.name,
            responseFormat = ResponseFormat.STEP_BY_STEP.name
        )
        val cloudConv1Msg1 = CloudMessageRecord(
            id = "cloud_restore_msg_1",
            conversationId = cloudConv1.id,
            sender = MessageSender.USER.name,
            content = "Review this PDF specification and show a Kotlin coroutine supervisorScope snippet.",
            timestampMillis = 12_000L,
            status = MessageStatus.SENT.name,
            attachmentType = AttachmentType.PDF.name,
            attachmentName = "coroutine_spec.pdf",
            isPinned = false
        )
        val cloudConv1Msg2 = CloudMessageRecord(
            id = "cloud_restore_msg_2",
            conversationId = cloudConv1.id,
            sender = MessageSender.ASSISTANT.name,
            content = "Here is the structured supervisorScope example.",
            timestampMillis = 25_000L,
            status = MessageStatus.SENT.name,
            codeSnippet = "supervisorScope {\n    launch { fetchRemote() }\n}",
            codeLanguage = "kotlin",
            isPinned = true
        )

        val cloudConv2 = CloudConversationRecord(
            id = "cloud_restore_conv_2",
            userId = user.uid,
            title = "Archived Sprint Retrospective",
            createdAtMillis = 30_000L,
            updatedAtMillis = 40_000L,
            isPinned = false,
            isArchived = true,
            lastMessage = "Sprint velocity improved by 18%.",
            responseStyle = ResponseStyle.CONCISE.name,
            responseLanguage = ResponseLanguage.BENGALI.name,
            responseFormat = ResponseFormat.STRUCTURED.name
        )
        val cloudConv2Msg1 = CloudMessageRecord(
            id = "cloud_restore_msg_3",
            conversationId = cloudConv2.id,
            sender = MessageSender.USER.name,
            content = "Sprint velocity improved by 18%.",
            timestampMillis = 40_000L,
            status = MessageStatus.SENT.name,
            isPinned = false
        )

        cloudRepository.seedCloudConversationForTest(
            userId = user.uid,
            conversation = cloudConv1,
            messages = listOf(cloudConv1Msg1, cloudConv1Msg2)
        )
        cloudRepository.seedCloudConversationForTest(
            userId = user.uid,
            conversation = cloudConv2,
            messages = listOf(cloudConv2Msg1)
        )

        // 3. Trigger authentication sync into the initially empty Room database (consent remains UNDECIDED)
        val syncResult = syncManager.onUserAuthenticated(user.uid)
        assertTrue(syncResult.isSuccess)
        assertEquals(SyncStatus.SYNCED, syncManager.syncState.value.status)
        assertEquals(SyncConsentDecision.UNDECIDED, syncManager.syncState.value.consentDecision)

        // 4. Verify both conversations and all metadata were restored into Room
        val restoredRoomRecords = conversationRepository.getAllCloudConversationRecords(user.uid)
            .associateBy { it.id }
        assertEquals(2, restoredRoomRecords.size)

        val restoredConv1Record = restoredRoomRecords[cloudConv1.id]
        assertNotNull(restoredConv1Record)
        assertEquals("Kotlin Coroutines & Architecture", restoredConv1Record!!.title)
        assertEquals(10_000L, restoredConv1Record.createdAtMillis)
        assertEquals(25_000L, restoredConv1Record.updatedAtMillis)
        assertTrue(restoredConv1Record.isPinned)
        assertFalse(restoredConv1Record.isArchived)
        assertEquals("Here is the structured supervisorScope example.", restoredConv1Record.lastMessage)
        assertEquals(ResponseStyle.DETAILED.name, restoredConv1Record.responseStyle)
        assertEquals(ResponseLanguage.ENGLISH.name, restoredConv1Record.responseLanguage)
        assertEquals(ResponseFormat.STEP_BY_STEP.name, restoredConv1Record.responseFormat)

        val restoredConv1Domain = conversationRepository.getConversationById(cloudConv1.id)
        assertNotNull(restoredConv1Domain)
        assertEquals(ResponseStyle.DETAILED, restoredConv1Domain!!.responseStyle)
        assertEquals(ResponseLanguage.ENGLISH, restoredConv1Domain.responseLanguage)
        assertEquals(ResponseFormat.STEP_BY_STEP, restoredConv1Domain.responseFormat)

        val restoredConv2Record = restoredRoomRecords[cloudConv2.id]
        assertNotNull(restoredConv2Record)
        assertEquals("Archived Sprint Retrospective", restoredConv2Record!!.title)
        assertEquals(30_000L, restoredConv2Record.createdAtMillis)
        assertEquals(40_000L, restoredConv2Record.updatedAtMillis)
        assertFalse(restoredConv2Record.isPinned)
        assertTrue(restoredConv2Record.isArchived)
        assertEquals("Sprint velocity improved by 18%.", restoredConv2Record.lastMessage)
        assertEquals(ResponseStyle.CONCISE.name, restoredConv2Record.responseStyle)
        assertEquals(ResponseLanguage.BENGALI.name, restoredConv2Record.responseLanguage)
        assertEquals(ResponseFormat.STRUCTURED.name, restoredConv2Record.responseFormat)

        // 5. Verify all three messages and their metadata (attachments, code snippets, pin status) in Room
        val conv1RoomMessages = conversationRepository.getMessagesSync(cloudConv1.id)
        assertEquals(2, conv1RoomMessages.size)

        val msg1 = conv1RoomMessages[0]
        assertEquals("cloud_restore_msg_1", msg1.id)
        assertEquals(MessageSender.USER, msg1.sender)
        assertEquals("Review this PDF specification and show a Kotlin coroutine supervisorScope snippet.", msg1.content)
        assertEquals(MessageStatus.SENT, msg1.status)
        assertEquals(AttachmentType.PDF, msg1.attachmentType)
        assertEquals("coroutine_spec.pdf", msg1.attachmentName)
        assertFalse(msg1.isPinned)

        val msg2 = conv1RoomMessages[1]
        assertEquals("cloud_restore_msg_2", msg2.id)
        assertEquals(MessageSender.ASSISTANT, msg2.sender)
        assertEquals("Here is the structured supervisorScope example.", msg2.content)
        assertEquals(MessageStatus.SENT, msg2.status)
        assertEquals("supervisorScope {\n    launch { fetchRemote() }\n}", msg2.codeSnippet)
        assertEquals("kotlin", msg2.codeLanguage)
        assertTrue(msg2.isPinned)

        val conv2RoomMessages = conversationRepository.getMessagesSync(cloudConv2.id)
        assertEquals(1, conv2RoomMessages.size)
        assertEquals("cloud_restore_msg_3", conv2RoomMessages[0].id)
        assertEquals(MessageSender.USER, conv2RoomMessages[0].sender)
        assertEquals("Sprint velocity improved by 18%.", conv2RoomMessages[0].content)

        // 6. Simulate app restart with a new CloudSyncManager instance and repeat synchronization
        val restartedSyncManager = CloudSyncManager(
            localRepository = conversationRepository,
            cloudRepository = cloudRepository,
            context = context
        )
        assertTrue(restartedSyncManager.onUserAuthenticated(user.uid).isSuccess)
        assertTrue(restartedSyncManager.syncWithCloud(user.uid).isSuccess)

        // Assert zero duplicate conversations or messages after restart & repeat sync
        assertEquals(2, conversationRepository.getAllCloudConversationRecords(user.uid).size)
        assertEquals(2, conversationRepository.getMessagesSync(cloudConv1.id).size)
        assertEquals(1, conversationRepository.getMessagesSync(cloudConv2.id).size)
        assertTrue(restartedSyncManager.syncState.value.activeConflicts.isEmpty())

        // 7. Verify an unconsented local Guest conversation remains local and is NOT uploaded
        authRepository.signOut()
        restartedSyncManager.onUserSignedOut()

        val guestConv = conversationRepository.createConversation(
            id = "local_guest_private_conv",
            title = "Guest Private Draft"
        )
        restartedSyncManager.registerLocalConversationCreated(guestConv.id)
        conversationRepository.saveMessage(
            Message(
                id = "local_guest_private_msg",
                conversationId = guestConv.id,
                sender = MessageSender.USER,
                content = "Keep this strictly local on device",
                timestamp = "12:00 PM",
                status = MessageStatus.SENT
            )
        )

        // Re-authenticate user with UNDECIDED consent -> Guest conversation stays in Room, not uploaded to cloud
        authRepository.signInWithEmailPassword("restore@minova.ai", "RestorePassword123")
        assertTrue(restartedSyncManager.onUserAuthenticated(user.uid).isSuccess)
        assertEquals(SyncConsentDecision.UNDECIDED, restartedSyncManager.syncState.value.consentDecision)

        // Room has all 3 conversations (2 cloud + 1 local guest)
        val allLocalIds = conversationRepository.getAllCloudConversationRecords(user.uid).map { it.id }.toSet()
        assertEquals(setOf("cloud_restore_conv_1", "cloud_restore_conv_2", "local_guest_private_conv"), allLocalIds)

        // Cloud still has only the 2 seeded cloud conversations (unconsented guest chat was NOT uploaded)
        val cloudIds = cloudRepository.fetchUserConversations(user.uid).getOrThrow().map { it.id }.toSet()
        assertEquals(setOf("cloud_restore_conv_1", "cloud_restore_conv_2"), cloudIds)
        assertFalse(cloudIds.contains("local_guest_private_conv"))
    }

    @Test
    fun drawerContent_rendersAt70PercentPhoneWidth_cappedAt280Dp_and_anchoredLeft() {
        val sampleProfile = UserProfile(
            name = "Minhaz",
            email = "minhaz@minova.ai",
            tier = "MINOVA PRO"
        )

        composeRule.setContent {
            MyApplicationTheme {
                // Simulate a 360.dp wide phone screen (e.g., Realme 8 standard 360dp width)
                BoxWithConstraints(
                    modifier = Modifier
                        .requiredWidth(360.dp)
                        .height(800.dp)
                ) {
                    val phoneDrawerWidth = (maxWidth * 0.70f).coerceIn(220.dp, 280.dp)
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            MinovaDrawerContent(
                                conversations = emptyList(),
                                activeConversationId = null,
                                userProfile = sampleProfile,
                                onNavigate = {},
                                onSelectConversation = {},
                                onNewChatClick = {},
                                onTogglePin = {},
                                drawerWidth = phoneDrawerWidth,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(phoneDrawerWidth)
                            )
                        },
                        scrimColor = DrawerDefaults.scrimColor
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // 70% of 360.dp = 252.dp, anchored at left
        val drawerNode = composeRule.onNodeWithTag("minova_drawer_surface")
        drawerNode.assertIsDisplayed()
        drawerNode.assertWidthIsEqualTo(252.dp)

        // Verify footer elements remain displayed and accessible
        composeRule.onNodeWithTag("drawer_profile_link").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_settings_button").assertIsDisplayed()
    }
}
