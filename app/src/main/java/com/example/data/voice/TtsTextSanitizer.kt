package com.example.data.voice

import java.util.Locale

/**
 * Utility for language detection, markdown/code/SSE cleaning, and sentence chunking for Android Text-to-Speech.
 */
object TtsTextSanitizer {

    /**
     * Detects whether the given text is predominantly Bengali/Bangla using Unicode block analysis.
     * Bengali Unicode block: \u0980 - \u09FF
     */
    fun isPredominantlyBengali(text: String): Boolean {
        if (text.isBlank()) return false
        var bengaliCharCount = 0
        var latinCharCount = 0

        for (char in text) {
            val codePoint = char.code
            if (codePoint in 0x0980..0x09FF) {
                bengaliCharCount++
            } else if ((codePoint in 0x0041..0x005A) || (codePoint in 0x0061..0x007A)) {
                latinCharCount++
            }
        }

        // If there's any significant Bengali presence or more Bengali than Latin letters
        return bengaliCharCount > 0 && (bengaliCharCount >= latinCharCount || bengaliCharCount >= 10)
    }

    /**
     * Resolves the primary TTS locale to attempt for the given text and user settings.
     */
    fun resolveTargetLocale(text: String, userLanguageSetting: String, isBengali: Boolean): Locale {
        return if (isBengali) {
            Locale("bn", "BD")
        } else {
            when (userLanguageSetting) {
                "Bengali (Bangla)" -> Locale("bn", "BD")
                "English (UK)" -> Locale.UK
                "English (US)" -> Locale.US
                "System Default" -> Locale.getDefault()
                else -> Locale.US
            }
        }
    }

    /**
     * Cleans raw assistant text for speech synthesis:
     * - Removes SSE metadata (event:, data:, id:, retry:, etc.)
     * - Converts markdown code blocks (```...```) to brief spoken summaries like "Code block omitted for brevity" or speaks short 1-2 line snippets
     * - Removes backticks, asterisks, hash headers, and link targets
     * - Unescapes common formatting
     */
    fun sanitizeForSpeech(rawText: String, isBengali: Boolean = false): String {
        if (rawText.isBlank()) return ""

        var text = rawText

        // 1. Remove SSE protocol artifacts if present
        text = text.replace(Regex("(?m)^event:\\s*.*\$"), "")
        text = text.replace(Regex("(?m)^data:\\s*"), "")
        text = text.replace(Regex("(?m)^id:\\s*.*\$"), "")
        text = text.replace(Regex("(?m)^retry:\\s*.*\$"), "")
        text = text.replace(Regex("\\[DONE\\]"), "")

        // 2. Handle fenced code blocks: ```lang ... ```
        val codeBlockRegex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*\\n?([\\s\\S]*?)```")
        text = codeBlockRegex.replace(text) { matchResult ->
            val codeBody = matchResult.groups[1]?.value?.trim() ?: ""
            val lines = codeBody.lines().filter { it.isNotBlank() }
            if (lines.size <= 2 && codeBody.length < 80) {
                // Short inline expression or single statement: speak it cleanly
                val cleanCode = codeBody.replace(Regex("[{}();]"), " ")
                if (isBengali) " কোড: $cleanCode. " else " Code: $cleanCode. "
            } else {
                // Multi-line code block: summarize for smooth audio experience
                if (isBengali) " [এখানে একটি কোড ব্লক রয়েছে, যা পড়ার জন্য বাদ দেওয়া হয়েছে।] "
                else " [Code block omitted for brevity.] "
            }
        }

        // 3. Inline code: `val x = 10` -> val x = 10
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // 4. Markdown headers: # Header -> Header
        text = text.replace(Regex("(?m)^#{1,6}\\s+(.*)$"), "$1.")

        // 5. Markdown links: [Title](https://...) -> Title
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

        // 6. Markdown bold / italic: **bold** or *italic* -> text
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("_([^_]+)_"), "$1")
        text = text.replace(Regex("~~([^~]+)~~"), "$1")

        // 7. Bullet points and numbered lists: clean prefix
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")

        // 8. Blockquotes: > quote -> quote
        text = text.replace(Regex("(?m)^>\\s*"), "")

        // 9. Clean excessive blank lines and whitespace
        text = text.replace(Regex("\\n{2,}"), ". ")
        text = text.replace(Regex("\\n"), " ")
        text = text.replace(Regex("\\s{2,}"), " ")

        return text.trim()
    }

    /**
     * Safely chunks text for Android TextToSpeech to prevent utterance cutoffs or buffer overflows.
     * Max target chunk length is ~250 characters, prioritizing sentence delimiters (. ! ? । \n).
     */
    fun splitIntoUtteranceChunks(text: String, maxChunkSize: Int = 250): List<String> {
        val sanitized = text.trim()
        if (sanitized.isEmpty()) return emptyList()
        if (sanitized.length <= maxChunkSize) return listOf(sanitized)

        val chunks = mutableListOf<String>()
        // Split by sentence terminators: english (. ! ?), bengali (।), and semicolons
        val sentenceDelimiters = Regex("(?<=[.!?।;])\\s+")
        val sentences = sanitized.split(sentenceDelimiters).filter { it.isNotBlank() }

        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isEmpty()) continue

            if (trimmedSentence.length > maxChunkSize) {
                // If a single sentence exceeds maxChunkSize, split by words
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }

                val words = trimmedSentence.split(Regex("\\s+"))
                for (word in words) {
                    if (currentChunk.length + word.length + 1 > maxChunkSize) {
                        if (currentChunk.isNotEmpty()) {
                            chunks.add(currentChunk.toString().trim())
                            currentChunk = StringBuilder()
                        }
                    }
                    if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                    currentChunk.append(word)
                }
            } else if (currentChunk.length + trimmedSentence.length + 1 > maxChunkSize) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder(trimmedSentence)
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(trimmedSentence)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks
    }
}
