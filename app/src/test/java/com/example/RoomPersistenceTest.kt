package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MinovaDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.ConversationRepository
import com.example.data.repository.ConversationRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.util.ConversationTitleGenerator
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.example.data.remote.model.FileAnalysisResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: MinovaDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = MinovaDatabase.createInMemory(context)
        conversationDao = database.conversationDao()
        messageDao = database.messageDao()
        repository = ConversationRepositoryImpl(
            conversationDao = conversationDao,
            messageDao = messageDao,
            ioDispatcher = testDispatcher
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `titleGenerator generates clean and concise titles`() {
        // Short prompt
        val title1 = ConversationTitleGenerator.generateTitle("Write a binary search")
        assertEquals("Write a binary search", title1)

        // Prompt from requirements: "Explain polymorphism in C++ with examples"
        val title2 = ConversationTitleGenerator.generateTitle("Explain polymorphism in C++ with examples")
        assertEquals("Explain polymorphism in C++", title2)

        // Extra whitespace
        val title3 = ConversationTitleGenerator.generateTitle("   How   to   architect   clean   code  ")
        assertEquals("How to architect clean code", title3)

        // Empty prompt fallback
        val title4 = ConversationTitleGenerator.generateTitle("   ")
        assertEquals("New Conversation", title4)
    }

    @Test
    fun `conversationRepository creates and persists conversation in Room`() = runTest(testDispatcher) {
        val conv = repository.createConversation(
            id = "conv-test-1",
            title = "Test Architecture",
            isPinned = true
        )

        assertEquals("conv-test-1", conv.id)
        assertEquals("Test Architecture", conv.title)
        assertTrue(conv.isPinned)

        val retrieved = repository.getConversationById("conv-test-1")
        assertNotNull(retrieved)
        assertEquals("Test Architecture", retrieved?.title)
        assertTrue(retrieved?.isPinned == true)

        val activeList = repository.getActiveConversations().first()
        assertEquals(1, activeList.size)
        assertEquals("conv-test-1", activeList[0].id)
    }

    @Test
    fun `messages are persisted and retrieved in chronological order`() = runTest(testDispatcher) {
        repository.createConversation("conv-1", "Chat Session")

        val msg1 = Message(
            id = "m1",
            conversationId = "conv-1",
            sender = MessageSender.USER,
            content = "First question",
            timestamp = "10:00 AM",
            status = MessageStatus.SENT
        )
        val msg2 = Message(
            id = "m2",
            conversationId = "conv-1",
            sender = MessageSender.ASSISTANT,
            content = "First answer",
            timestamp = "10:01 AM",
            status = MessageStatus.SENT
        )

        repository.saveMessage(msg1)
        repository.saveMessage(msg2)

        val messages = repository.getMessagesSync("conv-1")
        assertEquals(2, messages.size)
        assertEquals("First question", messages[0].content)
        assertEquals("First answer", messages[1].content)
    }

    @Test
    fun `deleting a conversation cascade deletes all associated messages`() = runTest(testDispatcher) {
        repository.createConversation("conv-to-delete", "Disposable Chat")

        val msg = Message(
            id = "m-cascade",
            conversationId = "conv-to-delete",
            sender = MessageSender.USER,
            content = "Will be deleted",
            timestamp = "10:00 AM",
            status = MessageStatus.SENT
        )
        repository.saveMessage(msg)

        assertEquals(1, repository.getMessagesSync("conv-to-delete").size)

        repository.deleteConversation("conv-to-delete")

        assertNull(repository.getConversationById("conv-to-delete"))
        assertEquals(0, repository.getMessagesSync("conv-to-delete").size)
    }

    @Test
    fun `pin and archive toggles update persistent database state`() = runTest(testDispatcher) {
        repository.createConversation("conv-toggle", "Toggle Title", isPinned = false, isArchived = false)

        repository.togglePin("conv-toggle")
        val pinned = repository.getConversationById("conv-toggle")
        assertTrue(pinned?.isPinned == true)

        repository.toggleArchive("conv-toggle")
        val archived = repository.getConversationById("conv-toggle")
        assertTrue(archived?.isArchived == true)

        val activeList = repository.getActiveConversations().first()
        assertTrue(activeList.none { it.id == "conv-toggle" })

        val archivedList = repository.getArchivedConversations().first()
        assertTrue(archivedList.any { it.id == "conv-toggle" })
    }

    @Test
    fun `search finds conversations matching query in title or messages`() = runTest(testDispatcher) {
        repository.createConversation("c-kotlin", "Kotlin Coroutines Masterclass")
        repository.createConversation("c-rust", "Rust Memory Safety")

        val results = repository.searchConversations("Kotlin").first()
        assertEquals(1, results.size)
        assertEquals("c-kotlin", results[0].id)
    }

    @Test
    fun `viewModel automatically creates conversation and persists multi-turn session`() = runTest(testDispatcher) {
        val inMemoryRepo = InMemoryConversationRepository()
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "Echo response"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                val body = "data: {\"text\":\"Echo response\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }

            override suspend fun analyzeFile(
                file: MultipartBody.Part,
                message: RequestBody,
                conversation: RequestBody?
            ): Response<FileAnalysisResponse> {
                return Response.success(FileAnalysisResponse(success = true, message = "Echo response"))
            }
        }

        val chatRepo = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val viewModel = MinovaViewModel(
            chatRepository = chatRepo,
            conversationRepository = inMemoryRepo
        )

        // Send first message
        viewModel.onComposerTextChange("Explain polymorphism in C++ with examples")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertTrue(viewModel.chatUiState.value is ChatUiState.Success)
        val convId = viewModel.activeConversationId.value
        assertNotNull(convId)

        // Verify conversation was created with auto title
        val savedConv = inMemoryRepo.getConversationById(convId!!)
        assertNotNull(savedConv)
        assertEquals("Explain polymorphism in C++", savedConv?.title)

        // Verify messages persisted
        val savedMessages = inMemoryRepo.getMessagesSync(convId)
        assertEquals(2, savedMessages.size)
        assertEquals(MessageSender.USER, savedMessages[0].sender)
        assertEquals("Explain polymorphism in C++ with examples", savedMessages[0].content)
        assertEquals(MessageSender.ASSISTANT, savedMessages[1].sender)
        assertEquals("Echo response", savedMessages[1].content)
    }
}
