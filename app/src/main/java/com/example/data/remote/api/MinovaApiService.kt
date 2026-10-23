
package com.example.data.remote.api

import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.remote.model.CloudflareImageGenerateRequest
import com.example.data.remote.model.DocumentGenerateRequest
import com.example.data.remote.model.FileAnalysisResponse
import com.example.data.remote.model.ImageGenerateRequest
import com.example.data.remote.model.SpreadsheetGenerateRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

/**
 * Retrofit REST API interface for the MINOVA AI backend.
 *
 * Provider API keys remain on the backend.
 */
interface MinovaApiService {

    /**
     * Send a chat message with conversation context.
     */
    @POST("api/chat")
    suspend fun sendChat(
        @Body request: ChatRequest
    ): Response<ChatResponse>

    /**
     * Receive chat responses through Server-Sent Events.
     */
    @Headers(
        "Accept: text/event-stream",
        "Cache-Control: no-cache",
        "Accept-Encoding: identity",
        "X-Accel-Buffering: no"
    )
    @Streaming
    @POST("api/chat/stream")
    suspend fun sendChatStream(
        @Body request: ChatRequest
    ): Response<ResponseBody>

    /**
     * Upload a file for AI analysis.
     */
    @Multipart
    @POST("api/files/analyze")
    suspend fun analyzeFile(
        @Part file: MultipartBody.Part,
        @Part("message") message: RequestBody,
        @Part("conversation") conversation: RequestBody? = null
    ): Response<FileAnalysisResponse>

    /**
     * Generate an image through the Cloudflare AI integration.
     * The backend returns binary JPEG image data.
     */
    @Streaming
    @POST("api/image/generate")
    suspend fun generateCloudflareImage(
        @Body request: CloudflareImageGenerateRequest
    ): Response<ResponseBody>

    /**
     * Check Cloudflare image generation service health.
     */
    @GET("api/image/health")
    suspend fun checkImageHealth(): Response<ResponseBody>
}

/**
 * Retrofit REST API interface for MINOVA AI File Studio.
 */
interface MinovaFileStudioApiService {

    /**
     * Generate a PDF document.
     */
    @POST("api/files/generate/pdf")
    suspend fun generatePdf(
        @Body request: DocumentGenerateRequest
    ): Response<ResponseBody>

    /**
     * Generate a Word document.
     */
    @POST("api/files/generate/docx")
    suspend fun generateDocx(
        @Body request: DocumentGenerateRequest
    ): Response<ResponseBody>

    /**
     * Generate an Excel workbook.
     */
    @POST("api/files/generate/xlsx")
    suspend fun generateXlsx(
        @Body request: SpreadsheetGenerateRequest
    ): Response<ResponseBody>

    /**
     * Legacy File Studio image endpoint.
     * Retained for compatibility with existing references.
     */
    @POST("api/files/generate/image")
    suspend fun generateImage(
        @Body request: ImageGenerateRequest
    ): Response<ResponseBody>
}
