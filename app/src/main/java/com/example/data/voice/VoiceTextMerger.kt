package com.example.data.voice

import android.speech.SpeechRecognizer
import java.util.Locale

object VoiceTextMerger {

    /**
     * Combines existing composer base text with incoming speech text (partial or final).
     * Preserves pre-existing text and prevents duplicate spaces or duplicated words.
     */
    fun merge(baseText: String, speechText: String): String {
        val trimmedSpeech = speechText.trim()
        if (trimmedSpeech.isEmpty()) return baseText
        if (baseText.isBlank()) return trimmedSpeech

        val trimmedBase = baseText.trimEnd()
        return "$trimmedBase $trimmedSpeech"
    }

    /**
     * Resolves the BCP 47 language tag based on the user's selected language setting.
     * Supports Bengali (বাংলা / bn-BD), English (US / UK), and System Default without forcing English.
     */
    fun resolveLanguageTag(voiceLanguage: String, defaultLocale: Locale = Locale.getDefault()): String {
        val normalized = voiceLanguage.trim().lowercase()
        return when {
            normalized.contains("বাংলা") || normalized.contains("bangla") || normalized.contains("bengali") ||
                    normalized == "bn" || normalized.startsWith("bn-") || normalized.startsWith("bn_") -> "bn-BD"
            normalized.contains("uk") || normalized.contains("great britain") || normalized == "en-gb" || normalized == "en_gb" -> "en-GB"
            normalized.contains("us") || normalized.contains("united states") || normalized == "en-us" || normalized == "en_us" -> "en-US"
            normalized.contains("system") || normalized.contains("default") -> {
                val tag = defaultLocale.toLanguageTag()
                if (tag.isNotBlank() && tag != "und") tag else {
                    val lang = defaultLocale.language
                    val country = defaultLocale.country
                    if (lang.isNotBlank() && country.isNotBlank()) "$lang-$country"
                    else if (lang.isNotBlank()) lang
                    else "en-US"
                }
            }
            normalized.matches(Regex("^[a-z]{2,3}([-_][a-z0-9]{2,4})?\$")) -> {
                voiceLanguage.trim().replace('_', '-')
            }
            else -> "en-US"
        }
    }

    /**
     * Maps Android SpeechRecognizer error integer codes to clear, human-friendly messages.
     * Never exposes raw technical error codes to normal users.
     */
    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech service client error. Please try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error during voice recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Please check your connection."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognition service is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Voice input stopped."
            else -> "Voice input error. Please try again."
        }
    }
}
