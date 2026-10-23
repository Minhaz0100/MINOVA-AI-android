
package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * JSON request payload sent to POST /api/files/generate/pdf
 * and POST /api/files/generate/docx.
 */
@JsonClass(generateAdapter = true)
data class DocumentGenerateRequest(
    @Json(name = "title")
    val title: String,

    @Json(name = "subtitle")
    val subtitle: String? = null,

    @Json(name = "author")
    val author: String? = null,

    @Json(name = "sections")
    val sections: List<DocumentSectionPayload>,

    @Json(name = "blocks")
    val blocks: List<DocumentSectionPayload> = sections,

    @Json(name = "content")
    val content: String? = null
)

/**
 * Structured content block within DocumentGenerateRequest.
 */
@JsonClass(generateAdapter = true)
data class DocumentSectionPayload(
    @Json(name = "type")
    val type: String,

    @Json(name = "text")
    val text: String? = null,

    @Json(name = "heading")
    val heading: String? = null,

    @Json(name = "level")
    val level: Int? = null,

    @Json(name = "ordered")
    val ordered: Boolean? = null,

    @Json(name = "items")
    val items: List<String>? = null,

    @Json(name = "headers")
    val headers: List<String>? = null,

    @Json(name = "rows")
    val rows: List<List<String>>? = null
)

/**
 * JSON request payload sent to POST /api/files/generate/xlsx.
 */
@JsonClass(generateAdapter = true)
data class SpreadsheetGenerateRequest(
    @Json(name = "title")
    val title: String,

    @Json(name = "workbookTitle")
    val workbookTitle: String = title,

    @Json(name = "sheets")
    val sheets: List<WorksheetPayload>,

    @Json(name = "worksheets")
    val worksheets: List<WorksheetPayload> = sheets
)

/**
 * Worksheet payload within SpreadsheetGenerateRequest.
 */
@JsonClass(generateAdapter = true)
data class WorksheetPayload(
    @Json(name = "name")
    val name: String,

    @Json(name = "headers")
    val headers: List<String>,

    @Json(name = "columns")
    val columns: List<String> = headers,

    @Json(name = "rows")
    val rows: List<List<String>>
)

/**
 * Legacy image request payload for POST /api/files/generate/image.
 *
 * Retained for compatibility with the existing File Studio endpoint.
 */
@JsonClass(generateAdapter = true)
data class ImageGenerateRequest(
    @Json(name = "prompt")
    val prompt: String,

    @Json(name = "aspectRatio")
    val aspectRatio: String = "1:1"
)

/**
 * Request payload for POST /api/image/generate.
 *
 * The Cloudflare image endpoint currently accepts a prompt.
 */
@JsonClass(generateAdapter = true)
data class CloudflareImageGenerateRequest(
    @Json(name = "prompt")
    val prompt: String
)
