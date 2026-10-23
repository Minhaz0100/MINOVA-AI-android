package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload sent to POST /api/chat
 */
@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "message")
    val message: String,
    @Json(name = "conversation")
    val conversation: List<ApiChatMessage> = emptyList(),
    @Json(name = "webSearch")
    val webSearch: Boolean? = null
)

/**
 * Message history element within [ChatRequest]
 */
@JsonClass(generateAdapter = true)
data class ApiChatMessage(
    @Json(name = "role")
    val role: String,
    @Json(name = "content")
    val content: String
)

/**
 * Response payload received from POST /api/chat
 */
@JsonClass(generateAdapter = true)
data class ChatResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "error")
    val error: ApiError? = null
)

/**
 * Error detail object within [ChatResponse]
 */
@JsonClass(generateAdapter = true)
data class ApiError(
    @Json(name = "code")
    val code: String,
    @Json(name = "message")
    val message: String
)

/**
 * Response payload received from POST /api/files/analyze
 */
@JsonClass(generateAdapter = true)
data class FileAnalysisResponse(
    @Json(name = "success")
    val success: Boolean = true,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "response")
    val response: String? = null,
    @Json(name = "text")
    val text: String? = null,
    @Json(name = "analysis")
    val analysis: String? = null,
    @Json(name = "error")
    val error: ApiError? = null
) {
    val resultText: String?
        get() = message ?: response ?: text ?: analysis
}
