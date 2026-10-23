package com.example

import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.InMemoryConversationRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.voice.TextToSpeechManager
import com.example.data.voice.TtsState
import com.example.data.voice.TtsTextSanitizer
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FakeTextToSpeechManager : TextToSpeechManager {
    private val _ttsState = MutableStateFlow<TtsState>(TtsState.IDLE)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _activeMessageId = MutableStateFlow<String?>(null)
    override val activeMessageId: StateFlow<String?> = _activeMessageId.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var lastSpokenText: String? = null
    var currentSpeed: Float = 1.0f

    override fun speak(
        messageId: String,
        text: String,
        preferredLanguage: String,
        onError: (String) -> Unit
    ) {
        if (_activeMessageId.value == messageId && _isSpeaking.value) {
            stop()
            return
        }
        _activeMessageId.value = messageId
        lastSpokenText = text
        _isSpeaking.value = true
        _ttsState.value = TtsState.SPEAKING
    }

    override fun stop() {
        _activeMessageId.value = null
        _isSpeaking.value = false
        _ttsState.value = TtsState.IDLE
    }

    override fun setSpeechRate(rate: Float) {
        currentSpeed = rate
    }

    override fun shutdown() {
        stop()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TextToSpeechTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTtsManager: FakeTextToSpeechManager
    private lateinit var conversationRepository: InMemoryConversationRepository
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var viewModel: MinovaViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTtsManager = FakeTextToSpeechManager()
        conversationRepository = InMemoryConversationRepository()
        settingsRepository = InMemorySettingsRepository()
        viewModel = MinovaViewModel(
            chatRepository = ChatRepositoryImpl(apiService = FakeMinovaApiService(), ioDispatcher = testDispatcher),
            conversationRepository = conversationRepository,
            appContext = null,
            settingsRepository = settingsRepository,
            voiceInputManager = null,
            textToSpeechManager = fakeTtsManager
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ttsTextSanitizer_detectsPredominantlyBengaliCorrectly() {
        val bengali = "আমি মিনোভা এআই, আপনার কোডিং ও রিসার্চ অ্যাসিস্ট্যান্ট।"
        assertTrue(TtsTextSanitizer.isPredominantlyBengali(bengali))

        val english = "Hello, I am MINOVA AI. How can I help you today?"
        assertFalse(TtsTextSanitizer.isPredominantlyBengali(english))

        val empty = ""
        assertFalse(TtsTextSanitizer.isPredominantlyBengali(empty))
    }

    @Test
    fun ttsTextSanitizer_resolvesTargetLocale() {
        val bnLocale = TtsTextSanitizer.resolveTargetLocale("বাংলা", "System Default", isBengali = true)
        assertEquals("bn", bnLocale.language)

        val enLocale = TtsTextSanitizer.resolveTargetLocale("English", "English (UK)", isBengali = false)
        assertEquals(Locale.UK, enLocale)
    }

    @Test
    fun ttsTextSanitizer_splitsIntoSafeUtteranceChunks() {
        val longText = "Sentence one. Sentence two! Sentence three? Sentence four."
        val chunks = TtsTextSanitizer.splitIntoUtteranceChunks(longText)
        assertTrue(chunks.isNotEmpty())
    }

    @Test
    fun ttsTextSanitizer_cleansSseAndMarkdownArtifacts() {
        val raw = """
            data: # Header
            data: Here is **bold text** and `inline code`.
            ```kotlin
            fun main() { println("Hi") }
            ```
            data: [DONE]
        """.trimIndent()

        val cleaned = TtsTextSanitizer.sanitizeForSpeech(raw, isBengali = false)
        assertFalse(cleaned.contains("data:"))
        assertFalse(cleaned.contains("[DONE]"))
        assertFalse(cleaned.contains("```"))
        assertTrue(cleaned.contains("bold text"))
        assertTrue(cleaned.contains("inline code"))
    }

    @Test
    fun viewModel_ttsFlow_togglesAndSwitchesSpeechCorrectly() = runTest {
        val msgId1 = "msg_1"
        val text1 = "Hello world"
        viewModel.toggleSpeech(msgId1, text1)
        advanceUntilIdle()

        assertEquals(msgId1, fakeTtsManager.activeMessageId.value)
        assertTrue(fakeTtsManager.isSpeaking.value)

        // Toggle same message should stop
        viewModel.toggleSpeech(msgId1, text1)
        advanceUntilIdle()
        assertNull(fakeTtsManager.activeMessageId.value)
        assertFalse(fakeTtsManager.isSpeaking.value)
    }

    @Test
    fun viewModel_ttsFlow_stopsWhenSwitchingChat() = runTest {
        viewModel.toggleSpeech("msg_1", "Speaking")
        advanceUntilIdle()
        assertTrue(fakeTtsManager.isSpeaking.value)

        viewModel.startNewChat()
        advanceUntilIdle()
        assertFalse(fakeTtsManager.isSpeaking.value)
    }
}
