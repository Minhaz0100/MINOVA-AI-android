package com.example.data.model

import com.example.ui.theme.ThemeMode

enum class MessageSender {
    USER,
    ASSISTANT
}

enum class MessageStatus {
    SENT,
    STREAMING,
    PENDING_BACKEND,
    ERROR
}

enum class AttachmentType {
    IMAGE,
    DOCUMENT,
    PHOTO,
    CAMERA,
    PDF,
    CODE
}

data class Attachment(
    val attachmentId: String = java.util.UUID.randomUUID().toString(),
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: AttachmentType = determineType(fileName, mimeType)
) {
    val label: String get() = fileName

    val isImage: Boolean
        get() = type == AttachmentType.IMAGE || type == AttachmentType.PHOTO || type == AttachmentType.CAMERA || mimeType.startsWith("image/")

    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }

    companion object {
        fun normalizeMimeType(rawMime: String?, fileName: String? = null): String {
            val trimmed = rawMime?.trim()?.lowercase(java.util.Locale.US)
            val extension = fileName?.substringAfterLast('.', "")?.lowercase(java.util.Locale.US) ?: ""

            return when {
                trimmed == "image/jpg" -> "image/jpeg"
                trimmed == "image/pjpeg" -> "image/jpeg"
                trimmed != null && trimmed.isNotBlank() && trimmed != "application/octet-stream" -> trimmed
                extension in setOf("jpg", "jpeg") -> "image/jpeg"
                extension == "png" -> "image/png"
                extension == "webp" -> "image/webp"
                extension == "pdf" -> "application/pdf"
                extension == "txt" -> "text/plain"
                extension == "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> trimmed ?: "application/octet-stream"
            }
        }

        fun determineType(fileName: String, mimeType: String): AttachmentType {
            val normalizedMime = normalizeMimeType(mimeType, fileName)
            val extension = fileName.substringAfterLast('.', "").lowercase(java.util.Locale.US)
            return when {
                normalizedMime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp") -> AttachmentType.IMAGE
                normalizedMime == "application/pdf" || extension == "pdf" -> AttachmentType.DOCUMENT
                else -> AttachmentType.DOCUMENT
            }
        }
    }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase(java.util.Locale.US)
}

data class Message(
    val id: String,
    val conversationId: String,
    val sender: MessageSender,
    val content: String,
    val timestamp: String,
    val status: MessageStatus = MessageStatus.SENT,
    val errorMessage: String? = null,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val attachmentType: AttachmentType? = null,
    val attachmentName: String? = null,
    val attachment: Attachment? = null,
    val attachments: List<Attachment> = if (attachment != null) listOf(attachment) else emptyList(),
    val isPinned: Boolean = false
) {
    val allAttachments: List<Attachment>
        get() = if (attachments.isNotEmpty()) attachments else if (attachment != null) listOf(attachment) else emptyList()
}

data class Conversation(
    val id: String,
    val title: String,
    val timestamp: String,
    val category: String, // "Today", "Yesterday", "Previous 7 days"
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val lastMessage: String = "",
    val responseStyle: ResponseStyle? = null,
    val responseLanguage: ResponseLanguage? = null,
    val responseFormat: ResponseFormat? = null
)

data class SuggestionCard(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String
)

data class AttachmentOption(
    val type: AttachmentType,
    val label: String,
    val subtitle: String
)

data class UserProfile(
    val name: String = "Alex Mercer",
    val email: String = "alex.mercer@minova.ai",
    val tier: String = "MINOVA PRO • Early Access",
    val memberSince: String = "Member since September 2026",
    val tokensUsed: String = "142,500 / 500,000",
    val conversationsCount: Int = 18,
    val savedSnippetsCount: Int = 34
)

enum class ResponseStyle(val displayName: String, val description: String) {
    BALANCED("Balanced", "Harmonious balance of conciseness and thoroughness"),
    CONCISE("Concise", "Short, focused responses that get straight to the point"),
    DETAILED("Detailed", "In-depth explanations with step-by-step reasoning and context"),
    EDUCATIONAL("Educational", "Teacher-style explanations, step-by-step structure, definitions, examples, and practical notes"),
    PROFESSIONAL("Professional", "Precise, structured, professional language"),
    CREATIVE("Creative", "More flexible and idea-oriented responses when the task is creative");

    companion object {
        fun fromString(value: String): ResponseStyle = entries.find {
            it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
        } ?: BALANCED
    }
}

enum class ResponseLanguage(val displayName: String, val description: String) {
    AUTO("Auto", "Respond in the language used by the user"),
    BENGALI("বাংলা", "Prefer Bengali unless technical terminology or code requires otherwise"),
    ENGLISH("English", "Respond in English without translating source code");

    companion object {
        fun fromString(value: String): ResponseLanguage = entries.find {
            it.name.equals(value, ignoreCase = true) ||
            it.displayName.equals(value, ignoreCase = true) ||
            (it == BENGALI && (value.equals("bengali", ignoreCase = true) || value.equals("bangla", ignoreCase = true) || value.startsWith("bn", ignoreCase = true))) ||
            (it == ENGLISH && (value.equals("en", ignoreCase = true) || value.startsWith("en-", ignoreCase = true)))
        } ?: AUTO
    }
}

enum class ResponseFormat(val displayName: String, val description: String) {
    AUTO("Auto", "Choose the most appropriate format naturally"),
    PLAIN("Plain", "Prioritize clean, readable paragraphs"),
    STRUCTURED("Structured", "Prefer headings, bullet points, and tables where useful"),
    STEP_BY_STEP("Step-by-step", "Organize instructional answers into numbered steps");

    companion object {
        fun fromString(value: String): ResponseFormat = entries.find {
            it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
        } ?: AUTO
    }
}

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val responseStyle: ResponseStyle = ResponseStyle.BALANCED,
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
    val responseFormat: ResponseFormat = ResponseFormat.AUTO,
    val voiceLanguage: String = "System Default",
    val voiceInputEnabled: Boolean = true,
    val voiceOutputEnabled: Boolean = true,
    val speechSpeed: Float = 1.0f,
    val enterToSend: Boolean = true,
    val showTimestamps: Boolean = false,
    val autoFocusComposer: Boolean = false,
    val showAttachmentPreview: Boolean = true,
    val aiModel: String = "MINOVA AI",
    val responsePreference: String = "Balanced",
    val codeHighlighting: Boolean = true,
    val ttsVoice: String = "Minova Azure (Natural)",
    val webSearchDefault: Boolean = false
)
