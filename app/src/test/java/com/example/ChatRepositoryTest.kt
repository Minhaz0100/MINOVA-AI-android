package com.example

import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.remote.NetworkConfig
import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.model.ApiError
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.remote.stream.StreamEvent
import com.example.data.remote.stream.StreamResponseParser
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.NetworkResult
import com.example.data.repository.StreamResult
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.example.data.remote.model.CloudflareImageGenerateRequest
import com.example.data.remote.model.FileAnalysisResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
import retrofit2.Response

open class FakeMinovaApiService : MinovaApiService {
    override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
        return Response.success(ChatResponse(success = true, message = "Default message"))
    }

    override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
        return Response.success("data: {\"text\":\"Default\"}\n\n".toResponseBody("text/event-stream".toMediaType()))
    }

    override suspend fun analyzeFile(
        file: MultipartBody.Part,
        message: RequestBody,
        conversation: RequestBody?
    ): Response<FileAnalysisResponse> {
        return Response.success(FileAnalysisResponse(success = true, message = "Default file analysis result"))
    }

    override suspend fun generateCloudflareImage(
        request: CloudflareImageGenerateRequest
    ): Response<ResponseBody> {
        return Response.success(ByteArray(4).toResponseBody("image/jpeg".toMediaType()))
    }

    override suspend fun checkImageHealth(): Response<ResponseBody> {
        return Response.success("{\"healthy\":true}".toResponseBody("application/json".toMediaType()))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {

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
    fun `streamResponseParser parses various SSE formats correctly`() {
        val parser = StreamResponseParser()

        // 1. data with json message
        val event1 = parser.parseLine("data: {\"message\":\"Hello \"}")
        assertTrue(event1 is StreamEvent.Chunk)
        assertEquals("Hello ", (event1 as StreamEvent.Chunk).text)

        // 2. data with json text
        val event2 = parser.parseLine("data: {\"text\":\"MINOVA\"}")
        assertTrue(event2 is StreamEvent.Chunk)
        assertEquals("MINOVA", (event2 as StreamEvent.Chunk).text)

        // 3. data with Gemini candidate format
        val event3 = parser.parseLine("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" AI\"}]}}]}")
        assertTrue(event3 is StreamEvent.Chunk)
        assertEquals(" AI", (event3 as StreamEvent.Chunk).text)

        // 4. data: [DONE]
        val eventDone = parser.parseLine("data: [DONE]")
        assertTrue(eventDone is StreamEvent.Done)

        // 5. Heartbeat / comment
        val eventComment = parser.parseLine(": ping")
        assertNull(eventComment)

        // 6. data error
        val eventError = parser.parseLine("data: {\"error\":{\"message\":\"Rate limited\",\"code\":\"429\"}}")
        assertTrue(eventError is StreamEvent.Error)
        assertEquals("Rate limited", (eventError as StreamEvent.Error).message)

        // 7. Plain text fallback
        val eventPlain = parser.parseLine("data: plain chunk")
        assertTrue(eventPlain is StreamEvent.Chunk)
        assertEquals("plain chunk", (eventPlain as StreamEvent.Chunk).text)

        // 8. Backend SSE completion event: event: done followed by data: {"success":true}
        val eventDoneHeader = parser.parseLine("event: done")
        assertNull(eventDoneHeader)
        val eventDonePayload = parser.parseLine("data: {\"success\":true}")
        assertTrue(eventDonePayload is StreamEvent.Done)

        // 9. Standalone {"success":true} without preceding event: done
        val parser2 = StreamResponseParser()
        val eventStandaloneSuccess = parser2.parseLine("data: {\"success\":true}")
        assertTrue(eventStandaloneSuccess is StreamEvent.Done)

        // 10. Metadata JSON without textual fields must return null, never leak as chunk
        val parser3 = StreamResponseParser()
        val eventMetadata = parser3.parseLine("data: {\"usageMetadata\":{\"tokens\":42}}")
        assertNull(eventMetadata)
    }

    @Test
    fun `sendChatMessageStream ignores event done and success true completion metadata`() = runTest(testDispatcher) {
        val sseStreamContent = """
            data: {"text":"This is the AI response."}
            event: done
            data: {"success":true}
        """.trimIndent()

        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "This is the AI response."))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                val body = sseStreamContent.toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val results = repository.sendChatMessageStream("Hi", emptyList()).toList()

        assertEquals(2, results.size)
        assertTrue(results[0] is StreamResult.Chunk)
        assertEquals("This is the AI response.", (results[0] as StreamResult.Chunk).text)

        assertTrue(results[1] is StreamResult.Complete)
        val fullText = (results[1] as StreamResult.Complete).fullText
        assertEquals("This is the AI response.", fullText)
        assertTrue(!fullText.contains("success"))
        assertTrue(!fullText.contains("{"))
    }

    @Test
    fun `sendChatMessageStream collects incremental chunks and completes`() = runTest(testDispatcher) {
        val sseStreamContent = """
            data: {"text":"Hello "}

            data: {"text":"World"}

            data: [DONE]
        """.trimIndent()

        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "Hello World"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                val body = sseStreamContent.toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val results = repository.sendChatMessageStream("Hi", emptyList()).toList()

        assertEquals(3, results.size)
        assertTrue(results[0] is StreamResult.Chunk)
        assertEquals("Hello ", (results[0] as StreamResult.Chunk).text)

        assertTrue(results[1] is StreamResult.Chunk)
        assertEquals("World", (results[1] as StreamResult.Chunk).text)

        assertTrue(results[2] is StreamResult.Complete)
        assertEquals("Hello World", (results[2] as StreamResult.Complete).fullText)
    }

    @Test
    fun `viewModel streams chunks into assistant message and finishes as sent`() = runTest(testDispatcher) {
        val sseStreamContent = """
            data: {"message":"I am MINOVA"}
            data: {"message":", ready to help!"}
            data: [DONE]
        """.trimIndent()

        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "I am MINOVA, ready to help!"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                val body = sseStreamContent.toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val viewModel = MinovaViewModel(chatRepository = repository)
        viewModel.onComposerTextChange("Who are you?")
        viewModel.sendMessage()

        advanceUntilIdle()

        assertTrue(viewModel.chatUiState.value is ChatUiState.Success)
        val messages = viewModel.activeMessages.value
        assertEquals(2, messages.size)
        assertEquals(MessageSender.USER, messages[0].sender)
        assertEquals("Who are you?", messages[0].content)

        assertEquals(MessageSender.ASSISTANT, messages[1].sender)
        assertEquals("I am MINOVA, ready to help!", messages[1].content)
        assertEquals(MessageStatus.SENT, messages[1].status)
    }

    @Test
    fun `stopStreaming cancels active stream and preserves received tokens`() = runTest(testDispatcher) {
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "Long response"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                val body = "data: {\"text\":\"Part one\"}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val viewModel = MinovaViewModel(chatRepository = repository)
        viewModel.onComposerTextChange("Tell me a story")
        viewModel.sendMessage()

        advanceUntilIdle()

        // Call stopStreaming
        viewModel.stopStreaming()

        assertEquals(ChatUiState.Idle, viewModel.chatUiState.value)
        val assistantMsg = viewModel.activeMessages.value.find { it.sender == MessageSender.ASSISTANT }
        assertNotNull(assistantMsg)
        assertEquals(MessageStatus.SENT, assistantMsg?.status)
    }

    @Test
    fun `first message sends empty conversation in request`() = runTest(testDispatcher) {
        var capturedRequest: ChatRequest? = null
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                capturedRequest = request
                return Response.success(ChatResponse(success = true, message = "First response"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedRequest = request
                val body = "data: {\"text\":\"First response\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val results = repository.sendChatMessageStream("Hello AI", emptyList()).toList()
        assertTrue(results.isNotEmpty())
        assertNotNull(capturedRequest)
        assertEquals("Hello AI", capturedRequest?.message)
        assertTrue(capturedRequest?.conversation?.isEmpty() == true)
    }

    @Test
    fun `second message includes previous user message and assistant response with correct roles`() = runTest(testDispatcher) {
        var capturedStreamRequest: ChatRequest? = null
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                return Response.success(ChatResponse(success = true, message = "Response 2"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedStreamRequest = request
                val body = "data: {\"text\":\"I remember!\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val history = listOf(
            Message(
                id = "msg-1",
                conversationId = "conv-1",
                sender = MessageSender.USER,
                content = "My name is Alice",
                timestamp = "10:00 AM",
                status = MessageStatus.SENT
            ),
            Message(
                id = "msg-2",
                conversationId = "conv-1",
                sender = MessageSender.ASSISTANT,
                content = "Nice to meet you, Alice!",
                timestamp = "10:01 AM",
                status = MessageStatus.SENT
            )
        )

        val results = repository.sendChatMessageStream("What is my name?", history).toList()
        assertTrue(results.isNotEmpty())

        assertNotNull(capturedStreamRequest)
        assertEquals("What is my name?", capturedStreamRequest?.message)

        val conversation = capturedStreamRequest?.conversation
        assertNotNull(conversation)
        assertEquals(2, conversation?.size)

        assertEquals("user", conversation?.get(0)?.role)
        assertEquals("My name is Alice", conversation?.get(0)?.content)

        assertEquals("assistant", conversation?.get(1)?.role)
        assertEquals("Nice to meet you, Alice!", conversation?.get(1)?.content)
    }

    @Test
    fun `current user message is not duplicated in conversation context`() = runTest(testDispatcher) {
        var capturedRequest: ChatRequest? = null
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                capturedRequest = request
                return Response.success(ChatResponse(success = true, message = "Acknowledged"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedRequest = request
                val body = "data: {\"text\":\"Acknowledged\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        // Simulating history that accidentally has the current message already at the end
        val historyWithDuplicate = listOf(
            Message(
                id = "msg-1",
                conversationId = "c1",
                sender = MessageSender.USER,
                content = "First turn",
                timestamp = "10:00 AM",
                status = MessageStatus.SENT
            ),
            Message(
                id = "msg-2",
                conversationId = "c1",
                sender = MessageSender.ASSISTANT,
                content = "First reply",
                timestamp = "10:01 AM",
                status = MessageStatus.SENT
            ),
            Message(
                id = "msg-3",
                conversationId = "c1",
                sender = MessageSender.USER,
                content = "Second turn query",
                timestamp = "10:02 AM",
                status = MessageStatus.SENT
            )
        )

        repository.sendChatMessage("Second turn query", historyWithDuplicate)

        assertNotNull(capturedRequest)
        assertEquals("Second turn query", capturedRequest?.message)

        val conversation = capturedRequest?.conversation
        assertNotNull(conversation)
        assertEquals(2, conversation?.size)
        assertEquals("user", conversation?.get(0)?.role)
        assertEquals("First turn", conversation?.get(0)?.content)
        assertEquals("assistant", conversation?.get(1)?.role)
        assertEquals("First reply", conversation?.get(1)?.content)
        // Ensure "Second turn query" was not duplicated in conversation
        assertTrue(conversation?.none { it.content == "Second turn query" } == true)
    }

    @Test
    fun `error and incomplete messages are filtered out of conversation context`() = runTest(testDispatcher) {
        var capturedRequest: ChatRequest? = null
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                capturedRequest = request
                return Response.success(ChatResponse(success = true, message = "OK"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                capturedRequest = request
                val body = "data: {\"text\":\"OK\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val historyWithErrors = listOf(
            Message(
                id = "msg-1",
                conversationId = "c1",
                sender = MessageSender.USER,
                content = "Valid query",
                timestamp = "10:00 AM",
                status = MessageStatus.SENT
            ),
            Message(
                id = "msg-2",
                conversationId = "c1",
                sender = MessageSender.ASSISTANT,
                content = "Something went wrong",
                timestamp = "10:01 AM",
                status = MessageStatus.ERROR
            ),
            Message(
                id = "msg-3",
                conversationId = "c1",
                sender = MessageSender.ASSISTANT,
                content = "",
                timestamp = "10:02 AM",
                status = MessageStatus.STREAMING
            )
        )

        repository.sendChatMessageStream("New query", historyWithErrors).toList()

        assertNotNull(capturedRequest)
        assertEquals("New query", capturedRequest?.message)

        val conversation = capturedRequest?.conversation
        assertNotNull(conversation)
        assertEquals(1, conversation?.size)
        assertEquals("user", conversation?.get(0)?.role)
        assertEquals("Valid query", conversation?.get(0)?.content)
    }

    @Test
    fun `viewModel automatically preserves multi-turn conversation context`() = runTest(testDispatcher) {
        val requestsList = mutableListOf<ChatRequest>()
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
                requestsList.add(request)
                return Response.success(ChatResponse(success = true, message = "Reply to ${request.message}"))
            }

            override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
                requestsList.add(request)
                val body = "data: {\"text\":\"Reply to ${request.message}\"}\n\nevent: done\ndata: {\"success\":true}\n".toResponseBody("text/event-stream".toMediaType())
                return Response.success(body)
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val viewModel = MinovaViewModel(chatRepository = repository)

        // Turn 1
        viewModel.onComposerTextChange("Turn 1 Question")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, requestsList.size)
        assertEquals("Turn 1 Question", requestsList[0].message)
        assertTrue(requestsList[0].conversation.isEmpty())

        // Turn 2
        viewModel.onComposerTextChange("Turn 2 Question")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(2, requestsList.size)
        assertEquals("Turn 2 Question", requestsList[1].message)
        assertEquals(2, requestsList[1].conversation.size)
        assertEquals("user", requestsList[1].conversation[0].role)
        assertEquals("Turn 1 Question", requestsList[1].conversation[0].content)
        assertEquals("assistant", requestsList[1].conversation[1].role)
        assertEquals("Reply to Turn 1 Question", requestsList[1].conversation[1].content)
    }

    @Test
    fun `analyzeFileMessage calls apiService and returns success response`() = runTest(testDispatcher) {
        val fakeApi = object : FakeMinovaApiService() {
            override suspend fun analyzeFile(
                file: MultipartBody.Part,
                message: RequestBody,
                conversation: RequestBody?
            ): Response<FileAnalysisResponse> {
                return Response.success(
                    FileAnalysisResponse(
                        success = true,
                        message = "Analyzed document contents successfully."
                    )
                )
            }
        }

        val repository = ChatRepositoryImpl(
            apiService = fakeApi,
            ioDispatcher = testDispatcher
        )

        val attachment = Attachment(
            uri = "content://com.example.provider/docs/spec.pdf",
            fileName = "spec.pdf",
            mimeType = "application/pdf",
            sizeBytes = 2048L,
            type = AttachmentType.PDF
        )

        val filePart = MultipartBody.Part.createFormData(
            "file",
            attachment.fileName,
            "sample content".toRequestBody("application/pdf".toMediaType())
        )

        val result = repository.analyzeFileMessage(
            filePart = filePart,
            message = "Summarize this PDF",
            conversationHistory = emptyList()
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("Analyzed document contents successfully.", (result as NetworkResult.Success).data)
    }

    @Test
    fun `viewModel handles attachment selection and clears correctly`() = runTest(testDispatcher) {
        val repository = ChatRepositoryImpl(
            apiService = FakeMinovaApiService(),
            ioDispatcher = testDispatcher
        )

        val viewModel = MinovaViewModel(chatRepository = repository)

        val attachment = Attachment(
            uri = "content://com.example.provider/images/photo.png",
            fileName = "photo.png",
            mimeType = "image/png",
            sizeBytes = 5120L,
            type = AttachmentType.PHOTO
        )

        viewModel.selectAttachment(attachment)
        assertEquals(attachment, viewModel.selectedAttachment.value)

        viewModel.clearSelectedAttachment()
        assertNull(viewModel.selectedAttachment.value)
    }
}
