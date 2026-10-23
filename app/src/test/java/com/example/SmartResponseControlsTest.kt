package com.example

import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.model.SettingsState
import com.example.data.remote.model.ChatRequest
import com.example.data.remote.model.ChatResponse
import com.example.data.remote.model.FileAnalysisResponse
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.util.ResponsePreferencesHelper
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
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SmartResponseControlsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var conversationRepository: InMemoryConversationRepository
    private lateinit var fakeApi: CapturingMinovaApiService
    private lateinit var viewModel: MinovaViewModel

    class CapturingMinovaApiService : FakeMinovaApiService() {
        var lastChatRequest: ChatRequest? = null
        var lastFileMessage: String? = null

        override suspend fun sendChat(request: ChatRequest): Response<ChatResponse> {
            lastChatRequest = request
            return Response.success(ChatResponse(success = true, message = "Generated answer"))
        }

        override suspend fun sendChatStream(request: ChatRequest): Response<ResponseBody> {
            lastChatRequest = request
            return Response.success("data: {\"text\":\"Streamed chunk\"}\n\ndata: [DONE]\n\n".toResponseBody("text/event-stream".toMediaType()))
        }

        override suspend fun analyzeFile(
            file: MultipartBody.Part,
            message: RequestBody,
            conversation: RequestBody?
        ): Response<FileAnalysisResponse> {
            val buffer = okio.Buffer()
            message.writeTo(buffer)
            lastFileMessage = buffer.readUtf8()
            return Response.success(FileAnalysisResponse(success = true, message = "File analyzed successfully"))
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = InMemorySettingsRepository()
        conversationRepository = InMemoryConversationRepository()
        fakeApi = CapturingMinovaApiService()
        viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(apiService = fakeApi, ioDispatcher = testDispatcher),
            conversationRepository = conversationRepository,
            appContext = null,
            settingsRepository = settingsRepository,
            voiceInputManager = null,
            textToSpeechManager = null
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. ALL RESPONSE STYLE VALUES
    @Test
    fun testAllResponseStyleValuesAndDisplay() {
        val styles = ResponseStyle.entries
        assertEquals(6, styles.size)

        assertEquals("Balanced", ResponseStyle.BALANCED.displayName)
        assertEquals("Concise", ResponseStyle.CONCISE.displayName)
        assertEquals("Detailed", ResponseStyle.DETAILED.displayName)
        assertEquals("Educational", ResponseStyle.EDUCATIONAL.displayName)
        assertEquals("Professional", ResponseStyle.PROFESSIONAL.displayName)
        assertEquals("Creative", ResponseStyle.CREATIVE.displayName)

        styles.forEach { style ->
            val parsed = ResponseStyle.fromString(style.name)
            assertEquals(style, parsed)
            val parsedFromDisplay = ResponseStyle.fromString(style.displayName)
            assertEquals(style, parsedFromDisplay)
        }
    }

    // 2. ALL RESPONSE LANGUAGE VALUES
    @Test
    fun testAllResponseLanguageValuesAndDisplay() {
        val languages = ResponseLanguage.entries
        assertEquals(3, languages.size)

        assertEquals("Auto", ResponseLanguage.AUTO.displayName)
        assertEquals("বাংলা", ResponseLanguage.BENGALI.displayName)
        assertEquals("English", ResponseLanguage.ENGLISH.displayName)

        assertEquals(ResponseLanguage.BENGALI, ResponseLanguage.fromString("বাংলা"))
        assertEquals(ResponseLanguage.BENGALI, ResponseLanguage.fromString("bengali"))
        assertEquals(ResponseLanguage.BENGALI, ResponseLanguage.fromString("bangla"))
        assertEquals(ResponseLanguage.ENGLISH, ResponseLanguage.fromString("English"))
        assertEquals(ResponseLanguage.ENGLISH, ResponseLanguage.fromString("en"))
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.fromString("Auto"))
    }

    // 3. ALL RESPONSE FORMAT VALUES
    @Test
    fun testAllResponseFormatValuesAndDisplay() {
        val formats = ResponseFormat.entries
        assertEquals(4, formats.size)

        assertEquals("Auto", ResponseFormat.AUTO.displayName)
        assertEquals("Plain", ResponseFormat.PLAIN.displayName)
        assertEquals("Structured", ResponseFormat.STRUCTURED.displayName)
        assertEquals("Step-by-step", ResponseFormat.STEP_BY_STEP.displayName)

        formats.forEach { format ->
            val parsed = ResponseFormat.fromString(format.name)
            assertEquals(format, parsed)
            val parsedFromDisplay = ResponseFormat.fromString(format.displayName)
            assertEquals(format, parsedFromDisplay)
        }
    }

    // 4. DEFAULT VALUES AND DATASTORE PERSISTENCE
    @Test
    fun testDefaultValuesAndSettingsPersistence() = runTest {
        val defaults = settingsRepository.getSettings()
        assertEquals(ResponseStyle.BALANCED, defaults.responseStyle)
        assertEquals(ResponseLanguage.AUTO, defaults.responseLanguage)
        assertEquals(ResponseFormat.AUTO, defaults.responseFormat)

        // Update settings in repository
        settingsRepository.updateResponseStyle(ResponseStyle.EDUCATIONAL)
        settingsRepository.updateResponseLanguage(ResponseLanguage.BENGALI)
        settingsRepository.updateResponseFormat(ResponseFormat.STEP_BY_STEP)

        val updated = settingsRepository.getSettings()
        assertEquals(ResponseStyle.EDUCATIONAL, updated.responseStyle)
        assertEquals(ResponseLanguage.BENGALI, updated.responseLanguage)
        assertEquals(ResponseFormat.STEP_BY_STEP, updated.responseFormat)
    }

    // 5. CONVERSATION-SPECIFIC PREFERENCE PERSISTENCE
    @Test
    fun testConversationSpecificPreferencePersistence() = runTest {
        val conv = conversationRepository.createConversation(
            id = "conv_spec_1",
            title = "Algorithm Design",
            responseStyle = ResponseStyle.PROFESSIONAL,
            responseLanguage = ResponseLanguage.ENGLISH,
            responseFormat = ResponseFormat.STRUCTURED
        )

        assertEquals(ResponseStyle.PROFESSIONAL, conv.responseStyle)
        assertEquals(ResponseLanguage.ENGLISH, conv.responseLanguage)
        assertEquals(ResponseFormat.STRUCTURED, conv.responseFormat)

        val retrieved = conversationRepository.getConversationById("conv_spec_1")
        assertNotNull(retrieved)
        assertEquals(ResponseStyle.PROFESSIONAL, retrieved?.responseStyle)
        assertEquals(ResponseLanguage.ENGLISH, retrieved?.responseLanguage)
        assertEquals(ResponseFormat.STRUCTURED, retrieved?.responseFormat)

        // Update preferences for this conversation
        conversationRepository.updateConversationPreferences(
            id = "conv_spec_1",
            style = ResponseStyle.CREATIVE,
            language = ResponseLanguage.BENGALI,
            format = ResponseFormat.PLAIN
        )

        val updated = conversationRepository.getConversationById("conv_spec_1")
        assertEquals(ResponseStyle.CREATIVE, updated?.responseStyle)
        assertEquals(ResponseLanguage.BENGALI, updated?.responseLanguage)
        assertEquals(ResponseFormat.PLAIN, updated?.responseFormat)
    }

    // 6. NEW CONVERSATION DEFAULTS AND CONVERSATION SWITCHING
    @Test
    fun testNewConversationDefaultsAndSwitching() = runTest {
        // Set global settings to Educational, English, Step-by-step
        viewModel.updateSettings(
            SettingsState(
                responseStyle = ResponseStyle.EDUCATIONAL,
                responseLanguage = ResponseLanguage.ENGLISH,
                responseFormat = ResponseFormat.STEP_BY_STEP
            )
        )
        advanceUntilIdle()

        // 1. New conversation inherits global defaults
        viewModel.startNewChat()
        advanceUntilIdle()
        assertEquals(ResponseStyle.EDUCATIONAL, viewModel.activeResponseStyle.value)
        assertEquals(ResponseLanguage.ENGLISH, viewModel.activeResponseLanguage.value)
        assertEquals(ResponseFormat.STEP_BY_STEP, viewModel.activeResponseFormat.value)

        // 2. Create another conversation with custom overrides
        conversationRepository.createConversation(
            id = "conv_custom",
            title = "Bengali Code Guide",
            responseStyle = ResponseStyle.CONCISE,
            responseLanguage = ResponseLanguage.BENGALI,
            responseFormat = ResponseFormat.PLAIN
        )

        // 3. Switch to custom conversation -> restores saved overrides
        viewModel.selectConversation("conv_custom")
        advanceUntilIdle()
        assertEquals(ResponseStyle.CONCISE, viewModel.activeResponseStyle.value)
        assertEquals(ResponseLanguage.BENGALI, viewModel.activeResponseLanguage.value)
        assertEquals(ResponseFormat.PLAIN, viewModel.activeResponseFormat.value)

        // 4. Create conversation with no overrides (null) -> falls back to current global defaults
        conversationRepository.createConversation(
            id = "conv_null_overrides",
            title = "Standard Chat",
            responseStyle = null,
            responseLanguage = null,
            responseFormat = null
        )
        viewModel.selectConversation("conv_null_overrides")
        advanceUntilIdle()
        assertEquals(ResponseStyle.EDUCATIONAL, viewModel.activeResponseStyle.value)
        assertEquals(ResponseLanguage.ENGLISH, viewModel.activeResponseLanguage.value)
        assertEquals(ResponseFormat.STEP_BY_STEP, viewModel.activeResponseFormat.value)
    }

    // 7. RESET SETTINGS
    @Test
    fun testResetSettingsRestoresDefaultsWithoutDeletingData() = runTest {
        // Persist a conversation and message
        conversationRepository.createConversation(
            id = "conv_keep",
            title = "Keep this safe",
            responseStyle = ResponseStyle.CREATIVE
        )
        conversationRepository.saveMessage(
            Message(
                id = "m1",
                conversationId = "conv_keep",
                sender = MessageSender.USER,
                content = "Do not delete me",
                timestamp = "10:00 AM"
            )
        )

        // Set non-default settings
        viewModel.updateSettings(
            SettingsState(
                responseStyle = ResponseStyle.CREATIVE,
                responseLanguage = ResponseLanguage.BENGALI,
                responseFormat = ResponseFormat.STRUCTURED
            )
        )
        advanceUntilIdle()

        // Reset Settings
        viewModel.resetSettings()
        advanceUntilIdle()

        // Global settings restored to defaults
        val currentSettings = viewModel.settings.value
        assertEquals(ResponseStyle.BALANCED, currentSettings.responseStyle)
        assertEquals(ResponseLanguage.AUTO, currentSettings.responseLanguage)
        assertEquals(ResponseFormat.AUTO, currentSettings.responseFormat)

        // Active preferences restored if in new chat
        viewModel.startNewChat()
        advanceUntilIdle()
        assertEquals(ResponseStyle.BALANCED, viewModel.activeResponseStyle.value)
        assertEquals(ResponseLanguage.AUTO, viewModel.activeResponseLanguage.value)
        assertEquals(ResponseFormat.AUTO, viewModel.activeResponseFormat.value)

        // Verify conversation and message remain intact
        val convs = conversationRepository.getAllConversations().first()
        assertEquals(1, convs.size)
        assertEquals("Keep this safe", convs[0].title)

        val msgs = conversationRepository.getMessagesForConversation("conv_keep").first()
        assertEquals(1, msgs.size)
        assertEquals("Do not delete me", msgs[0].content)
    }

    // 8. REQUEST INTEGRATION: SYSTEM DIRECTIVE GENERATION
    @Test
    fun testResponsePreferencesHelperDirectives() {
        // 1. Defaults produce NULL directive (Zero payload overhead)
        val defaultDirective = ResponsePreferencesHelper.buildSystemDirective(
            style = ResponseStyle.BALANCED,
            language = ResponseLanguage.AUTO,
            format = ResponseFormat.AUTO
        )
        assertNull(defaultDirective)

        // 2. Concise + Bengali + Step-by-step
        val customDirective = ResponsePreferencesHelper.buildSystemDirective(
            style = ResponseStyle.CONCISE,
            language = ResponseLanguage.BENGALI,
            format = ResponseFormat.STEP_BY_STEP
        )
        assertNotNull(customDirective)
        assertTrue(customDirective!!.contains("Style: Provide concise"))
        assertTrue(customDirective.contains("Language: Prefer Bengali"))
        assertTrue(customDirective.contains("Do not translate source code"))
        assertTrue(customDirective.contains("Format: Organize instructional or procedural answers into clear numbered steps"))

        // 3. Educational + English + Structured
        val eduDirective = ResponsePreferencesHelper.buildSystemDirective(
            style = ResponseStyle.EDUCATIONAL,
            language = ResponseLanguage.ENGLISH,
            format = ResponseFormat.STRUCTURED
        )
        assertNotNull(eduDirective)
        assertTrue(eduDirective!!.contains("Style: Provide educational"))
        assertTrue(eduDirective.contains("Language: Respond in English"))
        assertTrue(eduDirective.contains("Format: Structure response using headings"))
    }

    // 9. NORMAL CHAT & STREAMING INTEGRATION
    @Test
    fun testStreamingIncludesSelectedResponsePreferences() = runTest {
        viewModel.setActiveResponseStyle(ResponseStyle.EDUCATIONAL)
        viewModel.setActiveResponseLanguage(ResponseLanguage.BENGALI)
        viewModel.setActiveResponseFormat(ResponseFormat.STRUCTURED)

        viewModel.onComposerTextChange("Explain QuickSort algorithm")
        viewModel.sendMessage()
        advanceUntilIdle()

        val capturedRequest = fakeApi.lastChatRequest
        assertNotNull("Chat request must have been dispatched to API", capturedRequest)
        assertTrue("Request must include system directive", capturedRequest!!.message.contains("[Response Guidelines:"))
        assertTrue("Request must contain Educational directive", capturedRequest.message.contains("Style: Provide educational"))
        assertTrue("Request must contain Bengali directive", capturedRequest.message.contains("Language: Prefer Bengali"))
        assertTrue("Request must contain Structured directive", capturedRequest.message.contains("Format: Structure response"))
        assertTrue("Original prompt must be preserved at end", capturedRequest.message.contains("Explain QuickSort algorithm"))
    }

    // 10. QUICK RESPONSE CONTROL SETTER
    @Test
    fun testQuickResponseControlSetterUpdatesActiveAndPersists() = runTest {
        conversationRepository.createConversation(
            id = "conv_quick",
            title = "Quick Switching Test"
        )
        viewModel.selectConversation("conv_quick")
        advanceUntilIdle()

        // Switch via quick control
        viewModel.setActiveResponseStyle(ResponseStyle.CREATIVE)
        advanceUntilIdle()

        assertEquals(ResponseStyle.CREATIVE, viewModel.activeResponseStyle.value)

        // Room persistence check
        val saved = conversationRepository.getConversationById("conv_quick")
        assertEquals(ResponseStyle.CREATIVE, saved?.responseStyle)
    }
}
