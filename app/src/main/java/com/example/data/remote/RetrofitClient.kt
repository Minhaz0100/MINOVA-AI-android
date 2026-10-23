package com.example.data.remote

import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.api.MinovaFileStudioApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton factory and holder for Retrofit and OkHttpClient instances.
 */
object RetrofitClient {

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val streamingInterceptor: Interceptor by lazy {
        Interceptor { chain ->
            val original = chain.request()
            val isStreamRequest = original.header("Accept")?.contains("text/event-stream") == true ||
                original.url.encodedPath.contains("/stream")

            if (isStreamRequest) {
                // Explicitly disable compression and reverse proxy buffering for streaming endpoints
                val requestWithStreamHeaders = original.newBuilder()
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Accept-Encoding", "identity")
                    .header("X-Accel-Buffering", "no")
                    .build()
                chain.proceed(requestWithStreamHeaders)
            } else {
                chain.proceed(original)
            }
        }
    }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            // Use HEADERS level so streaming response bodies are not buffered in memory
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor(streamingInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var currentBaseUrl: String = NetworkConfig.MINOVA_BACKEND_BASE_URL
    private var cachedApiService: MinovaApiService? = null
    private var cachedFileStudioApiService: MinovaFileStudioApiService? = null

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val safeUrl = if (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")) {
            normalizedUrl
        } else {
            "https://YOUR_MINOVA_BACKEND_URL/"
        }

        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Obtains the configured [MinovaApiService].
     *
     * @param baseUrl Optional override if a custom backend endpoint is configured at runtime.
     */
    fun getApiService(baseUrl: String = NetworkConfig.MINOVA_BACKEND_BASE_URL): MinovaApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (cachedApiService == null || currentBaseUrl != normalizedUrl) {
            currentBaseUrl = normalizedUrl
            val retrofit = buildRetrofit(normalizedUrl)
            cachedApiService = retrofit.create(MinovaApiService::class.java)
            cachedFileStudioApiService = retrofit.create(MinovaFileStudioApiService::class.java)
        }

        return cachedApiService!!
    }

    /**
     * Obtains the configured [MinovaFileStudioApiService] using the shared backend URL and OkHttpClient.
     */
    fun getFileStudioApiService(
        baseUrl: String = NetworkConfig.MINOVA_BACKEND_BASE_URL
    ): MinovaFileStudioApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (cachedFileStudioApiService == null || currentBaseUrl != normalizedUrl) {
            currentBaseUrl = normalizedUrl
            val retrofit = buildRetrofit(normalizedUrl)
            cachedApiService = retrofit.create(MinovaApiService::class.java)
            cachedFileStudioApiService = retrofit.create(MinovaFileStudioApiService::class.java)
        }

        return cachedFileStudioApiService!!
    }
}
