package com.example.data.repository

/**
 * Sealed representation of a network operation outcome with clean UI-friendly errors.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(
        val userMessage: String,
        val statusCode: Int? = null,
        val errorCode: String? = null
    ) : NetworkResult<Nothing>()
}
