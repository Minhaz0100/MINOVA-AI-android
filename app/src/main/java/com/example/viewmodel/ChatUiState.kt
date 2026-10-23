package com.example.viewmodel

/**
 * UI State representing the current status of the chat network interaction.
 */
sealed interface ChatUiState {
    object Idle : ChatUiState
    object Loading : ChatUiState
    data class Uploading(val progressText: String = "Uploading...") : ChatUiState
    data class Analyzing(val statusText: String = "Analyzing...") : ChatUiState
    data class Streaming(val partialResponse: String) : ChatUiState
    data class Success(val response: String) : ChatUiState
    data class Error(val message: String, val canRetry: Boolean = true) : ChatUiState
}

val ChatUiState.isStreaming: Boolean
    get() = this is ChatUiState.Loading || this is ChatUiState.Streaming || this is ChatUiState.Uploading || this is ChatUiState.Analyzing

val ChatUiState.statusMessage: String
    get() = when (this) {
        is ChatUiState.Uploading -> progressText
        is ChatUiState.Analyzing -> statusText
        is ChatUiState.Loading -> "Thinking..."
        else -> ""
    }
