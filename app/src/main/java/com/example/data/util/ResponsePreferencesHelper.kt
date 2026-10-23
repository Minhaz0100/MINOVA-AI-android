package com.example.data.util

import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle

/**
 * Encapsulates directive generation for user-selected AI response controls.
 * Prepares guidelines for the backend model without exposing internal prompts to the UI.
 */
object ResponsePreferencesHelper {

    /**
     * Builds a concise instruction directive to guide model generation according to user preferences.
     * Returns null if all settings are at their default ("Auto" / "Balanced"), adding zero payload overhead.
     */
    fun buildSystemDirective(
        style: ResponseStyle = ResponseStyle.BALANCED,
        language: ResponseLanguage = ResponseLanguage.AUTO,
        format: ResponseFormat = ResponseFormat.AUTO
    ): String? {
        val directives = mutableListOf<String>()

        // 1. Response Style
        when (style) {
            ResponseStyle.BALANCED -> {
                // Default: balanced, harmonious answer with appropriate detail.
            }
            ResponseStyle.CONCISE -> {
                directives.add("Style: Provide concise, short, and direct answers with minimal unnecessary explanation.")
            }
            ResponseStyle.DETAILED -> {
                directives.add("Style: Provide detailed, deeper explanations with useful examples and context.")
            }
            ResponseStyle.EDUCATIONAL -> {
                directives.add("Style: Provide educational, teacher-style explanations with step-by-step structure, definitions, examples, and practical notes.")
            }
            ResponseStyle.PROFESSIONAL -> {
                directives.add("Style: Use precise, structured, and professional language.")
            }
            ResponseStyle.CREATIVE -> {
                directives.add("Style: Provide flexible, imaginative, and idea-oriented responses when the task is creative.")
            }
        }

        // 2. Response Language
        when (language) {
            ResponseLanguage.AUTO -> {
                // Default: respond in the language used by the user.
            }
            ResponseLanguage.BENGALI -> {
                directives.add("Language: Prefer Bengali (বাংলা) unless technical terminology or programming code requires otherwise. Do not translate source code or syntax.")
            }
            ResponseLanguage.ENGLISH -> {
                directives.add("Language: Respond in English. Do not translate source code.")
            }
        }

        // 3. Response Format
        when (format) {
            ResponseFormat.AUTO -> {
                // Default: choose the most appropriate format naturally.
            }
            ResponseFormat.PLAIN -> {
                directives.add("Format: Prioritize readable paragraphs without unnecessary formatting on simple answers.")
            }
            ResponseFormat.STRUCTURED -> {
                directives.add("Format: Structure response using headings, bullet points, and tables where useful.")
            }
            ResponseFormat.STEP_BY_STEP -> {
                directives.add("Format: Organize instructional or procedural answers into clear numbered steps.")
            }
        }

        if (directives.isEmpty()) return null
        return "[Response Guidelines: ${directives.joinToString("; ")}]"
    }
}
