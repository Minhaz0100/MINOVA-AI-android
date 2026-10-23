package com.example.data.voice

import kotlinx.coroutines.flow.StateFlow

interface VoiceInputManager {
    val voiceState: StateFlow<VoiceState>
    val errorMessage: StateFlow<String?>

    fun isRecognitionAvailable(): Boolean

    fun startListening(
        baseText: String,
        language: String,
        onTextUpdated: (mergedText: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onComplete: (finalMergedText: String) -> Unit = {}
    )

    fun stopListening()
    fun cancel()
    fun destroy()
}
