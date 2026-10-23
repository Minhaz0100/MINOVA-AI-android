package com.example.data.repository

import com.example.data.local.dao.CustomPromptDao
import com.example.data.local.entity.CustomPromptEntity
import com.example.data.model.BuiltInPrompts
import com.example.data.model.PromptCategory
import com.example.data.model.PromptItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Repository interface for managing built-in and custom prompts in the Prompt Library.
 */
interface PromptLibraryRepository {
    fun observeAllPrompts(): Flow<List<PromptItem>>
    fun observeCustomPrompts(): Flow<List<PromptItem>>
    fun getBuiltInPrompts(): List<PromptItem>

    suspend fun saveCustomPrompt(
        title: String,
        description: String,
        promptText: String,
        category: PromptCategory,
        tags: List<String> = emptyList(),
        existingId: String? = null
    ): PromptItem

    suspend fun deleteCustomPrompt(promptId: String)
    suspend fun clearAllCustomPrompts()
}

/**
 * Room-backed implementation of [PromptLibraryRepository] for persistent custom prompts.
 */
class PromptLibraryRepositoryImpl(
    private val customPromptDao: CustomPromptDao
) : PromptLibraryRepository {

    override fun observeAllPrompts(): Flow<List<PromptItem>> {
        return customPromptDao.observeCustomPrompts().map { customEntities ->
            val customItems = customEntities.map { it.toDomain() }
            customItems + BuiltInPrompts.all
        }
    }

    override fun observeCustomPrompts(): Flow<List<PromptItem>> {
        return customPromptDao.observeCustomPrompts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getBuiltInPrompts(): List<PromptItem> = BuiltInPrompts.all

    override suspend fun saveCustomPrompt(
        title: String,
        description: String,
        promptText: String,
        category: PromptCategory,
        tags: List<String>,
        existingId: String?
    ): PromptItem {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanPromptText = promptText.trim()

        require(cleanTitle.isNotEmpty()) { "Prompt title cannot be empty" }
        require(cleanPromptText.isNotEmpty()) { "Prompt template text cannot be empty" }

        val now = System.currentTimeMillis()
        val existingEntity = existingId?.let { customPromptDao.getCustomPromptById(it) }

        val item = PromptItem(
            id = existingEntity?.id ?: existingId ?: "custom_${UUID.randomUUID()}",
            title = cleanTitle,
            description = cleanDescription.ifEmpty { "Custom ${category.displayName} prompt" },
            promptText = cleanPromptText,
            category = category,
            tags = if (tags.isEmpty()) listOf("custom", category.displayName.lowercase()) else tags,
            isCustom = true,
            createdAt = existingEntity?.createdAt ?: now,
            updatedAt = now
        )

        customPromptDao.upsertCustomPrompt(CustomPromptEntity.fromDomain(item))
        return item
    }

    override suspend fun deleteCustomPrompt(promptId: String) {
        customPromptDao.deleteCustomPromptById(promptId)
    }

    override suspend fun clearAllCustomPrompts() {
        customPromptDao.deleteAllCustomPrompts()
    }
}

/**
 * In-memory implementation of [PromptLibraryRepository] for unit tests and previews.
 */
class InMemoryPromptLibraryRepository : PromptLibraryRepository {

    private val customPromptsState = MutableStateFlow<List<PromptItem>>(emptyList())

    override fun observeAllPrompts(): Flow<List<PromptItem>> {
        return customPromptsState.map { custom ->
            custom.sortedByDescending { it.updatedAt } + BuiltInPrompts.all
        }
    }

    override fun observeCustomPrompts(): Flow<List<PromptItem>> {
        return customPromptsState.map { custom ->
            custom.sortedByDescending { it.updatedAt }
        }
    }

    override fun getBuiltInPrompts(): List<PromptItem> = BuiltInPrompts.all

    override suspend fun saveCustomPrompt(
        title: String,
        description: String,
        promptText: String,
        category: PromptCategory,
        tags: List<String>,
        existingId: String?
    ): PromptItem {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanPromptText = promptText.trim()

        require(cleanTitle.isNotEmpty()) { "Prompt title cannot be empty" }
        require(cleanPromptText.isNotEmpty()) { "Prompt template text cannot be empty" }

        val now = System.currentTimeMillis()
        val current = customPromptsState.value
        val existing = existingId?.let { id -> current.find { it.id == id } }

        val item = PromptItem(
            id = existing?.id ?: existingId ?: "custom_${UUID.randomUUID()}",
            title = cleanTitle,
            description = cleanDescription.ifEmpty { "Custom ${category.displayName} prompt" },
            promptText = cleanPromptText,
            category = category,
            tags = if (tags.isEmpty()) listOf("custom", category.displayName.lowercase()) else tags,
            isCustom = true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        customPromptsState.update { list ->
            val filtered = list.filterNot { it.id == item.id }
            listOf(item) + filtered
        }

        return item
    }

    override suspend fun deleteCustomPrompt(promptId: String) {
        customPromptsState.update { list -> list.filterNot { it.id == promptId } }
    }

    override suspend fun clearAllCustomPrompts() {
        customPromptsState.value = emptyList()
    }
}
