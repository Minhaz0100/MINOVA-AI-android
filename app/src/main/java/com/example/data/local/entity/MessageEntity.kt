package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val sender: String, // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timestampFormatted: String = "",
    val status: String = "SENT", // "SENT", "STREAMING", "ERROR"
    val errorMessage: String? = null,
    val attachmentType: String? = null,
    val attachmentName: String? = null,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val isPinned: Boolean = false
)
