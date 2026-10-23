package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.model.DocumentBlockType
import com.example.data.model.DocumentContentBlock
import com.example.data.model.FileStudioMode
import com.example.data.model.GeneratedBinaryFile
import com.example.data.model.ImageAspectRatio
import com.example.data.model.SpreadsheetWorksheet
import com.example.data.remote.NetworkConfig
import com.example.data.remote.RetrofitClient
import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.api.MinovaFileStudioApiService
import com.example.data.remote.model.CloudflareImageGenerateRequest
import com.example.data.remote.model.DocumentGenerateRequest
import com.example.data.remote.model.DocumentSectionPayload
import com.example.data.remote.model.ImageGenerateRequest
import com.example.data.remote.model.SpreadsheetGenerateRequest
import com.example.data.remote.model.WorksheetPayload
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface FileStudioRepository {
    suspend fun generateDocument(
        mode: FileStudioMode,
        title: String,
        subtitle: String = "",
        author: String = "",
        blocks: List<DocumentContentBlock>
    ): NetworkResult<GeneratedBinaryFile>

    suspend fun generateSpreadsheet(
        workbookTitle: String,
        worksheets: List<SpreadsheetWorksheet>
    ): NetworkResult<GeneratedBinaryFile>

    suspend fun generateImage(
        prompt: String,
        aspectRatio: ImageAspectRatio = ImageAspectRatio.SQUARE_1_1
    ): NetworkResult<GeneratedBinaryFile>

    suspend fun checkImageHealth(): NetworkResult<Boolean> = NetworkResult.Success(true)
}

class FileStudioRepositoryImpl(
    private val apiService: MinovaFileStudioApiService = RetrofitClient.getFileStudioApiService(),
    private val minovaApiService: MinovaApiService = RetrofitClient.getApiService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FileStudioRepository {

    override suspend fun generateDocument(
        mode: FileStudioMode,
        title: String,
        subtitle: String,
        author: String,
        blocks: List<DocumentContentBlock>
    ): NetworkResult<GeneratedBinaryFile> = withContext(ioDispatcher) {
        if (mode != FileStudioMode.PDF && mode != FileStudioMode.DOCX) {
            return@withContext NetworkResult.Error(
                userMessage = "Invalid document mode: ${mode.displayName}.",
                errorCode = "VALIDATION_ERROR"
            )
        }

        val validationError = validateDocumentInput(title, blocks)
        if (validationError != null) {
            return@withContext NetworkResult.Error(
                userMessage = validationError,
                errorCode = "VALIDATION_ERROR"
            )
        }

        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }

        val request = buildDocumentRequest(
            title = title,
            subtitle = subtitle,
            author = author,
            blocks = blocks
        )

        executeFileCall(
            mode = mode,
            fallbackTitle = title.trim()
        ) {
            when (mode) {
                FileStudioMode.PDF -> apiService.generatePdf(request)
                FileStudioMode.DOCX -> apiService.generateDocx(request)
                else -> throw IllegalArgumentException("Unsupported document mode: $mode")
            }
        }
    }

    override suspend fun generateSpreadsheet(
        workbookTitle: String,
        worksheets: List<SpreadsheetWorksheet>
    ): NetworkResult<GeneratedBinaryFile> = withContext(ioDispatcher) {
        val validationError = validateSpreadsheetInput(workbookTitle, worksheets)
        if (validationError != null) {
            return@withContext NetworkResult.Error(
                userMessage = validationError,
                errorCode = "VALIDATION_ERROR"
            )
        }

        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }

        val request = buildSpreadsheetRequest(workbookTitle, worksheets)

        executeFileCall(
            mode = FileStudioMode.XLSX,
            fallbackTitle = workbookTitle.trim()
        ) {
            apiService.generateXlsx(request)
        }
    }

    override suspend fun generateImage(
        prompt: String,
        aspectRatio: ImageAspectRatio
    ): NetworkResult<GeneratedBinaryFile> = withContext(ioDispatcher) {
        val validationError = validateImageInput(prompt)
        if (validationError != null) {
            return@withContext NetworkResult.Error(
                userMessage = validationError,
                errorCode = "VALIDATION_ERROR"
            )
        }

        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured. Set MINOVA_BACKEND_BASE_URL to your active backend server.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }

        val request = buildCloudflareImageRequest(prompt)

        executeFileCall(
            mode = FileStudioMode.IMAGE,
            fallbackTitle = prompt.trim().take(32).ifBlank { "minova_image" }
        ) {
            minovaApiService.generateCloudflareImage(request)
        }
    }

    override suspend fun checkImageHealth(): NetworkResult<Boolean> = withContext(ioDispatcher) {
        if (!NetworkConfig.isBackendConfigured) {
            return@withContext NetworkResult.Error(
                userMessage = "Backend URL not configured.",
                errorCode = "ERR_BACKEND_UNCONFIGURED"
            )
        }
        try {
            val response = minovaApiService.checkImageHealth()
            if (response.isSuccessful) {
                val bodyText = response.body()?.use { it.string() }.orEmpty()
                val isHealthy = !bodyText.contains("\"healthy\":false", ignoreCase = true) &&
                    !bodyText.contains("\"enabled\":false", ignoreCase = true) &&
                    !bodyText.contains("\"status\":\"unavailable\"", ignoreCase = true)
                NetworkResult.Success(isHealthy)
            } else {
                val errorText = try {
                    response.errorBody()?.use { it.string() }
                } catch (_: Exception) {
                    null
                }
                parseNon2xxError(
                    statusCode = response.code(),
                    contentType = response.headers()["Content-Type"].orEmpty().lowercase(),
                    errorBodyText = errorText,
                    mode = FileStudioMode.IMAGE
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(
                userMessage = "Unable to check image service health: ${e.message ?: "network error"}",
                errorCode = "ERR_HEALTH_CHECK"
            )
        }
    }

    private suspend fun executeFileCall(
        mode: FileStudioMode,
        fallbackTitle: String,
        call: suspend () -> Response<ResponseBody>
    ): NetworkResult<GeneratedBinaryFile> {
        return try {
            val response = call()
            parseFileGenerationResponse(
                response = response,
                mode = mode,
                fallbackTitle = fallbackTitle
            )
        } catch (e: CancellationException) {
            throw e
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
                userMessage = "Connection timed out while generating ${mode.displayName}.",
                errorCode = "ERR_TIMEOUT"
            )
        } catch (e: IOException) {
            NetworkResult.Error(
                userMessage = "Network communication error while generating ${mode.displayName}. Please try again.",
                errorCode = "ERR_IO"
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                userMessage = "An unexpected error occurred while generating ${mode.displayName}: ${e.message ?: "unknown"}",
                errorCode = "ERR_UNEXPECTED"
            )
        }
    }

    companion object {
        fun validateDocumentInput(
            title: String,
            blocks: List<DocumentContentBlock>
        ): String? {
            if (title.trim().isBlank()) {
                return "Please enter a document title."
            }
            if (blocks.isEmpty()) {
                return "Please add at least one structured content block (heading, paragraph, list, or table)."
            }
            val hasNonBlankContent = blocks.any { block ->
                when (block.type) {
                    DocumentBlockType.HEADING, DocumentBlockType.PARAGRAPH -> block.text.trim().isNotEmpty()
                    DocumentBlockType.LIST -> block.listItems.any { it.trim().isNotEmpty() }
                    DocumentBlockType.TABLE -> block.tableHeaders.any { it.trim().isNotEmpty() } ||
                        block.tableRows.any { row -> row.any { it.trim().isNotEmpty() } }
                }
            }
            if (!hasNonBlankContent) {
                return "Please enter content in at least one heading, paragraph, list item, or table cell."
            }
            return null
        }

        fun validateSpreadsheetInput(
            workbookTitle: String,
            worksheets: List<SpreadsheetWorksheet>
        ): String? {
            if (workbookTitle.trim().isBlank()) {
                return "Please enter a workbook title."
            }
            if (worksheets.isEmpty()) {
                return "Please include at least one worksheet."
            }
            for ((index, sheet) in worksheets.withIndex()) {
                if (sheet.name.trim().isBlank()) {
                    return "Worksheet #${index + 1} must have a name."
                }
                if (sheet.headers.none { it.trim().isNotEmpty() }) {
                    return "Worksheet '${sheet.name.trim()}' must have at least one non-empty column header."
                }
            }
            return null
        }

        fun validateImageInput(prompt: String): String? {
            if (prompt.trim().isBlank()) {
                return "Please enter an image prompt before generating."
            }
            return null
        }

        fun buildDocumentRequest(
            title: String,
            subtitle: String,
            author: String,
            blocks: List<DocumentContentBlock>
        ): DocumentGenerateRequest {
            val cleanTitle = title.trim()
            val cleanSubtitle = subtitle.trim().takeIf { it.isNotEmpty() }
            val cleanAuthor = author.trim().takeIf { it.isNotEmpty() }

            val sections = blocks.mapNotNull { block ->
                when (block.type) {
                    DocumentBlockType.HEADING -> {
                        val headingText = block.text.trim()
                        if (headingText.isEmpty()) null
                        else DocumentSectionPayload(
                            type = "heading",
                            text = headingText,
                            heading = headingText,
                            level = block.headingLevel.coerceIn(1, 3)
                        )
                    }
                    DocumentBlockType.PARAGRAPH -> {
                        val paragraphText = block.text.trim()
                        if (paragraphText.isEmpty()) null
                        else DocumentSectionPayload(
                            type = "paragraph",
                            text = paragraphText
                        )
                    }
                    DocumentBlockType.LIST -> {
                        val cleanItems = block.listItems.map { it.trim() }.filter { it.isNotEmpty() }
                        if (cleanItems.isEmpty()) null
                        else DocumentSectionPayload(
                            type = "list",
                            ordered = block.listOrdered,
                            items = cleanItems
                        )
                    }
                    DocumentBlockType.TABLE -> {
                        val cleanHeaders = block.tableHeaders.map { it.trim() }
                        val cleanRows = block.tableRows.map { row ->
                            val normalized = row.map { it.trim() }
                            if (normalized.size < cleanHeaders.size) {
                                normalized + List(cleanHeaders.size - normalized.size) { "" }
                            } else {
                                normalized.take(cleanHeaders.size)
                            }
                        }.filter { row -> row.any { it.isNotEmpty() } }

                        if (cleanHeaders.all { it.isEmpty() } && cleanRows.isEmpty()) null
                        else DocumentSectionPayload(
                            type = "table",
                            headers = cleanHeaders,
                            rows = cleanRows
                        )
                    }
                }
            }

            val compiledContent = buildString {
                sections.forEachIndexed { idx, section ->
                    when (section.type) {
                        "heading" -> {
                            val hashes = "#".repeat((section.level ?: 1).coerceIn(1, 3))
                            append("$hashes ${section.text ?: section.heading ?: ""}")
                        }
                        "paragraph" -> {
                            append(section.text ?: "")
                        }
                        "list" -> {
                            section.items?.forEachIndexed { itemIdx, item ->
                                val prefix = if (section.ordered == true) "${itemIdx + 1}." else "-"
                                append("$prefix $item")
                                if (itemIdx < section.items.lastIndex) append("\n")
                            }
                        }
                        "table" -> {
                            val headers = section.headers.orEmpty()
                            if (headers.isNotEmpty()) {
                                append("| ${headers.joinToString(" | ")} |\n")
                                append("| ${headers.joinToString(" | ") { "---" }} |")
                                section.rows?.forEach { row ->
                                    append("\n| ${row.joinToString(" | ")} |")
                                }
                            }
                        }
                    }
                    if (idx < sections.lastIndex) append("\n\n")
                }
            }

            return DocumentGenerateRequest(
                title = cleanTitle,
                subtitle = cleanSubtitle,
                author = cleanAuthor,
                sections = sections,
                blocks = sections,
                content = compiledContent
            )
        }

        fun buildSpreadsheetRequest(
            workbookTitle: String,
            worksheets: List<SpreadsheetWorksheet>
        ): SpreadsheetGenerateRequest {
            val cleanTitle = workbookTitle.trim()
            val sheetPayloads = worksheets.map { sheet ->
                val cleanHeaders = sheet.headers.map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("Column 1") }
                val cleanRows = sheet.rows.map { row ->
                    val trimmedRow = row.map { it.trim() }
                    if (trimmedRow.size < cleanHeaders.size) {
                        trimmedRow + List(cleanHeaders.size - trimmedRow.size) { "" }
                    } else {
                        trimmedRow.take(cleanHeaders.size)
                    }
                }
                WorksheetPayload(
                    name = sheet.name.trim().ifEmpty { "Sheet1" },
                    headers = cleanHeaders,
                    columns = cleanHeaders,
                    rows = cleanRows
                )
            }
            return SpreadsheetGenerateRequest(
                title = cleanTitle,
                workbookTitle = cleanTitle,
                sheets = sheetPayloads,
                worksheets = sheetPayloads
            )
        }

        fun buildImageRequest(
            prompt: String,
            aspectRatio: ImageAspectRatio
        ): ImageGenerateRequest {
            return ImageGenerateRequest(
                prompt = prompt.trim(),
                aspectRatio = aspectRatio.value
            )
        }

        fun buildCloudflareImageRequest(
            prompt: String
        ): CloudflareImageGenerateRequest {
            return CloudflareImageGenerateRequest(
                prompt = prompt.trim()
            )
        }

        /**
         * Safely decodes binary JPEG/PNG/WebP image bytes into a [Bitmap] without throwing.
         */
        fun decodeBitmapSafely(bytes: ByteArray): Bitmap? {
            if (bytes.isEmpty()) return null
            return try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Inspects response status and Content-Type before parsing either binary file bytes
         * or structured JSON error payloads, closing response bodies safely.
         */
        fun parseFileGenerationResponse(
            response: Response<ResponseBody>,
            mode: FileStudioMode,
            fallbackTitle: String
        ): NetworkResult<GeneratedBinaryFile> {
            val statusCode = response.code()
            val headerContentType = response.headers()["Content-Type"]
                ?: response.body()?.contentType()?.toString()
                ?: response.errorBody()?.contentType()?.toString()
                ?: ""
            val lowerContentType = headerContentType.lowercase()

            if (!response.isSuccessful) {
                val errorText = try {
                    response.errorBody()?.use { it.string() }
                } catch (_: Exception) {
                    null
                }
                return parseNon2xxError(
                    statusCode = statusCode,
                    contentType = lowerContentType,
                    errorBodyText = errorText,
                    mode = mode
                )
            }

            val body = response.body()
            if (body == null) {
                return NetworkResult.Error(
                    userMessage = "Server returned an empty response body for ${mode.displayName}.",
                    statusCode = statusCode,
                    errorCode = "ERR_EMPTY_BODY"
                )
            }

            // Guard against a 2xx response that actually returned a JSON error payload instead of binary file bytes
            if (lowerContentType.contains("application/json")) {
                val jsonText = try {
                    body.use { it.string() }
                } catch (_: Exception) {
                    null
                }
                return parseNon2xxError(
                    statusCode = statusCode,
                    contentType = lowerContentType,
                    errorBodyText = jsonText,
                    mode = mode
                )
            }

            val bytes = body.use { it.bytes() }
            if (bytes.isEmpty()) {
                return NetworkResult.Error(
                    userMessage = "Generated ${mode.displayName} file was empty (0 bytes).",
                    statusCode = statusCode,
                    errorCode = "ERR_EMPTY_FILE"
                )
            }

            val resolvedMimeType = resolveMimeType(mode, lowerContentType)
            val resolvedExtension = resolveExtension(mode, resolvedMimeType)
            val contentDisposition = response.headers()["Content-Disposition"]
            val fileName = resolveFileName(
                contentDisposition = contentDisposition,
                fallbackTitle = fallbackTitle,
                extension = resolvedExtension
            )

            return NetworkResult.Success(
                GeneratedBinaryFile(
                    mode = mode,
                    fileName = fileName,
                    mimeType = resolvedMimeType,
                    bytes = bytes
                )
            )
        }

        private fun parseNon2xxError(
            statusCode: Int,
            contentType: String,
            errorBodyText: String?,
            mode: FileStudioMode
        ): NetworkResult.Error {
            var extractedCode: String? = null
            var extractedMessage: String? = null

            val trimmedError = errorBodyText?.trim().orEmpty()
            val looksLikeJson = contentType.contains("application/json") ||
                (trimmedError.startsWith("{") && trimmedError.endsWith("}"))

            if (looksLikeJson && trimmedError.isNotEmpty()) {
                try {
                    val mapType = Types.newParameterizedType(
                        Map::class.java,
                        String::class.java,
                        Any::class.java
                    )
                    val adapter = RetrofitClient.moshi.adapter<Map<String, Any?>>(mapType)
                    val map = adapter.fromJson(trimmedError)
                    if (map != null) {
                        val topCode = map["code"] as? String
                        val topMessage = map["message"] as? String
                        val errorField = map["error"]
                        when (errorField) {
                            is Map<*, *> -> {
                                extractedCode = (errorField["code"] as? String) ?: topCode
                                extractedMessage = (errorField["message"] as? String) ?: topMessage
                            }
                            is String -> {
                                if (errorField == errorField.uppercase() && errorField.contains("_")) {
                                    extractedCode = errorField
                                    extractedMessage = topMessage
                                } else {
                                    extractedCode = topCode
                                    extractedMessage = topMessage ?: errorField
                                }
                            }
                            else -> {
                                extractedCode = topCode
                                extractedMessage = topMessage
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to string inspection if JSON structure is non-standard
                }
            }

            val isImageUnavailable =
                extractedCode == "IMAGE_GENERATION_UNAVAILABLE" ||
                    trimmedError.contains("IMAGE_GENERATION_UNAVAILABLE") ||
                    (mode == FileStudioMode.IMAGE && statusCode == 503)

            if (isImageUnavailable) {
                val unavailableMsg = extractedMessage?.takeIf { it.isNotBlank() }
                    ?: "Image generation is currently unavailable on the MINOVA AI backend (HTTP 503 IMAGE_GENERATION_UNAVAILABLE)."
                return NetworkResult.Error(
                    userMessage = unavailableMsg,
                    statusCode = statusCode,
                    errorCode = "IMAGE_GENERATION_UNAVAILABLE"
                )
            }

            val finalErrorCode = extractedCode?.takeIf { it.isNotBlank() } ?: "HTTP_$statusCode"
            val finalUserMessage = extractedMessage?.takeIf { it.isNotBlank() } ?: when (statusCode) {
                400 -> "Validation failed on server (400). Please check your ${mode.shortLabel} fields."
                401 -> "Unauthorized request (401). Please check backend credentials."
                403 -> "Access forbidden by server (403)."
                404 -> "Endpoint ${mode.endpointPath} was not found on the active server (HTTP 404). Verify that the latest File Studio backend routes are deployed."
                413 -> "The ${mode.shortLabel} content payload is too large for the server (HTTP 413)."
                429 -> "Rate limit reached (HTTP 429). Please wait a moment and try again."
                500 -> "Internal server error (HTTP 500) while generating ${mode.displayName}."
                502 -> "Bad gateway (HTTP 502). Unable to reach the MINOVA backend service."
                503 -> "File generation service is currently unavailable (HTTP 503)."
                504 -> "Gateway timeout (HTTP 504). Generating ${mode.displayName} took too long."
                else -> "Server error (HTTP $statusCode) while generating ${mode.displayName}."
            }

            return NetworkResult.Error(
                userMessage = finalUserMessage,
                statusCode = statusCode,
                errorCode = finalErrorCode
            )
        }

        fun resolveMimeType(mode: FileStudioMode, headerContentType: String): String {
            val cleanHeader = headerContentType.substringBefore(";").trim().lowercase()
            return when (mode) {
                FileStudioMode.PDF -> "application/pdf"
                FileStudioMode.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                FileStudioMode.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                FileStudioMode.IMAGE -> when {
                    cleanHeader == "image/jpeg" || cleanHeader == "image/jpg" -> "image/jpeg"
                    cleanHeader == "image/webp" -> "image/webp"
                    cleanHeader == "image/png" -> "image/png"
                    else -> mode.defaultMimeType
                }
            }
        }

        fun resolveExtension(mode: FileStudioMode, mimeType: String): String {
            return when (mode) {
                FileStudioMode.PDF -> ".pdf"
                FileStudioMode.DOCX -> ".docx"
                FileStudioMode.XLSX -> ".xlsx"
                FileStudioMode.IMAGE -> when (mimeType.lowercase()) {
                    "image/png" -> ".png"
                    "image/webp" -> ".webp"
                    else -> ".jpg"
                }
            }
        }

        fun resolveFileName(
            contentDisposition: String?,
            fallbackTitle: String,
            extension: String
        ): String {
            val cleanExt = if (extension.startsWith(".")) extension else ".$extension"
            if (!contentDisposition.isNullOrBlank()) {
                val regex = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
                val match = regex.find(contentDisposition)
                val extracted = match?.groupValues?.getOrNull(1)?.trim()
                if (!extracted.isNullOrBlank()) {
                    val safeExtracted = sanitizeBaseFileName(extracted.substringBeforeLast("."))
                    if (safeExtracted.isNotBlank()) {
                        return "$safeExtracted$cleanExt"
                    }
                }
            }
            val base = sanitizeBaseFileName(fallbackTitle).ifBlank { "minova_file" }
            return "$base$cleanExt"
        }

        fun sanitizeBaseFileName(raw: String): String {
            return raw.trim()
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .take(48)
        }
    }
}

/**
 * In-memory fallback repository for Previews / isolated UI tests.
 * Note: Image generation returns HTTP 503 IMAGE_GENERATION_UNAVAILABLE to match backend behavior.
 */
class InMemoryFileStudioRepository : FileStudioRepository {
    override suspend fun generateDocument(
        mode: FileStudioMode,
        title: String,
        subtitle: String,
        author: String,
        blocks: List<DocumentContentBlock>
    ): NetworkResult<GeneratedBinaryFile> {
        val error = FileStudioRepositoryImpl.validateDocumentInput(title, blocks)
        if (error != null) {
            return NetworkResult.Error(userMessage = error, errorCode = "VALIDATION_ERROR")
        }
        val fileName = FileStudioRepositoryImpl.resolveFileName(null, title, mode.defaultExtension)
        return NetworkResult.Success(
            GeneratedBinaryFile(
                mode = mode,
                fileName = fileName,
                mimeType = mode.defaultMimeType,
                bytes = "%PDF-1.4 MINOVA preview bytes".encodeToByteArray()
            )
        )
    }

    override suspend fun generateSpreadsheet(
        workbookTitle: String,
        worksheets: List<SpreadsheetWorksheet>
    ): NetworkResult<GeneratedBinaryFile> {
        val error = FileStudioRepositoryImpl.validateSpreadsheetInput(workbookTitle, worksheets)
        if (error != null) {
            return NetworkResult.Error(userMessage = error, errorCode = "VALIDATION_ERROR")
        }
        val fileName = FileStudioRepositoryImpl.resolveFileName(
            null,
            workbookTitle,
            FileStudioMode.XLSX.defaultExtension
        )
        return NetworkResult.Success(
            GeneratedBinaryFile(
                mode = FileStudioMode.XLSX,
                fileName = fileName,
                mimeType = FileStudioMode.XLSX.defaultMimeType,
                bytes = "PK\u0003\u0004 MINOVA xlsx bytes".encodeToByteArray()
            )
        )
    }

    override suspend fun generateImage(
        prompt: String,
        aspectRatio: ImageAspectRatio
    ): NetworkResult<GeneratedBinaryFile> {
        val error = FileStudioRepositoryImpl.validateImageInput(prompt)
        if (error != null) {
            return NetworkResult.Error(userMessage = error, errorCode = "VALIDATION_ERROR")
        }
        return NetworkResult.Error(
            userMessage = "Image generation is disabled by default on the MINOVA AI backend (HTTP 503 IMAGE_GENERATION_UNAVAILABLE).",
            statusCode = 503,
            errorCode = "IMAGE_GENERATION_UNAVAILABLE"
        )
    }
}
