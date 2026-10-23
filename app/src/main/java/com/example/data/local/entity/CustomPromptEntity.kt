package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.PromptCategory
import com.example.data.model.PromptItem

@Entity(
    tableName = "custom_prompts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["updatedAt"])
    ]
)
data class CustomPromptEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val promptText: String,
    val category: String,
    val tagsCsv: String = "",
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): PromptItem {
        val parsedTags = tagsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return PromptItem(
            id = id,
            title = title,
            description = description,
            promptText = promptText,
            category = PromptCategory.fromString(category),
            tags = parsedTags,
            isCustom = true,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(item: PromptItem): CustomPromptEntity {
            return CustomPromptEntity(
                id = item.id,
                title = item.title.trim(),
                description = item.description.trim(),
                promptText = item.promptText.trim(),
                category = item.category.name,
                tagsCsv = item.tags.joinToString(",") { it.trim() },
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }
    }
}
