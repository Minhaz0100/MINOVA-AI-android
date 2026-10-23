package com.example.data.util

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * An OkHttp [RequestBody] that streams content directly from an Android content:// URI.
 * This prevents loading unnecessary copies of large files into memory.
 */
class ContentUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?,
    private val contentLength: Long = -1L
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open input stream for URI: $uri")
        inputStream.source().use { source ->
            sink.writeAll(source)
        }
    }
}
