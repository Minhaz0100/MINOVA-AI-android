package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getAttachmentsForConversationFlow(conversationId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getAttachmentsForConversation(conversationId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId LIMIT 1")
    suspend fun getAttachmentByMessageId(messageId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE messageId = :messageId LIMIT 1")
    fun getAttachmentByMessageIdFlow(messageId: String): Flow<AttachmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("DELETE FROM attachments WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("SELECT * FROM attachments")
    fun getAllAttachmentsFlow(): Flow<List<AttachmentEntity>>
}
