package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["messageId"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey
    val attachmentId: String,
    val messageId: String,
    val conversationId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
