package com.example.data.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object MessageClipboardHelper {

    private val SSE_PREFIX_REGEX = Regex("^(data|event|id|retry):\\s*", RegexOption.IGNORE_CASE)
    private val SSE_PROTOCOL_MARKERS = listOf(
        "[DONE]",
        "data: [DONE]",
        "data:[DONE]",
        "event: error",
        "event: ping",
        ":keepalive",
        ": keepalive"
    )

    /**
     * Sanitizes message content before copying to clipboard:
     * - Strips SSE stream artifacts and protocol markers (data:, event:, retry:, [DONE])
     * - Strips raw JSON envelopes if internal responses leaked
     * - Strips internal metadata markers
     * - Preserves full text formatting, markdown, and code indentation.
     */
    fun sanitizeForCopy(content: String): String {
        if (content.isBlank()) return ""

        val lines = content.lines()
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            // Skip protocol completion markers
            if (SSE_PROTOCOL_MARKERS.any { it.equals(trimmed, ignoreCase = true) }) {
                continue
            }

            // Skip SSE protocol control/metadata lines (event:, id:, retry:, comment lines starting with :)
            if (trimmed.startsWith("event:", ignoreCase = true) ||
                trimmed.startsWith("id:", ignoreCase = true) ||
                trimmed.startsWith("retry:", ignoreCase = true) ||
                trimmed.startsWith(":")
            ) {
                continue
            }

            // Strip leading SSE data: prefix
            val lineWithoutSse = if (trimmed.startsWith("data:", ignoreCase = true)) {
                trimmed.substringAfter(":").trimStart()
            } else {
                line
            }

            // If the line is an SSE [DONE] after stripping
            if (lineWithoutSse.trim().equals("[DONE]", ignoreCase = true)) {
                continue
            }

            // If the line is raw internal JSON wrapper, e.g. {"candidates":[{"content":...
            val trimmedLine = lineWithoutSse.trim()
            if (trimmedLine.startsWith("{\"candidates\"") ||
                (trimmedLine.startsWith("{") && trimmedLine.endsWith("}") && trimmedLine.contains("\"parts\""))
            ) {
                val extracted = extractTextFromGeminiJson(trimmedLine)
                if (extracted != null) {
                    cleanedLines.add(extracted)
                    continue
                }
            }

            cleanedLines.add(lineWithoutSse)
        }

        return cleanedLines.joinToString("\n").trim()
    }

    /**
     * Extracts text field if raw Gemini JSON leaked into content.
     */
    fun extractTextFromGeminiJson(json: String): String? {
        return try {
            val marker = "\"text\":"
            val textIdx = json.indexOf(marker)
            if (textIdx != -1) {
                val firstQuote = json.indexOf("\"", textIdx + marker.length)
                if (firstQuote != -1) {
                    val sb = StringBuilder()
                    var escaped = false
                    var i = firstQuote + 1
                    while (i < json.length) {
                        val c = json[i]
                        if (escaped) {
                            when (c) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\"' -> sb.append('\"')
                                '\\' -> sb.append('\\')
                                else -> sb.append(c)
                            }
                            escaped = false
                        } else if (c == '\\') {
                            escaped = true
                        } else if (c == '\"') {
                            break
                        } else {
                            sb.append(c)
                        }
                        i++
                    }
                    sb.toString()
                } else null
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Copies plain text to Android ClipboardManager and returns true if successful.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "MINOVA AI"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
