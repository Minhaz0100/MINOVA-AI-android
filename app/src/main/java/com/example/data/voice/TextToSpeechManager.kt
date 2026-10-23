package com.example.data.voice

import kotlinx.coroutines.flow.StateFlow

interface TextToSpeechManager {
    val ttsState: StateFlow<TtsState>
    val activeMessageId: StateFlow<String?>
    val isSpeaking: StateFlow<Boolean>

    /**
     * Speaks the assistant message text.
     * If this message is already speaking, stops it.
     * If another message was speaking, stops that first and starts speaking this one.
     */
    fun speak(
        messageId: String,
        text: String,
        preferredLanguage: String = "English (US)",
        onError: (String) -> Unit = {}
    )

    /**
     * Sets speech rate / playback speed (e.g. 0.75f, 1.0f, 1.25f, 1.5f, 1.75f).
     */
    fun setSpeechRate(rate: Float)

    /**
     * Stops any currently active speech playback immediately.
     */
    fun stop()

    /**
     * Shuts down the TTS engine and cleans up memory.
     */
    fun shutdown()
}
