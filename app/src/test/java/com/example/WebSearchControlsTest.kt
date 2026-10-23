package com.example

import androidx.datastore.preferences.core.emptyPreferences
import com.example.data.model.MessageStatus
import com.example.data.model.SettingsState
import com.example.data.remote.RetrofitClient
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.remote.stream.StreamEvent
import com.example.data.remote.stream.StreamResponseParser
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.repository.NetworkResult
import com.example.data.repository.SettingsRepositoryImpl
import com.example.data.repository.StreamResult
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WebSearchControlsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `1 chatRequest serializes optional webSearch field correctly`() {
        val adapter = RetrofitClient.moshi.adapter(ChatRequest::class.java)

        // Default omitted when null
        val defaultReq = ChatRequest(message = "Hello MINOVA")
        val defaultJson = adapter.toJson(defaultReq)
        assertFalse("Expected webSearch to be omitted when null, got: $defaultJson", defaultJson.contains("webSearch"))

        // Included when true
        val enabledReq = ChatRequest(message = "Latest news", webSearch = true)
        val enabledJson = adapter.toJson(enabledReq)
        assertTrue("Expected webSearch:true in JSON, got: $enabledJson", enabledJson.contains("\"webSearch\":true"))

        val parsed = adapter.fromJson(enabledJson)
        assertNotNull(parsed)
        assertEquals(true, parsed?.webSearch)
    }

    @Test
    fun `2 default webSearch preference is false and persists`() = runTest {
        val defaultState = SettingsState()
        assertFalse(defaultState.webSearchDefault)

        val mappedDefault = SettingsRepositoryImpl.mapPreferencesToSettings(emptyPreferences())
        assertFalse(mappedDefault.webSearchDefault)

        val repo = InMemorySettingsRepository()
        assertFalse(repo.getSettings().webSearchDefault)

        repo.updateWebSearchDefault(true)
        assertTrue(repo.getSettings().webSearchDefault)

        repo.resetToDefaults()
        assertFalse(repo.getSettings().webSearchDefault)
    }

    @Test
    fun `3 composer and session toggle state works and preserves explicit user choice`() = runTest {
        val settingsRepo = InMemorySettingsRepository(SettingsState(webSearchDefault = false))
        val convRepo = InMemoryConversationRepository()
        val chatRepo = ChatRepositoryImpl(FakeMinovaApiService(), testDispatcher)
        val viewModel = MinovaViewModel(
            conversationRepository = convRepo,
            settingsRepository = settingsRepo,
            chatRepository = chatRepo
        )
        advanceUntilIdle()

        // Initialized from webSearchDefault (false)
        assertFalse(viewModel.isWebSearchActive.value)

        // User toggles Web Search ON in composer for current session
        viewModel.toggleWebSearch()
        assertTrue(viewModel.isWebSearchActive.value)

        // Unrelated setting change does not overwrite explicit in-session choice
        viewModel.updateSettings(viewModel.settings.value.copy(showTimestamps = true))
        advanceUntilIdle()
        assertTrue(viewModel.isWebSearchActive.value)

        // Starting a new chat resets session toggle to global default (false)
        viewModel.startNewChat()
        assertFalse(viewModel.isWebSearchActive.value)
    }

    @Test
    fun `4 WEB_SEARCH_UNAVAILABLE is recognized in non-streaming HTTP 503 responses`() = runTest {
        val errorJson = """{"success":false,"error":{"code":"WEB_SEARCH_UNAVAILABLE","message":"Web Search is temporarily unavailable. Standard chat is still available."}}"""
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.error(503, errorJson.toResponseBody("application/json".toMediaType()))
            }
        }

        val chatRepo = ChatRepositoryImpl(fakeApi, testDispatcher)
        val result = chatRepo.sendChatMessage(
            message = "Search something",
            conversationHistory = emptyList(),
            webSearch = true
        )

        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals("WEB_SEARCH_UNAVAILABLE", error.errorCode)
        assertEquals(503, error.statusCode)
        assertTrue(error.userMessage.contains("Web Search is temporarily unavailable"))
    }

    @Test
    fun `5 WEB_SEARCH_UNAVAILABLE is recognized in SSE error events`() = runTest {
        val parser = StreamResponseParser()
        val line1 = parser.parseLine("event: error")
        assertNull(line1)

        val line2 = parser.parseLine("""data: {"success":false,"error":{"code":"WEB_SEARCH_UNAVAILABLE","message":"Web Search is temporarily unavailable. Standard chat is still available."}}""")
        assertTrue(line2 is StreamEvent.Error)
        val streamErr = line2 as StreamEvent.Error
        assertEquals("WEB_SEARCH_UNAVAILABLE", streamErr.code)
        assertEquals("Web Search is temporarily unavailable. Standard chat is still available.", streamErr.message)

        val ssePayload = "event: error\ndata: {\"success\":false,\"error\":{\"code\":\"WEB_SEARCH_UNAVAILABLE\",\"message\":\"Web Search is temporarily unavailable. Standard chat is still available.\"}}\n\n"
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                return Response.success(ssePayload.toResponseBody("text/event-stream".toMediaType()))
            }
        }

        val chatRepo = ChatRepositoryImpl(fakeApi, testDispatcher)
        val emissions = chatRepo.sendChatMessageStream(
            message = "Search live web",
            conversationHistory = emptyList(),
            webSearch = true
        ).toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is StreamResult.Error)
        val emittedErr = emissions[0] as StreamResult.Error
        assertEquals("WEB_SEARCH_UNAVAILABLE", emittedErr.errorCode)
        assertEquals("Web Search is temporarily unavailable. Standard chat is still available.", emittedErr.userMessage)
    }

    @Test
    fun `6 continueWithoutWebSearch disables Web Search for current session and retries cleanly`() = runTest {
        val capturedRequests = mutableListOf<ChatRequest>()
        val sseError = "event: error\ndata: {\"success\":false,\"error\":{\"code\":\"WEB_SEARCH_UNAVAILABLE\",\"message\":\"Web Search is temporarily unavailable. Standard chat is still available.\"}}\n\n"
        val sseSuccess = "data: {\"text\":\"Standard chat response\"}\n\ndata: [DONE]\n\n"

        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedRequests.add(request)
                return if (request.webSearch == true) {
                    Response.success(sseError.toResponseBody("text/event-stream".toMediaType()))
                } else {
                    Response.success(sseSuccess.toResponseBody("text/event-stream".toMediaType()))
                }
            }
        }

        val settingsRepo = InMemorySettingsRepository()
        val convRepo = InMemoryConversationRepository()
        val chatRepo = ChatRepositoryImpl(fakeApi, testDispatcher)
        val viewModel = MinovaViewModel(
            conversationRepository = convRepo,
            settingsRepository = settingsRepo,
            chatRepository = chatRepo
        )
        advanceUntilIdle()

        // Enable Web Search for session and send message
        viewModel.toggleWebSearch(true)
        assertTrue(viewModel.isWebSearchActive.value)

        viewModel.onComposerTextChange("What is Kotlin?")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Verify first request had webSearch = true and resulted in error state
        assertEquals(1, capturedRequests.size)
        assertEquals(true, capturedRequests[0].webSearch)
        assertTrue(viewModel.chatUiState.value is ChatUiState.Error)
        val lastMsg = viewModel.activeMessages.value.last()
        assertEquals(MessageStatus.ERROR, lastMsg.status)
        assertTrue(lastMsg.errorMessage?.contains("WEB_SEARCH_UNAVAILABLE") == true)

        // Trigger continueWithoutWebSearch
        viewModel.continueWithoutWebSearch()
        advanceUntilIdle()

        // Web Search is now disabled for current session and retry succeeded with standard chat
        assertFalse(viewModel.isWebSearchActive.value)
        assertEquals(2, capturedRequests.size)
        assertNull(capturedRequests[1].webSearch)
        val updatedLastMsg = viewModel.activeMessages.value.last()
        assertEquals(MessageStatus.SENT, updatedLastMsg.status)
        assertEquals("Standard chat response", updatedLastMsg.content)
    }

    @Test
    fun `7 existing normal chat and streaming behavior remains compatible when webSearch disabled`() = runTest {
        var lastRequest: ChatRequest? = null
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                lastRequest = request
                return Response.success("data: {\"text\":\"Compatible response\"}\n\ndata: [DONE]\n\n".toResponseBody("text/event-stream".toMediaType()))
            }
        }

        val settingsRepo = InMemorySettingsRepository()
        val convRepo = InMemoryConversationRepository()
        val chatRepo = ChatRepositoryImpl(fakeApi, testDispatcher)
        val viewModel = MinovaViewModel(
            conversationRepository = convRepo,
            settingsRepository = settingsRepo,
            chatRepository = chatRepo
        )
        advanceUntilIdle()

        viewModel.onComposerTextChange("Standard question")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertNotNull(lastRequest)
        assertNull(lastRequest?.webSearch)
        assertEquals("Compatible response", viewModel.activeMessages.value.last().content)
    }

    @Test
    fun `8 continueWithoutWebSearch handles async DB deletion ordering and regenerateMessage fallback without duplicate messages`() = runTest {
        val capturedRequests = mutableListOf<ChatRequest>()
        val sseError = "event: error\ndata: {\"success\":false,\"error\":{\"code\":\"WEB_SEARCH_UNAVAILABLE\",\"message\":\"Web Search is temporarily unavailable. Standard chat is still available.\"}}\n\n"
        val sseSuccess = "data: {\"text\":\"Fallback answer\"}\n\ndata: [DONE]\n\n"

        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedRequests.add(request)
                return if (request.webSearch == true) {
                    Response.success(sseError.toResponseBody("text/event-stream".toMediaType()))
                } else {
                    Response.success(sseSuccess.toResponseBody("text/event-stream".toMediaType()))
                }
            }
        }

        // Simulate Room's multi-hop suspension on deleteMessage so any concurrent getMessagesSync race would be exposed
        val baseRepo = InMemoryConversationRepository()
        val convRepo = object : com.example.data.repository.ConversationRepository by baseRepo {
            override suspend fun deleteMessage(id: String) {
                kotlinx.coroutines.yield()
                kotlinx.coroutines.yield()
                baseRepo.deleteMessage(id)
            }
        }
        val settingsRepo = InMemorySettingsRepository()
        val chatRepo = ChatRepositoryImpl(fakeApi, testDispatcher)
        val viewModel = MinovaViewModel(
            conversationRepository = convRepo,
            settingsRepository = settingsRepo,
            chatRepository = chatRepo
        )
        advanceUntilIdle()

        // 1. First turn with Web Search enabled -> fails with WEB_SEARCH_UNAVAILABLE
        viewModel.toggleWebSearch(true)
        viewModel.onComposerTextChange("Explain quantum entanglement")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(2, viewModel.activeMessages.value.size)
        assertEquals(MessageStatus.ERROR, viewModel.activeMessages.value.last().status)

        // 2. Tap Continue without Web Search -> deletes failed turn BEFORE getMessagesSync and retries cleanly
        viewModel.continueWithoutWebSearch()
        advanceUntilIdle()

        assertFalse(viewModel.isWebSearchActive.value)
        assertEquals(2, capturedRequests.size)
        assertNull(capturedRequests[1].webSearch)
        assertTrue(capturedRequests[1].conversation.isEmpty())
        assertEquals(2, viewModel.activeMessages.value.size)
        assertEquals("Explain quantum entanglement", viewModel.activeMessages.value[0].content)
        assertEquals("Fallback answer", viewModel.activeMessages.value[1].content)
        assertEquals(MessageStatus.SENT, viewModel.activeMessages.value[1].status)

        // 3. Re-enable Web Search and trigger regenerateMessage -> fails with WEB_SEARCH_UNAVAILABLE, then fallback succeeds
        viewModel.toggleWebSearch(true)
        viewModel.regenerateMessage(viewModel.activeMessages.value.last().id)
        advanceUntilIdle()

        assertEquals(MessageStatus.ERROR, viewModel.activeMessages.value.last().status)

        viewModel.continueWithoutWebSearch()
        advanceUntilIdle()

        assertFalse(viewModel.isWebSearchActive.value)
        assertEquals(4, capturedRequests.size)
        assertNull(capturedRequests[3].webSearch)
        assertEquals(2, viewModel.activeMessages.value.size)
        assertEquals("Fallback answer", viewModel.activeMessages.value.last().content)
        assertEquals(MessageStatus.SENT, viewModel.activeMessages.value.last().status)
    }
}
