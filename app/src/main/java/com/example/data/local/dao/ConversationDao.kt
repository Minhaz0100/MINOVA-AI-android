package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isPinned = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getPinnedConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getConversationByIdFlow(id: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET lastMessage = :lastMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastMessage(id: String, lastMessage: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET responseStyle = :style, responseLanguage = :language, responseFormat = :format, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateResponsePreferences(
        id: String,
        style: String?,
        language: String?,
        format: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' OR lastMessage LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id IN (:ids) ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getConversationsByIds(ids: List<String>): List<ConversationEntity>
}
