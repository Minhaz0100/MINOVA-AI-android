package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MinovaDatabase
import com.example.data.model.BuiltInPrompts
import com.example.data.model.PromptCategory
import com.example.data.model.PromptItem
import com.example.data.remote.api.MinovaApiService
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.remote.model.CloudflareImageGenerateRequest
import com.example.data.remote.model.FileAnalysisResponse
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.repository.InMemoryPromptLibraryRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.repository.PromptLibraryRepositoryImpl
import com.example.navigation.MinovaScreen
import com.example.ui.screens.PromptLibraryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromptLibraryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private class CapturingApiService : MinovaApiService {
        val capturedRequests = mutableListOf<ChatRequest>()

        override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
            capturedRequests.add(request)
            return Response.success(
                ChatResponse(
                    success = true,
                    message = "Mocked response for: ${request.message}"
                )
            )
        }

        override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
            capturedRequests.add(request)
            val sse = "data: {\"text\":\"Mocked stream response\"}\n\ndata: [DONE]\n\n"
            return Response.success(sse.toResponseBody("text/event-stream".toMediaType()))
        }

        override suspend fun analyzeFile(
            file: MultipartBody.Part,
            message: RequestBody,
            conversation: RequestBody?
        ): Response<FileAnalysisResponse> {
            return Response.success(
                FileAnalysisResponse(
                    success = true,
                    message = "Mocked file analysis"
                )
            )
        }

        override suspend fun generateCloudflareImage(
            request: CloudflareImageGenerateRequest
        ): Response<ResponseBody> {
            return Response.success(ByteArray(4).toResponseBody("image/jpeg".toMediaType()))
        }

        override suspend fun checkImageHealth(): Response<ResponseBody> {
            return Response.success("{\"healthy\":true}".toResponseBody("application/json".toMediaType()))
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `1 builtInPrompts contain Coding Study English and Writing categories with non-empty templates`() {
        val prompts = BuiltInPrompts.all
        assertEquals(12, prompts.size)

        PromptCategory.entries.forEach { category ->
            val inCategory = prompts.filter { it.category == category }
            assertEquals("Expected 3 built-in prompts in $category", 3, inCategory.size)
            inCategory.forEach { item ->
                assertTrue(item.id.isNotBlank())
                assertTrue(item.title.isNotBlank())
                assertTrue(item.description.isNotBlank())
                assertTrue(item.promptText.isNotBlank())
                assertFalse(item.isCustom)
            }
        }
    }

    @Test
    fun `2 category filtering and search query filter prompts accurately`() = runTest(testDispatcher) {
        val viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(CapturingApiService(), testDispatcher),
            conversationRepository = InMemoryConversationRepository(),
            settingsRepository = InMemorySettingsRepository(),
            promptLibraryRepository = InMemoryPromptLibraryRepository()
        )
        advanceUntilIdle()

        assertEquals(12, viewModel.filteredPrompts.value.size)

        // Filter by CODING category
        viewModel.selectPromptCategory(PromptCategory.CODING)
        assertEquals(3, viewModel.filteredPrompts.value.size)
        assertTrue(viewModel.filteredPrompts.value.all { it.category == PromptCategory.CODING })

        // Combine category with search query
        viewModel.updatePromptSearchQuery("debug")
        assertEquals(1, viewModel.filteredPrompts.value.size)
        assertEquals("builtin_coding_debugging", viewModel.filteredPrompts.value.first().id)

        // Clear category and search by tag/keyword in another category
        viewModel.selectPromptCategory(null)
        viewModel.updatePromptSearchQuery("vocabulary")
        assertEquals(1, viewModel.filteredPrompts.value.size)
        assertEquals(PromptCategory.ENGLISH, viewModel.filteredPrompts.value.first().category)
    }

    @Test
    fun `3 selecting a prompt populates chat composer without auto-sending and allows editing before send`() = runTest(testDispatcher) {
        val apiService = CapturingApiService()
        val viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(apiService, testDispatcher),
            conversationRepository = InMemoryConversationRepository(),
            settingsRepository = InMemorySettingsRepository(),
            promptLibraryRepository = InMemoryPromptLibraryRepository()
        )
        advanceUntilIdle()

        viewModel.navigateTo(MinovaScreen.PromptLibrary)
        assertEquals(MinovaScreen.PromptLibrary, viewModel.currentScreen.value)

        val emailPrompt = BuiltInPrompts.all.first { it.id == "builtin_writing_email" }
        viewModel.selectPromptFromLibrary(emailPrompt)

        // Should navigate back to Chat and populate composerText without sending
        assertTrue(viewModel.currentScreen.value is MinovaScreen.Chat)
        assertEquals(emailPrompt.promptText, viewModel.composerText.value)
        assertTrue("Selecting a prompt must not auto-send an API request", apiService.capturedRequests.isEmpty())
        assertTrue("Selecting a prompt must not create messages automatically", viewModel.activeMessages.value.isEmpty())

        // User edits template before sending
        val editedPrompt = emailPrompt.promptText.replace("[Who you are emailing]", "Engineering Lead")
        viewModel.onComposerTextChange(editedPrompt)
        assertEquals(editedPrompt, viewModel.composerText.value)

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, apiService.capturedRequests.size)
        assertEquals(editedPrompt, apiService.capturedRequests.first().message)
    }

    @Test
    fun `4 custom prompts can be saved, edited, deleted, and persist in Room database`() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MinovaDatabase.createInMemory(context)
        try {
            val repository = PromptLibraryRepositoryImpl(
                customPromptDao = database.customPromptDao()
            )

            // Initially 0 custom prompts and 12 total prompts
            assertTrue(repository.observeCustomPrompts().first().isEmpty())
            assertEquals(12, repository.observeAllPrompts().first().size)

            // Create custom prompt
            val created = repository.saveCustomPrompt(
                title = "Compose Performance Audit",
                description = "Check recomposition stability and state reads",
                promptText = "Audit this @Composable for unnecessary recompositions:\n\n[Paste composable]",
                category = PromptCategory.CODING,
                tags = listOf("compose", "performance")
            )
            assertTrue(created.isCustom)
            assertEquals("Compose Performance Audit", created.title)

            val customAfterCreate = repository.observeCustomPrompts().first()
            assertEquals(1, customAfterCreate.size)
            assertEquals(created.id, customAfterCreate.first().id)

            val allAfterCreate = repository.observeAllPrompts().first()
            assertEquals(13, allAfterCreate.size)
            assertEquals(created.id, allAfterCreate.first().id)

            // Edit custom prompt
            val updated = repository.saveCustomPrompt(
                title = "Jetpack Compose Stability Audit",
                description = "Updated description",
                promptText = "Updated prompt template text",
                category = PromptCategory.CODING,
                tags = listOf("compose", "stability"),
                existingId = created.id
            )
            assertEquals(created.id, updated.id)
            assertEquals("Jetpack Compose Stability Audit", updated.title)

            val customAfterEdit = repository.observeCustomPrompts().first()
            assertEquals(1, customAfterEdit.size)
            assertEquals("Jetpack Compose Stability Audit", customAfterEdit.first().title)
            assertEquals("Updated prompt template text", customAfterEdit.first().promptText)

            // Delete custom prompt
            repository.deleteCustomPrompt(created.id)
            assertTrue(repository.observeCustomPrompts().first().isEmpty())
            assertEquals(12, repository.observeAllPrompts().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `5 validation prevents saving custom prompt with blank title or blank template`() = runTest(testDispatcher) {
        val viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(CapturingApiService(), testDispatcher),
            conversationRepository = InMemoryConversationRepository(),
            settingsRepository = InMemorySettingsRepository(),
            promptLibraryRepository = InMemoryPromptLibraryRepository()
        )
        advanceUntilIdle()

        viewModel.saveCustomPrompt(
            title = "   ",
            description = "Desc",
            promptText = "Valid template",
            category = PromptCategory.STUDY
        )
        assertEquals("Prompt title is required.", viewModel.promptLibraryError.value)
        assertTrue(viewModel.customPrompts.value.isEmpty())

        viewModel.clearPromptLibraryError()
        assertNull(viewModel.promptLibraryError.value)

        viewModel.saveCustomPrompt(
            title = "Valid Title",
            description = "Desc",
            promptText = "   ",
            category = PromptCategory.STUDY
        )
        assertEquals("Prompt template text is required.", viewModel.promptLibraryError.value)
        assertTrue(viewModel.customPrompts.value.isEmpty())
    }

    @Test
    fun `6 MIGRATION_4_5 creates custom_prompts table and preserves existing v4 conversations messages attachments and pins`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `lastMessage` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `responseStyle` TEXT NOT NULL DEFAULT 'BALANCED',
                        `responseLanguage` TEXT NOT NULL DEFAULT 'AUTO',
                        `responseFormat` TEXT NOT NULL DEFAULT 'AUTO',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `attachmentType` TEXT,
                        `attachmentName` TEXT,
                        `attachmentUri` TEXT,
                        `attachmentMimeType` TEXT,
                        `attachmentSize` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `isPinned` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `message_attachments` (
                        `attachmentId` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `isImage` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`attachmentId`)
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory SQLite database
                .callback(callback)
                .build()
        )

        val db = helper.writableDatabase
        try {
            // Seed v4 data: conversation, pinned message with web search metadata, and attachment
            db.execSQL(
                """
                INSERT INTO `conversations` (`id`, `title`, `lastMessage`, `updatedAt`, `createdAt`, `isPinned`, `isArchived`, `responseStyle`, `responseLanguage`, `responseFormat`)
                VALUES ('conv_v4', 'Existing V4 Chat', 'Web grounded answer', 2000, 1000, 1, 0, 'DETAILED', 'ENGLISH', 'STRUCTURED')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `messages` (`id`, `conversationId`, `sender`, `content`, `timestamp`, `createdAt`, `status`, `attachmentType`, `attachmentName`, `attachmentUri`, `attachmentMimeType`, `attachmentSize`, `errorMessage`, `isPinned`)
                VALUES ('msg_v4', 'conv_v4', 'ASSISTANT', 'Web grounded answer', '10:00 AM', 1500, 'SENT', 'PDF', 'spec.pdf', 'content://spec.pdf', 'application/pdf', 4096, 'WEB_SEARCH_UNAVAILABLE: fallback note', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `message_attachments` (`attachmentId`, `messageId`, `uri`, `fileName`, `mimeType`, `sizeBytes`, `type`, `isImage`, `addedAt`)
                VALUES ('att_v4', 'msg_v4', 'content://spec.pdf', 'spec.pdf', 'application/pdf', 4096, 'PDF', 0, 1500)
                """.trimIndent()
            )

            // Execute non-destructive MIGRATION_4_5
            MinovaDatabase.MIGRATION_4_5.migrate(db)

            // Verify existing v4 conversation is preserved
            db.query("SELECT id, title, isPinned, responseStyle FROM conversations WHERE id = 'conv_v4'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("conv_v4", cursor.getString(0))
                assertEquals("Existing V4 Chat", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("DETAILED", cursor.getString(3))
            }

            // Verify existing v4 message, pin state, and error/web-search metadata are preserved
            db.query("SELECT id, content, errorMessage, isPinned FROM messages WHERE id = 'msg_v4'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("msg_v4", cursor.getString(0))
                assertEquals("Web grounded answer", cursor.getString(1))
                assertEquals("WEB_SEARCH_UNAVAILABLE: fallback note", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }

            // Verify existing v4 attachment is preserved
            db.query("SELECT attachmentId, fileName FROM message_attachments WHERE attachmentId = 'att_v4'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("att_v4", cursor.getString(0))
                assertEquals("spec.pdf", cursor.getString(1))
            }

            // Verify custom_prompts table was created and accepts inserts/queries
            db.execSQL(
                """
                INSERT INTO `custom_prompts` (`id`, `title`, `description`, `promptText`, `category`, `tagsCsv`, `createdAt`, `updatedAt`)
                VALUES ('custom_1', 'Migrated Prompt', 'Desc', 'Template text', 'CODING', 'kotlin,room', 3000, 3000)
                """.trimIndent()
            )
            db.query("SELECT id, title, category, tagsCsv FROM custom_prompts WHERE id = 'custom_1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("custom_1", cursor.getString(0))
                assertEquals("Migrated Prompt", cursor.getString(1))
                assertEquals("CODING", cursor.getString(2))
                assertEquals("kotlin,room", cursor.getString(3))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `7 PromptLibraryScreen renders categories search and populates composer when Use Prompt is clicked`() {
        Dispatchers.resetMain()
        var selectedPrompt: PromptItem? = null

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                PromptLibraryScreen(
                    prompts = BuiltInPrompts.all,
                    selectedCategory = null,
                    searchQuery = "",
                    isLoading = false,
                    errorMessage = null,
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onUsePrompt = { selectedPrompt = it },
                    onSaveCustomPrompt = { _, _, _, _, _, _ -> },
                    onDeleteCustomPrompt = {},
                    onDismissError = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("prompt_library_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_category_All").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_category_CODING").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_category_STUDY").assertIsDisplayed()

        val firstPrompt = BuiltInPrompts.all.first()
        composeTestRule.onNodeWithTag("prompt_card_${firstPrompt.id}").performClick()
        assertNotNull(selectedPrompt)
        assertEquals(firstPrompt.id, selectedPrompt?.id)
    }

    @Test
    fun `8 PromptLibraryScreen opens custom prompt dialog and saves custom prompt`() {
        Dispatchers.resetMain()
        var savedTitle: String? = null
        var savedPromptText: String? = null
        var savedCategory: PromptCategory? = null

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                PromptLibraryScreen(
                    prompts = BuiltInPrompts.all,
                    selectedCategory = null,
                    searchQuery = "",
                    isLoading = false,
                    errorMessage = null,
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onUsePrompt = {},
                    onSaveCustomPrompt = { title, _, promptText, category, _, onDone ->
                        savedTitle = title
                        savedPromptText = promptText
                        savedCategory = category
                        onDone()
                    },
                    onDeleteCustomPrompt = {},
                    onDismissError = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("add_custom_prompt_button").performClick()
        composeTestRule.onNodeWithText("New Custom Prompt").assertIsDisplayed()

        composeTestRule.onNodeWithTag("custom_prompt_title_input").performTextInput("My Custom SQL Prompt")
        composeTestRule.onNodeWithTag("custom_prompt_text_input").performTextInput("Optimize this SQL query: [Query]")
        composeTestRule.onNodeWithTag("dialog_category_STUDY").performClick()
        composeTestRule.onNodeWithTag("save_custom_prompt_button").performClick()

        assertEquals("My Custom SQL Prompt", savedTitle)
        assertEquals("Optimize this SQL query: [Query]", savedPromptText)
        assertEquals(PromptCategory.STUDY, savedCategory)
    }

    @Test
    fun `9 PromptLibraryScreen shows empty state when search has no matches`() {
        Dispatchers.resetMain()
        var clearedCategory: PromptCategory? = PromptCategory.CODING
        var clearedSearch = "nonexistent_query"

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                PromptLibraryScreen(
                    prompts = emptyList(),
                    selectedCategory = PromptCategory.CODING,
                    searchQuery = "nonexistent_query",
                    isLoading = false,
                    errorMessage = null,
                    onSelectCategory = { clearedCategory = it },
                    onSearchQueryChange = { clearedSearch = it },
                    onUsePrompt = {},
                    onSaveCustomPrompt = { _, _, _, _, _, _ -> },
                    onDeleteCustomPrompt = {},
                    onDismissError = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("prompt_library_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Matching Prompts").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_reset_filters_button").performClick()

        assertNull(clearedCategory)
        assertEquals("", clearedSearch)
    }
}
