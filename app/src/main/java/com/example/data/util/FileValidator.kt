package com.example.data.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import java.io.InputStream
import java.util.Locale

sealed interface FileValidationResult {
    data class Success(val attachment: Attachment) : FileValidationResult
    data class Error(val message: String) : FileValidationResult
}

sealed interface MultiFileValidationResult {
    data class Success(
        val validAttachments: List<Attachment>,
        val errorMessages: List<String> = emptyList()
    ) : MultiFileValidationResult
}

object FileValidator {

    const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024L * 1024L // 10 MB (10,485,760 bytes)
    const val MAX_ATTACHMENTS_PER_MESSAGE: Int = 5

    val SUPPORTED_IMAGE_EXTENSIONS = setOf(
        "jpg",
        "jpeg",
        "png",
        "webp"
    )

    val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(
        "pdf",
        "txt",
        "docx"
    )

    val SUPPORTED_EXTENSIONS = SUPPORTED_IMAGE_EXTENSIONS + SUPPORTED_DOCUMENT_EXTENSIONS

    val SUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/pjpeg",
        "image/png",
        "image/webp"
    )

    val SUPPORTED_DOCUMENT_MIME_TYPES = setOf(
        "application/pdf",
        "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/docx",
        "application/msword"
    )

    val SUPPORTED_MIME_TYPES = SUPPORTED_IMAGE_MIME_TYPES + SUPPORTED_DOCUMENT_MIME_TYPES

    /**
     * Normalizes and cleans MIME types (e.g. image/jpg -> image/jpeg).
     */
    fun normalizeMimeType(rawMime: String?, fileName: String? = null): String {
        return Attachment.normalizeMimeType(rawMime, fileName)
    }

    /**
     * Validates a file from a content:// or file:// URI according to MINOVA specifications:
     * 1. Check readability and access permissions
     * 2. Validate non-empty file
     * 3. Validate file extension and MIME type
     * 4. Validate file size <= 10 MB
     */
    fun validate(context: Context, uri: Uri): FileValidationResult {
        // 1. Check readability
        var testFileSize: Long = 0L
        var streamOpened = false
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileValidationResult.Error("Unable to read this file. Please choose another file.")
            inputStream.use { stream ->
                streamOpened = true
                try {
                    testFileSize = stream.available().toLong()
                } catch (_: Throwable) {
                    testFileSize = 0L
                }
            }
        } catch (_: Throwable) {
            return FileValidationResult.Error("Unable to read this file. Please choose another file.")
        }

        if (!streamOpened) {
            return FileValidationResult.Error("Unable to read this file. Please choose another file.")
        }

        // 2. Query file metadata (Name and Size) from ContentResolver
        var fileName: String? = null
        var fileSize: Long = -1L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Throwable) {
            // Fall back to stream inspection
        }

        // Fallback for file name if not returned by cursor
        if (fileName.isNullOrBlank()) {
            fileName = uri.lastPathSegment ?: "attached_file"
        }

        // Fallback for file size if not returned by cursor
        if (fileSize <= 0) {
            fileSize = testFileSize
        }

        // Check for empty file
        if (fileSize == 0L && testFileSize == 0L) {
            return FileValidationResult.Error("File is empty. Please select a valid file.")
        }

        // 3. Resolve and normalize MIME type and extension
        var rawMime = try {
            context.contentResolver.getType(uri)
        } catch (_: Throwable) {
            null
        }

        var extension = fileName!!.substringAfterLast('.', "").lowercase(Locale.US)

        // Handle case where filename has no extension (common in Android Photo Picker)
        if (extension.isEmpty() || extension == fileName) {
            val inferredExt = when {
                rawMime?.startsWith("image/png") == true -> "png"
                rawMime?.startsWith("image/webp") == true -> "webp"
                rawMime?.startsWith("image/") == true -> "jpg"
                rawMime == "application/pdf" -> "pdf"
                rawMime == "text/plain" -> "txt"
                rawMime?.contains("wordprocessingml") == true -> "docx"
                else -> ""
            }
            if (inferredExt.isNotEmpty()) {
                extension = inferredExt
                if (!fileName!!.endsWith(".$inferredExt")) {
                    fileName = "$fileName.$inferredExt"
                }
            }
        }

        val normalizedMime = normalizeMimeType(rawMime, fileName)

        // 4. Determine if image or document
        val isImage = normalizedMime.startsWith("image/") ||
                normalizedMime in SUPPORTED_IMAGE_MIME_TYPES ||
                extension in SUPPORTED_IMAGE_EXTENSIONS

        val isDocument = normalizedMime in SUPPORTED_DOCUMENT_MIME_TYPES ||
                extension in SUPPORTED_DOCUMENT_EXTENSIONS

        if (!isImage && !isDocument) {
            val extDisplay = if (extension.isNotBlank()) " ($extension)" else ""
            return FileValidationResult.Error("Unsupported file type$extDisplay. Please select a PDF, DOCX, TXT, JPG, PNG, or WEBP file.")
        }

        // 5. Check size limit: <= 10 MB
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return FileValidationResult.Error("File size must be 10 MB or less.")
        }

        val attachmentType = if (isImage) AttachmentType.IMAGE else AttachmentType.DOCUMENT

        val attachment = Attachment(
            uri = uri.toString(),
            fileName = fileName!!,
            mimeType = normalizedMime,
            sizeBytes = fileSize,
            type = attachmentType
        )

        return FileValidationResult.Success(attachment)
    }

    /**
     * Validates a batch of URIs, enforcing the 5-attachment maximum limit and individual file validations.
     * Keeps valid files while reporting user-friendly errors for any rejected files.
     */
    fun validateMultiple(
        context: Context,
        uris: List<Uri>,
        currentCount: Int = 0
    ): MultiFileValidationResult {
        val validAttachments = mutableListOf<Attachment>()
        val errorMessages = mutableListOf<String>()

        val remainingSlots = MAX_ATTACHMENTS_PER_MESSAGE - currentCount
        if (remainingSlots <= 0) {
            return MultiFileValidationResult.Success(
                validAttachments = emptyList(),
                errorMessages = listOf("Maximum 5 attachments allowed per message.")
            )
        }

        if (uris.size > remainingSlots) {
            errorMessages.add("Maximum 5 attachments allowed per message. Only the first $remainingSlots file(s) were processed.")
        }

        val urisToProcess = uris.take(remainingSlots)
        for (uri in urisToProcess) {
            when (val res = validate(context, uri)) {
                is FileValidationResult.Success -> validAttachments.add(res.attachment)
                is FileValidationResult.Error -> errorMessages.add(res.message)
            }
        }

        return MultiFileValidationResult.Success(
            validAttachments = validAttachments,
            errorMessages = errorMessages
        )
    }

    /**
     * Safely checks whether the given URI string can still be accessed and opened.
     */
    fun isUriAccessible(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
