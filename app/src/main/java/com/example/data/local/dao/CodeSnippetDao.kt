package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CodeSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeSnippetDao {

    @Query("SELECT * FROM code_snippets WHERE ownerUid = :ownerUid ORDER BY updatedAt DESC")
    fun observeSnippetsForOwner(ownerUid: String): Flow<List<CodeSnippetEntity>>

    @Query("SELECT * FROM code_snippets WHERE id = :id LIMIT 1")
    suspend fun getSnippetById(id: String): CodeSnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnippet(snippet: CodeSnippetEntity)

    @Query("UPDATE code_snippets SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameSnippet(
        id: String,
        newTitle: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM code_snippets WHERE id = :id")
    suspend fun deleteSnippetById(id: String)

    @Query("DELETE FROM code_snippets WHERE ownerUid = :ownerUid")
    suspend fun deleteAllSnippetsForOwner(ownerUid: String)
}
