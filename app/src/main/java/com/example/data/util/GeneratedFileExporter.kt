package com.example.data.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.GeneratedBinaryFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Handles saving generated binary files via Android's Storage Access Framework (SAF)
 * and sharing generated files via FileProvider.
 */
object GeneratedFileExporter {

    /**
     * Writes the binary contents of [generatedFile] to the user-selected SAF [destinationUri].
     * Only returns [Result.success] after all bytes are written and flushed.
     */
    suspend fun saveToUri(
        contentResolver: ContentResolver,
        destinationUri: Uri,
        generatedFile: GeneratedBinaryFile,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Int> = withContext(ioDispatcher) {
        try {
            if (generatedFile.bytes.isEmpty()) {
                return@withContext Result.failure(IOException("Generated file has no binary content to save."))
            }
            val outputStream = contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(
                    IOException("Unable to open output stream for selected storage location.")
                )

            outputStream.use { stream ->
                stream.write(generatedFile.bytes)
                stream.flush()
            }
            Result.success(generatedFile.bytes.size)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to save '${generatedFile.fileName}': ${e.message ?: "I/O error"}", e))
        }
    }

    /**
     * Writes [generatedFile] into the app's cache subdirectory and launches Android's share chooser
     * with a temporary read-granted [FileProvider] URI.
     */
    suspend fun shareGeneratedFile(
        context: Context,
        generatedFile: GeneratedBinaryFile,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            if (generatedFile.bytes.isEmpty()) {
                return@withContext Result.failure(IOException("Generated file has no binary content to share."))
            }
            val shareDir = File(context.cacheDir, "generated_files")
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                return@withContext Result.failure(IOException("Could not create temporary share directory."))
            }

            val safeFileName = generatedFile.fileName.ifBlank {
                "minova_export${generatedFile.mode.defaultExtension}"
            }
            val targetFile = File(shareDir, safeFileName)
            targetFile.outputStream().use { stream ->
                stream.write(generatedFile.bytes)
                stream.flush()
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, targetFile)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = generatedFile.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, safeFileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share $safeFileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Unable to share '${generatedFile.fileName}': ${e.message ?: "Share failed"}", e))
        }
    }
}
