package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CustomPromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPromptDao {
    @Query("SELECT * FROM custom_prompts ORDER BY updatedAt DESC")
    fun observeCustomPrompts(): Flow<List<CustomPromptEntity>>

    @Query("SELECT * FROM custom_prompts ORDER BY updatedAt DESC")
    suspend fun getCustomPromptsSync(): List<CustomPromptEntity>

    @Query("SELECT * FROM custom_prompts WHERE id = :id LIMIT 1")
    suspend fun getCustomPromptById(id: String): CustomPromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomPrompt(prompt: CustomPromptEntity)

    @Query("DELETE FROM custom_prompts WHERE id = :id")
    suspend fun deleteCustomPromptById(id: String)

    @Query("DELETE FROM custom_prompts")
    suspend fun deleteAllCustomPrompts()
}
