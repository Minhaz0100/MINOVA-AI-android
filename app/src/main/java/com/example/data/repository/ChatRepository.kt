package com.example.data.repository

import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.remote.NetworkConfig
import com.example.data.remote.RetrofitClient
import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.model.ApiChatMessage
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.stream.StreamEvent
import com.example.data.remote.stream.StreamResponseParser
import com.example.data.util.ResponsePreferencesHelper
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface ChatRepository {
    suspend fun sendChatMessage(
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle = ResponseStyle.BALANCED,
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        responseFormat: ResponseFormat = ResponseFormat.AUTO,
        webSearch: Boolean = false
    ): NetworkResult<String>

    fun sendChatMessageStream(
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle = ResponseStyle.BALANCED,
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        responseFormat: ResponseFormat = ResponseFormat.AUTO,
        webSearch: Boolean = false
    ): Flow<StreamResult>

    suspend fun analyzeFileMessage(
        filePart: MultipartBody.Part,
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle = ResponseStyle.BALANCED,
        responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
        responseFormat: ResponseFormat = ResponseFormat.AUTO
    ): NetworkResult<String>
}

class ChatRepositoryImpl(
    private val apiService: MinovaApiService = RetrofitClient.getApiService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatRepository {

    override fun sendChatMessageStream(
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle,
        responseLanguage: ResponseLanguage,
        responseFormat: ResponseFormat,
        webSearch: Boolean
    ): Flow<StreamResult> = flow {
        // 1. Verify backend base URL configuration
        if (!NetworkConfig.isBackendConfigured) {
            emit(
                StreamResult.Error(
                    userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                    errorCode = "ERR_BACKEND_UNCONFIGURED"
                )
            )
            return@flow
        }

        // 2. Map conversation history to API DTOs (filtering errors, incomplete streams, and duplicate current message)
        val apiHistory = buildApiConversationHistory(
            currentMessage = message,
            conversationHistory = conversationHistory
        )

        val directive = ResponsePreferencesHelper.buildSystemDirective(responseStyle, responseLanguage, responseFormat)
        val effectiveMessage = if (directive != null) "$directive\n\n$message" else message

        val request = ChatRequest(
            message = effectiveMessage,
            conversation = apiHistory,
            webSearch = if (webSearch) true else null
        )

        // 3. Execute streaming request
        try {
            val response = apiService.sendChatStream(request)

            if (!response.isSuccessful) {
                val statusCode = response.code()
                val errorBodyStr = try { response.errorBody()?.string() } catch (_: Exception) { null }
                val isWebSearchUnavailable = errorBodyStr?.contains("WEB_SEARCH_UNAVAILABLE") == true
                val friendlyMessage = if (isWebSearchUnavailable) {
                    "Web Search is temporarily unavailable. Standard chat is still available."
                } else when (statusCode) {
                    400 -> "Bad request. Please check your prompt input."
                    401 -> "Unauthorized request. Please check backend credentials."
                    403 -> "Access forbidden by server."
                    404 -> "Streaming endpoint not found on server (404)."
                    429 -> "Rate limit reached. Please wait a moment before sending another message."
                    500 -> "Internal server error. MINOVA backend encountered an issue."
                    502 -> "Bad gateway. Unable to reach MINOVA AI server."
                    503 -> "Service unavailable. Backend server is temporarily overloaded or down."
                    504 -> "Gateway timeout. Backend took too long to process."
                    else -> "Server error ($statusCode). Please try again later."
                }
                emit(
                    StreamResult.Error(
                        userMessage = friendlyMessage,
                        statusCode = statusCode,
                        errorCode = if (isWebSearchUnavailable) "WEB_SEARCH_UNAVAILABLE" else "HTTP_$statusCode"
                    )
                )
                return@flow
            }

            val body = response.body()
            if (body == null) {
                emit(
                    StreamResult.Error(
                        userMessage = "Malformed response from server (empty body).",
                        statusCode = response.code(),
                        errorCode = "ERR_EMPTY_BODY"
                    )
                )
                return@flow
            }

            val parser = StreamResponseParser()
            val fullTextBuilder = StringBuilder()
            var hasReceivedAnyChunk = false

            body.use { responseBody ->
                val source = responseBody.source()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    val event = parser.parseLine(line) ?: continue

                    when (event) {
                        is StreamEvent.Chunk -> {
                            if (event.text.isNotEmpty()) {
                                hasReceivedAnyChunk = true
                                fullTextBuilder.append(event.text)
                                emit(StreamResult.Chunk(event.text))
                            }
                        }
                        is StreamEvent.Error -> {
                            emit(
                                StreamResult.Error(
                                    userMessage = event.message,
                                    errorCode = event.code ?: "ERR_STREAM_BACKEND"
                                )
                            )
                            return@flow
                        }
                        is StreamEvent.Done -> {
                            break
                        }
                    }
                }
            }

            val finalOutput = fullTextBuilder.toString()
            if (!hasReceivedAnyChunk && finalOutput.isBlank()) {
                emit(
                    StreamResult.Error(
                        userMessage = "Empty response received from MINOVA AI backend.",
                        errorCode = "ERR_EMPTY_STREAM"
                    )
                )
            } else {
                emit(StreamResult.Complete(finalOutput))
            }
        } catch (e: CancellationException) {
            // Cancelled cleanly (e.g. user pressed Stop)
            throw e
        } catch (e: UnknownHostException) {
            emit(
                StreamResult.Error(
                    userMessage = "No internet connection or backend host cannot be resolved. Please check your network.",
                    errorCode = "ERR_NO_INTERNET"
                )
            )
        } catch (e: ConnectException) {
            emit(
                StreamResult.Error(
                    userMessage = "Unable to reach MINOVA backend server. Please verify the backend is running.",
                    errorCode = "ERR_CONNECTION_REFUSED"
                )
            )
        } catch (e: NoRouteToHostException) {
            emit(
                StreamResult.Error(
                    userMessage = "No route to host. Please verify your internet connection.",
                    errorCode = "ERR_NO_ROUTE"
                )
            )
        } catch (e: SocketTimeoutException) {
            emit(
                StreamResult.Error(
                    userMessage = "Connection timed out while waiting for AI response stream.",
                    errorCode = "ERR_TIMEOUT"
                )
            )
        } catch (e: IOException) {
            emit(
                StreamResult.Error(
                    userMessage = "Network communication error during response stream. Please try again.",
                    errorCode = "ERR_IO"
                )
            )
        } catch (e: Exception) {
            emit(
                StreamResult.Error(
                    userMessage = "An unexpected error occurred during stream: ${e.message ?: "unknown"}",
                    errorCode = "ERR_UNEXPECTED"
                )
            )
        }
    }.flowOn(ioDispatcher)

    override suspend fun sendChatMessage(
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle,
        responseLanguage: ResponseLanguage,
        responseFormat: ResponseFormat,
        webSearch: Boolean
    ): NetworkResult<String> = withContext(ioDispatcher) {
        // 1. Verify backend base URL configuration before attempting network request
        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }

        // 2. Map conversation history to API DTOs (filtering errors, incomplete streams, and duplicate current message)
        val apiHistory = buildApiConversationHistory(
            currentMessage = message,
            conversationHistory = conversationHistory
        )

        val directive = ResponsePreferencesHelper.buildSystemDirective(responseStyle, responseLanguage, responseFormat)
        val effectiveMessage = if (directive != null) "$directive\n\n$message" else message

        val request = ChatRequest(
            message = effectiveMessage,
            conversation = apiHistory,
            webSearch = if (webSearch) true else null
        )

        // 3. Execute network call and handle responses & errors gracefully
        try {
            val response = apiService.sendChat(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    return@withContext NetworkResult.Error(
                        userMessage = "Malformed response from server (empty body).",
                        statusCode = response.code(),
                        errorCode = "ERR_EMPTY_BODY"
                    )
                }

                if (body.success && !body.message.isNullOrBlank()) {
                    NetworkResult.Success(body.message)
                } else {
                    val errMsg = body.error?.message ?: "MINOVA backend returned an error."
                    val errCode = body.error?.code ?: "ERR_BACKEND_FAILED"
                    val isWebSearchUnavailable = errCode == "WEB_SEARCH_UNAVAILABLE" || errMsg.contains("WEB_SEARCH_UNAVAILABLE")
                    NetworkResult.Error(
                        userMessage = if (isWebSearchUnavailable) "Web Search is temporarily unavailable. Standard chat is still available." else errMsg,
                        statusCode = response.code(),
                        errorCode = if (isWebSearchUnavailable) "WEB_SEARCH_UNAVAILABLE" else errCode
                    )
                }
            } else {
                val statusCode = response.code()
                val errorBodyStr = try { response.errorBody()?.string() } catch (_: Exception) { null }
                val isWebSearchUnavailable = errorBodyStr?.contains("WEB_SEARCH_UNAVAILABLE") == true
                val friendlyMessage = if (isWebSearchUnavailable) {
                    "Web Search is temporarily unavailable. Standard chat is still available."
                } else when (statusCode) {
                    400 -> "Bad request. Please check your prompt input."
                    401 -> "Unauthorized request. Please check backend credentials."
                    403 -> "Access forbidden by server."
                    429 -> "Rate limit reached. Please wait a moment before sending another message."
                    500 -> "Internal server error. MINOVA backend encountered an issue."
                    502 -> "Bad gateway. Unable to reach MINOVA AI server."
                    503 -> "Service unavailable. Backend server is temporarily overloaded or down."
                    504 -> "Gateway timeout. Backend took too long to process."
                    else -> "Server error ($statusCode). Please try again later."
                }
                NetworkResult.Error(
                    userMessage = friendlyMessage,
                    statusCode = statusCode,
                    errorCode = if (isWebSearchUnavailable) "WEB_SEARCH_UNAVAILABLE" else "HTTP_$statusCode"
                )
            }
        } catch (e: UnknownHostException) {
            NetworkResult.Error(
                userMessage = "No internet connection or backend host cannot be resolved. Please check your network.",
                errorCode = "ERR_NO_INTERNET"
            )
        } catch (e: ConnectException) {
            NetworkResult.Error(
                userMessage = "Unable to reach MINOVA backend server. Please verify the backend is running.",
                errorCode = "ERR_CONNECTION_REFUSED"
            )
        } catch (e: NoRouteToHostException) {
            NetworkResult.Error(
                userMessage = "No route to host. Please verify your internet connection.",
                errorCode = "ERR_NO_ROUTE"
            )
        } catch (e: SocketTimeoutException) {
            NetworkResult.Error(
                userMessage = "Connection timed out. The server took too long to respond.",
                errorCode = "ERR_TIMEOUT"
            )
        } catch (e: JsonDataException) {
            NetworkResult.Error(
                userMessage = "Malformed response from server (JSON parse error).",
                errorCode = "ERR_MALFORMED_JSON"
            )
        } catch (e: JsonEncodingException) {
            NetworkResult.Error(
                userMessage = "Malformed request payload encoding.",
                errorCode = "ERR_ENCODING_ERROR"
            )
        } catch (e: IOException) {
            NetworkResult.Error(
                userMessage = "Network communication error. Please check your connection and try again.",
                errorCode = "ERR_IO"
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                userMessage = "An unexpected error occurred while communicating with the server.",
                errorCode = "ERR_UNEXPECTED"
            )
        }
    }

    override suspend fun analyzeFileMessage(
        filePart: MultipartBody.Part,
        message: String,
        conversationHistory: List<Message>,
        responseStyle: ResponseStyle,
        responseLanguage: ResponseLanguage,
        responseFormat: ResponseFormat
    ): NetworkResult<String> = withContext(ioDispatcher) {
        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }

        val apiHistory = buildApiConversationHistory(
            currentMessage = message,
            conversationHistory = conversationHistory
        )

        val directive = ResponsePreferencesHelper.buildSystemDirective(responseStyle, responseLanguage, responseFormat)
        val effectiveMessage = if (directive != null) "$directive\n\n$message" else message

        val messagePart = effectiveMessage.toRequestBody("text/plain".toMediaTypeOrNull())

        val conversationPart = if (apiHistory.isNotEmpty()) {
            val listType = Types.newParameterizedType(List::class.java, ApiChatMessage::class.java)
            val adapter = RetrofitClient.moshi.adapter<List<ApiChatMessage>>(listType)
            adapter.toJson(apiHistory).toRequestBody("application/json".toMediaTypeOrNull())
        } else {
            null
        }

        try {
            val response = apiService.analyzeFile(
                file = filePart,
                message = messagePart,
                conversation = conversationPart
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    return@withContext NetworkResult.Error(
                        userMessage = "Malformed response from server (empty body).",
                        statusCode = response.code(),
                        errorCode = "ERR_EMPTY_BODY"
                    )
                }

                val resultText = body.resultText
                if (body.success && !resultText.isNullOrBlank()) {
                    NetworkResult.Success(resultText)
                } else {
                    val errMsg = body.error?.message ?: "MINOVA backend returned an error during file analysis."
                    val errCode = body.error?.code ?: "ERR_BACKEND_FAILED"
                    NetworkResult.Error(
                        userMessage = errMsg,
                        statusCode = response.code(),
                        errorCode = errCode
                    )
                }
            } else {
                val errorCode = "ERR_HTTP_${response.code()}"
                val userMessage = when (response.code()) {
                    400 -> "Bad request. The uploaded file or prompt could not be processed."
                    413 -> "File is too large. The server rejected the upload (413 Payload Too Large)."
                    429 -> "Rate limit reached. Please wait a moment before sending another message."
                    500 -> "Internal server error. MINOVA backend encountered an issue processing the file."
                    502 -> "Bad gateway. Unable to reach MINOVA AI server."
                    503 -> "Service unavailable. Backend server is temporarily overloaded or down."
                    504 -> "Gateway timeout. Processing the file took too long."
                    else -> "Server returned error (${response.code()}). Please try again later."
                }
                NetworkResult.Error(
                    userMessage = userMessage,
                    statusCode = response.code(),
                    errorCode = errorCode
                )
            }
        } catch (e: ConnectException) {
            NetworkResult.Error(
                userMessage = "Unable to reach MINOVA backend server. Please verify the backend is running.",
                errorCode = "ERR_CONNECTION_REFUSED"
            )
        } catch (e: UnknownHostException) {
            NetworkResult.Error(
                userMessage = "Unable to reach MINOVA backend server. Please check your network connection.",
                errorCode = "ERR_CONNECTION_REFUSED"
            )
        } catch (e: NoRouteToHostException) {
            NetworkResult.Error(
                userMessage = "No route to host. Please verify your internet connection.",
                errorCode = "ERR_NO_ROUTE"
            )
        } catch (e: SocketTimeoutException) {
            NetworkResult.Error(
                userMessage = "Connection timed out while uploading or analyzing file.",
                errorCode = "ERR_TIMEOUT"
            )
        } catch (e: JsonDataException) {
            NetworkResult.Error(
                userMessage = "Malformed response from server (JSON parse error).",
                errorCode = "ERR_MALFORMED_JSON"
            )
        } catch (e: JsonEncodingException) {
            NetworkResult.Error(
                userMessage = "Malformed request payload encoding.",
                errorCode = "ERR_ENCODING_ERROR"
            )
        } catch (e: IOException) {
            NetworkResult.Error(
                userMessage = "Network communication error. Please check your connection and try again.",
                errorCode = "ERR_IO"
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                userMessage = "An unexpected error occurred while analyzing the file: ${e.message ?: "unknown"}",
                errorCode = "ERR_UNEXPECTED"
            )
        }
    }

    /**
     * Converts local Message history to backend [ApiChatMessage] protocol models.
     * Enforces context guidelines:
     * - Only includes previous user and assistant turns.
     * - Filters out error states, incomplete streaming messages, and blank content.
     * - Strips all UI-only metadata (IDs, timestamps, status, attachment metadata).
     * - Prevents duplicating the current user message if passed in the history list.
     * - Maps sender roles strictly to "user" and "assistant".
     */
    internal fun buildApiConversationHistory(
        currentMessage: String,
        conversationHistory: List<Message>
    ): List<ApiChatMessage> {
        return conversationHistory
            .filter { msg ->
                msg.status != MessageStatus.ERROR &&
                msg.status != MessageStatus.STREAMING &&
                msg.content.isNotBlank() &&
                (msg.sender == MessageSender.USER || msg.sender == MessageSender.ASSISTANT)
            }
            .let { list ->
                if (list.isNotEmpty() && list.last().sender == MessageSender.USER && list.last().content.trim() == currentMessage.trim()) {
                    list.dropLast(1)
                } else {
                    list
                }
            }
            .map { msg ->
                ApiChatMessage(
                    role = if (msg.sender == MessageSender.USER) "user" else "assistant",
                    content = msg.content
                )
            }
    }
}
