package com.example

import android.speech.SpeechRecognizer
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.voice.VoiceInputManager
import com.example.data.voice.VoiceState
import com.example.data.voice.VoiceTextMerger
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.MinovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun voiceTextMerger_mergesEmptyBaseAndSpeech() {
        val result = VoiceTextMerger.merge("", "Explain polymorphism")
        assertEquals("Explain polymorphism", result)

        val resultWithSpaces = VoiceTextMerger.merge("   ", "  Explain polymorphism  ")
        assertEquals("Explain polymorphism", resultWithSpaces)
    }

    @Test
    fun voiceTextMerger_preservesExistingTextWithoutDuplicateSpaces() {
        // Base text ending with space
        val result1 = VoiceTextMerger.merge("Explain ", "polymorphism in C++")
        assertEquals("Explain polymorphism in C++", result1)

        // Base text without trailing space
        val result2 = VoiceTextMerger.merge("Explain", "polymorphism in C++")
        assertEquals("Explain polymorphism in C++", result2)

        // Base text with multiple trailing spaces
        val result3 = VoiceTextMerger.merge("Explain    ", "polymorphism in C++")
        assertEquals("Explain polymorphism in C++", result3)
    }

    @Test
    fun voiceTextMerger_handlesEmptySpeech() {
        val result = VoiceTextMerger.merge("Already typed", "")
        assertEquals("Already typed", result)

        val resultSpaces = VoiceTextMerger.merge("Already typed", "   ")
        assertEquals("Already typed", resultSpaces)
    }

    @Test
    fun voiceTextMerger_mapsAllAndroidSpeechErrorCodesToFriendlyMessages() {
        val audioError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_AUDIO)
        assertEquals("Audio recording error. Please check your microphone.", audioError)

        val clientError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_CLIENT)
        assertEquals("Speech service client error. Please try again.", clientError)

        val permError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals("Microphone permission is required for voice input.", permError)

        val netError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_NETWORK)
        assertEquals("Network connection error during voice recognition.", netError)

        val timeoutError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        assertEquals("Network timeout. Please check your connection.", timeoutError)

        val noMatchError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("No speech recognized. Please speak clearly and try again.", noMatchError)

        val busyError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertEquals("Voice recognition service is busy. Please try again.", busyError)

        val serverError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_SERVER)
        assertEquals("Speech recognition server error. Please try again.", serverError)

        val speechTimeoutError = VoiceTextMerger.getErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals("No speech detected. Voice input stopped.", speechTimeoutError)
    }

    @Test
    fun voiceTextMerger_resolvesLanguageTagsForEnglishAndBengali() {
        // Bengali / Bangla in various representations
        val bn1 = VoiceTextMerger.resolveLanguageTag("বাংলা (Bangladesh)")
        assertEquals("bn-BD", bn1)

        val bn2 = VoiceTextMerger.resolveLanguageTag("Bengali (Bangla)")
        assertEquals("bn-BD", bn2)

        val bn3 = VoiceTextMerger.resolveLanguageTag("বাংলা")
        assertEquals("bn-BD", bn3)

        val bn4 = VoiceTextMerger.resolveLanguageTag("Bangla")
        assertEquals("bn-BD", bn4)

        val bn5 = VoiceTextMerger.resolveLanguageTag("bn-BD")
        assertEquals("bn-BD", bn5)

        val bn6 = VoiceTextMerger.resolveLanguageTag("bn")
        assertEquals("bn-BD", bn6)

        // Verify Bengali does NOT resolve or fall back to en-US
        assertFalse(bn1 == "en-US")
        assertFalse(bn2 == "en-US")
        assertFalse(bn3 == "en-US")

        // English US & UK
        val enUS = VoiceTextMerger.resolveLanguageTag("English (US)")
        assertEquals("en-US", enUS)

        val enGB = VoiceTextMerger.resolveLanguageTag("English (UK)")
        assertEquals("en-GB", enGB)

        // System default with US Locale
        val systemLocaleUS = Locale.US
        val systemTagUS = VoiceTextMerger.resolveLanguageTag("System Default", systemLocaleUS)
        assertEquals("en-US", systemTagUS)

        // System default with UK Locale
        val systemLocaleUK = Locale.UK
        val systemTagUK = VoiceTextMerger.resolveLanguageTag("System Default", systemLocaleUK)
        assertEquals("en-GB", systemTagUK)
    }

    @Test
    fun voiceTextMerger_preservesBengaliTypedTextAndAvoidsDuplicateSpaces() {
        // Bengali base text with Bengali spoken text
        val merged1 = VoiceTextMerger.merge("আমি জানতে চাই ", "কীভাবে জেটপ্যাক কম্পোজ শিখব")
        assertEquals("আমি জানতে চাই কীভাবে জেটপ্যাক কম্পোজ শিখব", merged1)

        // Bengali base text without trailing space
        val merged2 = VoiceTextMerger.merge("আমি জানতে চাই", "কীভাবে জেটপ্যাক কম্পোজ শিখব")
        assertEquals("আমি জানতে চাই কীভাবে জেটপ্যাক কম্পোজ শিখব", merged2)

        // Mixed English and Bengali
        val merged3 = VoiceTextMerger.merge("Write a code for ", "বাইনারি সার্চ অ্যালগরিদম")
        assertEquals("Write a code for বাইনারি সার্চ অ্যালগরিদম", merged3)
    }

    @Test
    fun viewModel_voiceRecognitionFlow_bengaliSpeechUpdatesComposer() = runTest {
        val fakeVoiceManager = FakeVoiceInputManager(isAvailable = true)
        val viewModel = MinovaViewModel(
            voiceInputManager = fakeVoiceManager
        )

        // Set voice language to Bengali
        val currentSettings = viewModel.settings.value
        viewModel.updateSettings(currentSettings.copy(voiceLanguage = "বাংলা (Bangladesh)"))

        // User typed base prompt
        viewModel.onComposerTextChange("দয়া করে বলুন ")
        assertEquals("দয়া করে বলুন ", viewModel.composerText.value)

        // Start voice recording
        viewModel.startVoiceRecording()
        assertTrue(viewModel.isVoiceActive.value)
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)

        // Live partial result in Bengali arrives
        fakeVoiceManager.simulatePartial("বাংলাদেশের রাজধানী কোনটি")
        assertEquals("দয়া করে বলুন বাংলাদেশের রাজধানী কোনটি", viewModel.composerText.value)

        // Final result arrives
        fakeVoiceManager.simulateComplete("বাংলাদেশের রাজধানী কোনটি এবং এর জনসংখ্যা কত?")
        assertEquals("দয়া করে বলুন বাংলাদেশের রাজধানী কোনটি এবং এর জনসংখ্যা কত?", viewModel.composerText.value)
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
        assertFalse(viewModel.isVoiceActive.value)
    }

    @Test
    fun viewModel_voiceRecognitionFlow_updatesComposerTextProgressively() = runTest {
        val fakeVoiceManager = FakeVoiceInputManager(isAvailable = true)
        val viewModel = MinovaViewModel(
            voiceInputManager = fakeVoiceManager
        )

        // User typed "Help me "
        viewModel.onComposerTextChange("Help me ")
        assertEquals("Help me ", viewModel.composerText.value)

        // Start voice recording
        viewModel.startVoiceRecording()
        assertTrue(viewModel.isVoiceActive.value)
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)

        // Live partial result arrives
        fakeVoiceManager.simulatePartial("understand")
        assertEquals("Help me understand", viewModel.composerText.value)

        // Another partial result arrives
        fakeVoiceManager.simulatePartial("understand coroutines")
        assertEquals("Help me understand coroutines", viewModel.composerText.value)

        // Final recognition result arrives
        fakeVoiceManager.simulateComplete("understand coroutines in Kotlin")
        assertEquals("Help me understand coroutines in Kotlin", viewModel.composerText.value)
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
        assertFalse(viewModel.isVoiceActive.value)

        // Voice recognition itself does NOT send any message to Room
        assertTrue(viewModel.activeMessages.value.isEmpty())
    }

    @Test
    fun viewModel_voiceRecognition_preservesExistingAttachment() = runTest {
        val fakeVoiceManager = FakeVoiceInputManager(isAvailable = true)
        val viewModel = MinovaViewModel(
            voiceInputManager = fakeVoiceManager
        )

        val testAttachment = Attachment(
            uri = "content://media/external/images/media/42",
            fileName = "diagram.png",
            mimeType = "image/png",
            type = AttachmentType.IMAGE,
            sizeBytes = 1024
        )
        viewModel.selectAttachment(testAttachment)
        assertEquals("diagram.png", viewModel.selectedAttachment.value?.fileName)

        // Voice input
        viewModel.startVoiceRecording()
        fakeVoiceManager.simulateComplete("Analyze this diagram")

        assertEquals("Analyze this diagram", viewModel.composerText.value)
        // Attachment is still safely selected and ready to be sent
        assertNotNull(viewModel.selectedAttachment.value)
        assertEquals("diagram.png", viewModel.selectedAttachment.value?.fileName)
    }

    @Test
    fun viewModel_voiceRecognition_handlesErrorGracefully() = runTest {
        val fakeVoiceManager = FakeVoiceInputManager(isAvailable = true)
        val viewModel = MinovaViewModel(
            voiceInputManager = fakeVoiceManager
        )

        viewModel.startVoiceRecording()
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)

        // Simulate an error
        fakeVoiceManager.simulateError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
        assertFalse(viewModel.isVoiceActive.value)
        assertTrue(viewModel.chatUiState.value is ChatUiState.Error)
        assertEquals(
            "No speech recognized. Please speak clearly and try again.",
            (viewModel.chatUiState.value as ChatUiState.Error).message
        )
    }

    @Test
    fun viewModel_voiceRecognition_stopAndCancelWorkCorrectly() = runTest {
        val fakeVoiceManager = FakeVoiceInputManager(isAvailable = true)
        val viewModel = MinovaViewModel(
            voiceInputManager = fakeVoiceManager
        )

        viewModel.startVoiceRecording()
        assertEquals(VoiceState.LISTENING, viewModel.voiceState.value)

        viewModel.stopVoiceRecording()
        assertEquals(VoiceState.IDLE, fakeVoiceManager.voiceState.value)

        viewModel.startVoiceRecording()
        viewModel.cancelVoiceRecording()
        assertEquals(VoiceState.IDLE, fakeVoiceManager.voiceState.value)
    }
}

/**
 * Fake implementation of VoiceInputManager for clean local unit testing.
 */
class FakeVoiceInputManager(
    private val isAvailable: Boolean = true
) : VoiceInputManager {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeBaseText: String = ""
    private var onTextUpdatedCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onCompleteCallback: ((String) -> Unit)? = null

    override fun isRecognitionAvailable(): Boolean = isAvailable

    override fun startListening(
        baseText: String,
        language: String,
        onTextUpdated: (mergedText: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onComplete: (finalMergedText: String) -> Unit
    ) {
        if (!isAvailable) {
            val error = "Speech recognition is not available on this device."
            _errorMessage.value = error
            onError(error)
            return
        }
        activeBaseText = baseText
        onTextUpdatedCallback = onTextUpdated
        onErrorCallback = onError
        onCompleteCallback = onComplete
        _voiceState.value = VoiceState.LISTENING
    }

    fun simulatePartial(speechText: String) {
        val merged = VoiceTextMerger.merge(activeBaseText, speechText)
        onTextUpdatedCallback?.invoke(merged)
    }

    fun simulateComplete(speechText: String) {
        val merged = VoiceTextMerger.merge(activeBaseText, speechText)
        _voiceState.value = VoiceState.IDLE
        onTextUpdatedCallback?.invoke(merged)
        onCompleteCallback?.invoke(merged)
    }

    fun simulateError(errorCode: Int) {
        val msg = VoiceTextMerger.getErrorMessage(errorCode)
        _errorMessage.value = msg
        _voiceState.value = VoiceState.IDLE
        onErrorCallback?.invoke(msg)
    }

    override fun stopListening() {
        _voiceState.value = VoiceState.IDLE
    }

    override fun cancel() {
        _voiceState.value = VoiceState.IDLE
    }

    override fun destroy() {
        _voiceState.value = VoiceState.IDLE
    }
}
