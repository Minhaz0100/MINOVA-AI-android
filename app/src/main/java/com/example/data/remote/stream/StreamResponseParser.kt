package com.example.data.remote.stream

/**
 * Events extracted from the Server-Sent Events (SSE) or chunked stream.
 */
sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Error(val message: String, val code: String? = null) : StreamEvent()
    object Done : StreamEvent()
}

/**
 * State-aware parser for SSE and chunked streaming responses from the MINOVA AI backend.
 * Handles standard SSE lines (data: ...), Gemini streaming schemas, NDJSON, and raw text chunks.
 * Built with pure Kotlin to guarantee portability across JVM unit tests and Android runtime.
 */
class StreamResponseParser {

    private var lastEventType: String? = null

    /**
     * Parses a single line from the response stream.
     * Returns null for heartbeat/comment lines or formatting delimiters.
     */
    fun parseLine(rawLine: String): StreamEvent? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) {
            // Heartbeat, comment, or empty separator
            return null
        }

        // Track SSE event type if explicitly provided (e.g. "event: error")
        if (trimmed.startsWith("event:", ignoreCase = true)) {
            lastEventType = trimmed.substring(6).trim()
            return null
        }

        // Ignore metadata lines
        if (trimmed.startsWith("id:", ignoreCase = true) || trimmed.startsWith("retry:", ignoreCase = true)) {
            return null
        }

        // Strip "data:" prefix while preserving whitespace inside the chunk payload
        val payload = when {
            rawLine.startsWith("data: ") -> rawLine.substring(6)
            rawLine.startsWith("data:") -> rawLine.substring(5)
            else -> rawLine
        }

        val trimmedPayload = payload.trim()

        // Handle explicit completion event type from preceding "event:" line
        if (lastEventType.equals("done", ignoreCase = true) ||
            lastEventType.equals("complete", ignoreCase = true) ||
            lastEventType.equals("finish", ignoreCase = true) ||
            lastEventType.equals("end", ignoreCase = true)
        ) {
            lastEventType = null
            return StreamEvent.Done
        }

        // Handle standard SSE stream termination markers
        if (trimmedPayload.equals("[DONE]", ignoreCase = true) ||
            trimmedPayload.equals("[COMPLETE]", ignoreCase = true) ||
            trimmedPayload.equals("[FINISH]", ignoreCase = true)
        ) {
            lastEventType = null
            return StreamEvent.Done
        }

        // If preceding line was "event: error", treat this data line as an error
        if (lastEventType.equals("error", ignoreCase = true)) {
            lastEventType = null
            val errMsg = extractErrorMessage(trimmedPayload) ?: trimmedPayload
            val errCode = extractStringField(trimmedPayload, "code")
                ?: if (trimmedPayload.contains("WEB_SEARCH_UNAVAILABLE")) "WEB_SEARCH_UNAVAILABLE" else "ERR_STREAM_BACKEND"
            return StreamEvent.Error(errMsg, errCode)
        }

        // Reset lastEventType for standard message data
        lastEventType = null

        // Parse JSON payloads
        if (trimmedPayload.startsWith("{") && trimmedPayload.endsWith("}")) {
            // Check for backend error object
            val errMsg = extractErrorMessage(trimmedPayload)
            if (errMsg != null) {
                val errCode = extractStringField(trimmedPayload, "code")
                    ?: if (trimmedPayload.contains("WEB_SEARCH_UNAVAILABLE")) "WEB_SEARCH_UNAVAILABLE" else "ERR_STREAM_BACKEND"
                return StreamEvent.Error(errMsg, errCode)
            }

            // Check standard message/text/chunk/content/response/output fields FIRST
            val textValue = extractStringField(trimmedPayload, "message")
                ?: extractStringField(trimmedPayload, "text")
                ?: extractStringField(trimmedPayload, "chunk")
                ?: extractStringField(trimmedPayload, "content")
                ?: extractStringField(trimmedPayload, "response")
                ?: extractStringField(trimmedPayload, "output")

            if (textValue != null) {
                return StreamEvent.Chunk(textValue)
            }

            // Check delta field
            val deltaText = extractDeltaContent(trimmedPayload)
            if (deltaText != null) {
                return StreamEvent.Chunk(deltaText)
            }

            // Check Gemini candidates streaming format: candidates[0].content.parts[0].text
            val candidateText = extractCandidateText(trimmedPayload)
            if (candidateText != null) {
                return StreamEvent.Chunk(candidateText)
            }

            // Check for stream completion indicator or pure protocol status metadata:
            // e.g. {"done": true}, {"completed": true}, {"success": true}
            val isCompletionOrStatus = extractBooleanField(trimmedPayload, "done") ||
                extractBooleanField(trimmedPayload, "completed") ||
                extractBooleanField(trimmedPayload, "success")

            if (isCompletionOrStatus) {
                return StreamEvent.Done
            }

            // Any other JSON payload without textual content (e.g. metadata, telemetry)
            // is protocol metadata and MUST NOT be displayed as an assistant chat message.
            return null
        }

        // Fallback: return payload as plain text chunk ONLY if non-empty and not JSON / comment
        if (payload.isNotEmpty() && !payload.startsWith("{") && !payload.startsWith(":")) {
            return StreamEvent.Chunk(payload)
        }

        return null
    }

    private fun extractStringField(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val match = pattern.find(json) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun extractBooleanField(json: String, key: String): Boolean {
        val pattern = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        val match = pattern.find(json) ?: return false
        return match.groupValues[1].equals("true", ignoreCase = true)
    }

    private fun extractDeltaContent(json: String): String? {
        val nestedDeltaContent = Regex("\"delta\"\\s*:\\s*\\{[^}]*\"content\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").find(json)
        if (nestedDeltaContent != null) {
            return unescapeJson(nestedDeltaContent.groupValues[1])
        }
        val nestedDeltaText = Regex("\"delta\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").find(json)
        if (nestedDeltaText != null) {
            return unescapeJson(nestedDeltaText.groupValues[1])
        }
        val directDelta = Regex("\"delta\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").find(json)
        if (directDelta != null) {
            return unescapeJson(directDelta.groupValues[1])
        }
        return null
    }

    private fun extractErrorMessage(json: String): String? {
        val nestedMessage = Regex("\"error\"\\s*:\\s*\\{[^}]*\"message\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").find(json)
        if (nestedMessage != null) {
            return unescapeJson(nestedMessage.groupValues[1])
        }
        val directError = Regex("\"error\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").find(json)
        if (directError != null) {
            return unescapeJson(directError.groupValues[1])
        }
        return null
    }

    private fun extractCandidateText(json: String): String? {
        val pattern = Regex("\"parts\"\\s*:\\s*\\[\\s*\\{[^}]*\"text\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val match = pattern.find(json) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun unescapeJson(str: String): String {
        return str
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
