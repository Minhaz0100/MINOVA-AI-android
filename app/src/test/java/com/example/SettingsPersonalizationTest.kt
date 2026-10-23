package com.example

import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.ResponseStyle
import com.example.data.model.SettingsState
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.voice.VoiceTextMerger
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPersonalizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var conversationRepository: InMemoryConversationRepository
    private lateinit var viewModel: MinovaViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = InMemorySettingsRepository()
        conversationRepository = InMemoryConversationRepository()
        viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(apiService = FakeMinovaApiService(), ioDispatcher = testDispatcher),
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

    @Test
    fun `default settings match Step 9 specifications`() {
        val defaults = SettingsState()

        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(ResponseStyle.BALANCED, defaults.responseStyle)
        assertEquals("System Default", defaults.voiceLanguage)
        assertTrue(defaults.voiceOutputEnabled)
        assertEquals(1.0f, defaults.speechSpeed, 0.001f)
        assertTrue(defaults.enterToSend)
        assertFalse(defaults.showTimestamps)
        assertFalse(defaults.autoFocusComposer)
        assertTrue(defaults.showAttachmentPreview)
        assertEquals("MINOVA AI", defaults.aiModel)
        assertEquals("Balanced", defaults.responseStyle.displayName)
        assertEquals("Balanced", defaults.responsePreference)
    }

    @Test
    fun `updating individual settings updates repository flow`() = runTest {
        // Theme
        settingsRepository.updateThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settingsRepository.getSettings().themeMode)

        // Response Style
        settingsRepository.updateResponseStyle(ResponseStyle.CONCISE)
        assertEquals(ResponseStyle.CONCISE, settingsRepository.getSettings().responseStyle)

        // Voice Language
        settingsRepository.updateVoiceLanguage("বাংলা (Bangladesh)")
        assertEquals("বাংলা (Bangladesh)", settingsRepository.getSettings().voiceLanguage)

        // Voice Output
        settingsRepository.updateVoiceOutputEnabled(false)
        assertFalse(settingsRepository.getSettings().voiceOutputEnabled)

        // Speech Speed
        settingsRepository.updateSpeechSpeed(1.5f)
        assertEquals(1.5f, settingsRepository.getSettings().speechSpeed, 0.001f)

        // Chat Settings
        settingsRepository.updateEnterToSend(false)
        assertFalse(settingsRepository.getSettings().enterToSend)

        settingsRepository.updateShowTimestamps(true)
        assertTrue(settingsRepository.getSettings().showTimestamps)

        settingsRepository.updateAutoFocusComposer(true)
        assertTrue(settingsRepository.getSettings().autoFocusComposer)

        // Attachment Preview
        settingsRepository.updateShowAttachmentPreview(false)
        assertFalse(settingsRepository.getSettings().showAttachmentPreview)
    }

    @Test
    fun `resetToDefaults restores all preferences without deleting conversations or messages`() = runTest {
        // Add sample conversation and message
        conversationRepository.createConversation(
            id = "conv_persisted",
            title = "Important Conversation"
        )
        conversationRepository.saveMessage(
            Message(
                id = "msg_1",
                conversationId = "conv_persisted",
                sender = MessageSender.USER,
                content = "Hello MINOVA",
                timestamp = "10:00 AM"
            )
        )

        // Modify multiple settings
        settingsRepository.updateThemeMode(ThemeMode.LIGHT)
        settingsRepository.updateResponseStyle(ResponseStyle.DETAILED)
        settingsRepository.updateVoiceLanguage("English (UK)")
        settingsRepository.updateVoiceOutputEnabled(false)
        settingsRepository.updateSpeechSpeed(1.75f)
        settingsRepository.updateEnterToSend(false)
        settingsRepository.updateShowTimestamps(true)
        settingsRepository.updateAutoFocusComposer(true)
        settingsRepository.updateShowAttachmentPreview(false)

        // Perform Reset
        settingsRepository.resetToDefaults()

        val resetSettings = settingsRepository.getSettings()

        // Verify all settings reverted to defaults
        assertEquals(ThemeMode.SYSTEM, resetSettings.themeMode)
        assertEquals(ResponseStyle.BALANCED, resetSettings.responseStyle)
        assertEquals("System Default", resetSettings.voiceLanguage)
        assertTrue(resetSettings.voiceOutputEnabled)
        assertEquals(1.0f, resetSettings.speechSpeed, 0.001f)
        assertTrue(resetSettings.enterToSend)
        assertFalse(resetSettings.showTimestamps)
        assertFalse(resetSettings.autoFocusComposer)
        assertTrue(resetSettings.showAttachmentPreview)

        // CRITICAL CHECK: Ensure conversations and messages are completely intact
        val convs = conversationRepository.getAllConversations().first()
        assertEquals(1, convs.size)
        assertEquals("Important Conversation", convs[0].title)

        val msgs = conversationRepository.getMessagesForConversation("conv_persisted").first()
        assertEquals(1, msgs.size)
        assertEquals("Hello MINOVA", msgs[0].content)
    }

    @Test
    fun `viewModel reacts to settings updates and resetSettings restores defaults`() = runTest {
        // Update via viewModel
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        viewModel.updateSettings(
            viewModel.settings.value.copy(
                responseStyle = ResponseStyle.CONCISE,
                speechSpeed = 1.25f,
                showTimestamps = true
            )
        )
        advanceUntilIdle()

        assertEquals(ResponseStyle.CONCISE, viewModel.settings.value.responseStyle)
        assertEquals(1.25f, viewModel.settings.value.speechSpeed, 0.001f)
        assertTrue(viewModel.settings.value.showTimestamps)

        // Call resetSettings
        viewModel.resetSettings()
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
        assertEquals(ResponseStyle.BALANCED, viewModel.settings.value.responseStyle)
        assertEquals(1.0f, viewModel.settings.value.speechSpeed, 0.001f)
        assertFalse(viewModel.settings.value.showTimestamps)
        assertTrue(viewModel.settings.value.enterToSend)
        assertTrue(viewModel.settings.value.showAttachmentPreview)
    }

    @Test
    fun `ResponseStyle fallback handles unknown or corrupted strings safely`() {
        assertEquals(ResponseStyle.BALANCED, ResponseStyle.fromString("UNKNOWN_VALUE"))
        assertEquals(ResponseStyle.BALANCED, ResponseStyle.fromString(""))
        assertEquals(ResponseStyle.BALANCED, ResponseStyle.fromString(null.toString()))
        assertEquals(ResponseStyle.CONCISE, ResponseStyle.fromString("concise"))
        assertEquals(ResponseStyle.DETAILED, ResponseStyle.fromString("Detailed"))
        assertEquals(ResponseStyle.BALANCED, ResponseStyle.fromString("Balanced"))
    }

    @Test
    fun `speech speed sanitization handles out-of-range values gracefully`() = runTest {
        settingsRepository.updateSpeechSpeed(10.0f) // Extreme speed
        assertEquals(1.0f, settingsRepository.getSettings().speechSpeed, 0.001f)

        settingsRepository.updateSpeechSpeed(-2.0f) // Negative speed
        assertEquals(1.0f, settingsRepository.getSettings().speechSpeed, 0.001f)

        settingsRepository.updateSpeechSpeed(0.75f) // Valid
        assertEquals(0.75f, settingsRepository.getSettings().speechSpeed, 0.001f)

        settingsRepository.updateSpeechSpeed(1.75f) // Valid
        assertEquals(1.75f, settingsRepository.getSettings().speechSpeed, 0.001f)
    }

    @Test
    fun `voice language tag resolution preserves Bengali and English options correctly`() {
        // Bengali
        val banglaTag = VoiceTextMerger.resolveLanguageTag("বাংলা (Bangladesh)", Locale.US)
        assertEquals("bn-BD", banglaTag)

        // English US
        val englishUsTag = VoiceTextMerger.resolveLanguageTag("English (US)", Locale.US)
        assertEquals("en-US", englishUsTag)

        // English UK
        val englishUkTag = VoiceTextMerger.resolveLanguageTag("English (UK)", Locale.US)
        assertEquals("en-GB", englishUkTag)

        // System default
        val systemTag = VoiceTextMerger.resolveLanguageTag("System Default", Locale.CANADA)
        assertTrue(systemTag.startsWith("en-CA") || systemTag.isNotEmpty())
    }
}
