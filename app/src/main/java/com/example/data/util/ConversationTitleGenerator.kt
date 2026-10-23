package com.example.data.util

object ConversationTitleGenerator {

    private const val MAX_TITLE_LENGTH = 30
    private const val MIN_WORD_BOUNDARY = 15

    /**
     * Generates a concise, polished conversation title locally from the user's prompt.
     * Truncates at natural word boundaries where appropriate (approx 20-40 chars).
     */
    fun generateTitle(prompt: String): String {
        val cleaned = prompt
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) {
            return "New Conversation"
        }

        // If prompt is within the target length, return cleaned text directly
        if (cleaned.length <= MAX_TITLE_LENGTH) {
            return cleaned
        }

        // Search for a word break before MAX_TITLE_LENGTH
        val candidate = cleaned.take(MAX_TITLE_LENGTH)
        val lastSpaceIndex = candidate.lastIndexOf(' ')

        return if (lastSpaceIndex >= MIN_WORD_BOUNDARY) {
            candidate.substring(0, lastSpaceIndex).trimEnd(',', '.', ':', ';', '-', '?', ' ')
        } else {
            candidate.take(26).trimEnd() + "..."
        }
    }
}
