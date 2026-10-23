package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.CodeLanguage
import com.example.data.model.CodeSnippet

@Entity(
    tableName = "code_snippets",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["updatedAt"]),
        Index(value = ["language"])
    ]
)
data class CodeSnippetEntity(
    @PrimaryKey
    val id: String,
    val ownerUid: String,
    val title: String,
    val language: String,
    val code: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): CodeSnippet {
        return CodeSnippet(
            id = id,
            ownerUid = ownerUid,
            title = title,
            language = CodeLanguage.fromId(language),
            code = code,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(snippet: CodeSnippet): CodeSnippetEntity {
            return CodeSnippetEntity(
                id = snippet.id,
                ownerUid = snippet.ownerUid,
                title = snippet.title,
                language = snippet.language.id,
                code = snippet.code,
                description = snippet.description,
                createdAt = snippet.createdAt,
                updatedAt = snippet.updatedAt
            )
        }
    }
}
