package com.example.data.remote

/**
 * Central configuration for the MINOVA AI network layer.
 *
 * IMPORTANT SECURITY:
 * - The Android application communicates ONLY with the MINOVA backend server.
 * - No Gemini API keys or sensitive AI provider secrets are stored in the client application.
 * - The MINOVA backend handles authentication, rate limiting, and Gemini API delegation.
 */
object NetworkConfig {

    /**
     * Production MINOVA AI backend base URL.
     *
     * IMPORTANT:
     * Keep the trailing slash because Retrofit requires the base URL
     * to end with a slash.
     */
    const val MINOVA_BACKEND_BASE_URL: String =
        "https://minova-ai-backend.ai.studio/"

    // Network timeout settings
    const val CONNECT_TIMEOUT_SECONDS: Long = 30L
    const val READ_TIMEOUT_SECONDS: Long = 60L
    const val WRITE_TIMEOUT_SECONDS: Long = 60L

    /**
     * Checks whether the MINOVA backend URL is properly configured.
     */
    val isBackendConfigured: Boolean
        get() = MINOVA_BACKEND_BASE_URL.isNotBlank() &&
                !MINOVA_BACKEND_BASE_URL.contains(
                    "YOUR_MINOVA_BACKEND_URL",
                    ignoreCase = true
                ) &&
                (
                    MINOVA_BACKEND_BASE_URL.startsWith("http://") ||
                    MINOVA_BACKEND_BASE_URL.startsWith("https://")
                )
}
