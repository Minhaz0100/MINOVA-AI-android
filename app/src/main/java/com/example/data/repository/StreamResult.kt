package com.example.data.repository

sealed interface StreamResult {
    data class Chunk(val text: String) : StreamResult
    data class Complete(val fullText: String) : StreamResult
    data class Error(
        val userMessage: String,
        val errorCode: String? = null,
        val statusCode: Int? = null,
        val throwable: Throwable? = null
    ) : StreamResult
}
