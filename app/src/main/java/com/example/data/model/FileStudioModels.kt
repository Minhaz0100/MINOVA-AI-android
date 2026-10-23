package com.example.data.model

import java.util.UUID

/**
 * Supported modes in AI File Studio.
 */
enum class FileStudioMode(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val defaultExtension: String,
    val defaultMimeType: String,
    val endpointPath: String
) {
    PDF(
        id = "pdf",
        displayName = "PDF Document",
        shortLabel = "PDF",
        defaultExtension = ".pdf",
        defaultMimeType = "application/pdf",
        endpointPath = "/api/files/generate/pdf"
    ),
    DOCX(
        id = "docx",
        displayName = "Word (DOCX)",
        shortLabel = "Word (DOCX)",
        defaultExtension = ".docx",
        defaultMimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        endpointPath = "/api/files/generate/docx"
    ),
    XLSX(
        id = "xlsx",
        displayName = "Excel (XLSX)",
        shortLabel = "Excel (XLSX)",
        defaultExtension = ".xlsx",
        defaultMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        endpointPath = "/api/files/generate/xlsx"
    ),
    IMAGE(
        id = "image",
        displayName = "AI Image",
        shortLabel = "Image",
        defaultExtension = ".jpg",
        defaultMimeType = "image/jpeg",
        endpointPath = "/api/image/generate"
    )
}

/**
 * Supported aspect ratios for Image mode.
 */
enum class ImageAspectRatio(
    val value: String,
    val label: String
) {
    SQUARE_1_1("1:1", "1:1 Square"),
    LANDSCAPE_16_9("16:9", "16:9 Landscape"),
    PORTRAIT_9_16("9:16", "9:16 Portrait"),
    STANDARD_4_3("4:3", "4:3 Standard"),
    VERTICAL_3_4("3:4", "3:4 Vertical")
}

/**
 * Structured content block types for PDF and Word (DOCX) generation.
 */
enum class DocumentBlockType(
    val apiType: String,
    val displayName: String
) {
    HEADING("heading", "Heading"),
    PARAGRAPH("paragraph", "Paragraph"),
    LIST("list", "List"),
    TABLE("table", "Table")
}

/**
 * Editable structured content block for PDF and Word (DOCX) modes.
 */
data class DocumentContentBlock(
    val id: String = "block_${UUID.randomUUID()}",
    val type: DocumentBlockType,
    val headingLevel: Int = 1,
    val text: String = "",
    val listOrdered: Boolean = false,
    val listItems: List<String> = listOf(""),
    val tableHeaders: List<String> = listOf("Column 1", "Column 2"),
    val tableRows: List<List<String>> = listOf(listOf("", ""))
)

/**
 * Editable worksheet model for Excel (XLSX) mode.
 */
data class SpreadsheetWorksheet(
    val id: String = "sheet_${UUID.randomUUID()}",
    val name: String = "Sheet1",
    val headers: List<String> = listOf("Item", "Category", "Value"),
    val rows: List<List<String>> = listOf(
        listOf("Q1 Revenue", "Finance", "12500"),
        listOf("Q2 Revenue", "Finance", "15800")
    )
)

/**
 * Represents a generated binary file received from the MINOVA backend.
 * Kept in memory until the user saves it via Storage Access Framework or shares it.
 */
data class GeneratedBinaryFile(
    val mode: FileStudioMode,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val generatedAtMillis: Long = System.currentTimeMillis()
) {
    val isImage: Boolean
        get() = mode == FileStudioMode.IMAGE || mimeType.startsWith("image/", ignoreCase = true)

    val sizeBytes: Int
        get() = bytes.size

    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedBinaryFile) return false
        return mode == other.mode &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes) &&
            generatedAtMillis == other.generatedAtMillis
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + generatedAtMillis.hashCode()
        return result
    }
}
