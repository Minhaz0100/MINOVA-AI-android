package com.example.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Native Android TextToSpeech implementation for MINOVA AI assistant responses.
 * Safely handles engine initialization, language availability, sentence chunking,
 * progress tracking, and graceful cleanup.
 */
class AndroidTextToSpeechManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : TextToSpeechManager, TextToSpeech.OnInitListener {

    private val tag = "AndroidTTS"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initError: String? = null

    private val _ttsState = MutableStateFlow(TtsState.INITIALIZING)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _activeMessageId = MutableStateFlow<String?>(null)
    override val activeMessageId: StateFlow<String?> = _activeMessageId.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Tracking pending utterance IDs for chunk sequencing
    private val pendingUtteranceIds = mutableSetOf<String>()
    private var currentSpeakingMessageId: String? = null
    private var pendingRequest: PendingSpeakRequest? = null
    private var currentSpeechRate: Float = 1.0f

    private data class PendingSpeakRequest(
        val messageId: String,
        val text: String,
        val preferredLanguage: String,
        val onError: (String) -> Unit
    )

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.e(tag, "Failed to instantiate TextToSpeech", e)
            _ttsState.value = TtsState.ERROR
            initError = "Text-to-Speech engine could not be created."
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            _ttsState.value = TtsState.IDLE
            initError = null
            setupProgressListener()
            try {
                tts?.setSpeechRate(currentSpeechRate)
            } catch (e: Throwable) {
                Log.w(tag, "Failed setting speech rate on init", e)
            }

            // Process queued request if user clicked while engine was initializing
            pendingRequest?.let { req ->
                pendingRequest = null
                speak(req.messageId, req.text, req.preferredLanguage, req.onError)
            }
        } else {
            isInitialized = false
            _ttsState.value = TtsState.ERROR
            initError = "TTS initialization failed (code: $status)."
            Log.e(tag, "TextToSpeech init failed with status: $status")
            pendingRequest?.onError?.invoke("Text-to-Speech is unavailable on this device.")
            pendingRequest = null
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    _ttsState.value = TtsState.SPEAKING
                    _isSpeaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (utteranceId != null) {
                        pendingUtteranceIds.remove(utteranceId)
                    }
                    if (pendingUtteranceIds.isEmpty()) {
                        _ttsState.value = TtsState.IDLE
                        _isSpeaking.value = false
                        _activeMessageId.value = null
                        currentSpeakingMessageId = null
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                scope.launch {
                    if (utteranceId != null) {
                        pendingUtteranceIds.remove(utteranceId)
                    }
                    if (pendingUtteranceIds.isEmpty()) {
                        _ttsState.value = TtsState.IDLE
                        _isSpeaking.value = false
                        _activeMessageId.value = null
                        currentSpeakingMessageId = null
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(tag, "Utterance error: $utteranceId, code: $errorCode")
                scope.launch {
                    if (utteranceId != null) {
                        pendingUtteranceIds.remove(utteranceId)
                    }
                    if (pendingUtteranceIds.isEmpty()) {
                        _ttsState.value = TtsState.IDLE
                        _isSpeaking.value = false
                        _activeMessageId.value = null
                        currentSpeakingMessageId = null
                    }
                }
            }
        })
    }

    override fun speak(
        messageId: String,
        text: String,
        preferredLanguage: String,
        onError: (String) -> Unit
    ) {
        // 1. If currently speaking the exact same message, toggle it off (Stop)
        if (_isSpeaking.value && currentSpeakingMessageId == messageId) {
            stop()
            return
        }

        // 2. If speaking another message, stop it first before playing the new one
        if (_isSpeaking.value) {
            stop()
        }

        // 3. Engine check: If still initializing, buffer request
        if (!isInitialized) {
            if (_ttsState.value == TtsState.INITIALIZING) {
                pendingRequest = PendingSpeakRequest(messageId, text, preferredLanguage, onError)
                return
            } else {
                onError("Text-to-Speech is not available on this device.")
                return
            }
        }

        val speechEngine = tts ?: run {
            onError("Text-to-Speech engine is not initialized.")
            return
        }

        // 4. Language detection & locale configuration
        val isBengali = TtsTextSanitizer.isPredominantlyBengali(text)
        val targetLocale = TtsTextSanitizer.resolveTargetLocale(text, preferredLanguage, isBengali)

        var languageAvailable = try {
            speechEngine.isLanguageAvailable(targetLocale)
        } catch (e: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        var selectedLocale = targetLocale

        if (languageAvailable < TextToSpeech.LANG_AVAILABLE) {
            if (isBengali) {
                // Bengali voice missing in engine: check English fallback
                val fallbackLocale = Locale.US
                val fallbackAvail = try {
                    speechEngine.isLanguageAvailable(fallbackLocale)
                } catch (_: Throwable) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }

                if (fallbackAvail >= TextToSpeech.LANG_AVAILABLE) {
                    selectedLocale = fallbackLocale
                    onError("Bengali voice not installed in Android TTS. Using English voice.")
                } else {
                    onError("Bengali speech is not supported by your device's TTS engine.")
                    return
                }
            } else {
                // English requested but target unavailable, try general English or system default
                val defaultLocale = speechEngine.defaultVoice?.locale ?: Locale.getDefault()
                val defaultAvail = try {
                    speechEngine.isLanguageAvailable(defaultLocale)
                } catch (_: Throwable) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }

                if (defaultAvail >= TextToSpeech.LANG_AVAILABLE) {
                    selectedLocale = defaultLocale
                } else {
                    onError("Voice language '${targetLocale.displayLanguage}' is not supported.")
                    return
                }
            }
        }

        try {
            speechEngine.language = selectedLocale
            speechEngine.setSpeechRate(currentSpeechRate)
        } catch (e: Throwable) {
            Log.w(tag, "Failed setting language or rate: $selectedLocale", e)
        }

        // 5. Text sanitization
        val speechText = TtsTextSanitizer.sanitizeForSpeech(text, isBengali)
        if (speechText.isBlank()) {
            onError("No readable text found to speak.")
            return
        }

        // 6. Sentence / utterance chunking
        val chunks = TtsTextSanitizer.splitIntoUtteranceChunks(speechText, maxChunkSize = 250)
        if (chunks.isEmpty()) {
            onError("Unable to process speech text.")
            return
        }

        // 7. Queue chunks sequentially
        pendingUtteranceIds.clear()
        currentSpeakingMessageId = messageId
        _activeMessageId.value = messageId
        _isSpeaking.value = true
        _ttsState.value = TtsState.SPEAKING

        for ((index, chunk) in chunks.withIndex()) {
            val utteranceId = "msg_${messageId}_chunk_$index"
            pendingUtteranceIds.add(utteranceId)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = speechEngine.speak(chunk, queueMode, params, utteranceId)

            if (result == TextToSpeech.ERROR) {
                Log.e(tag, "Error speaking chunk $index: $chunk")
                pendingUtteranceIds.remove(utteranceId)
            }
        }

        if (pendingUtteranceIds.isEmpty()) {
            _isSpeaking.value = false
            _activeMessageId.value = null
            currentSpeakingMessageId = null
            _ttsState.value = TtsState.IDLE
            onError("Speech playback could not be started.")
        }
    }

    override fun setSpeechRate(rate: Float) {
        val sanitized = if (rate in 0.5f..2.5f) rate else 1.0f
        currentSpeechRate = sanitized
        try {
            tts?.setSpeechRate(sanitized)
        } catch (e: Throwable) {
            Log.w(tag, "Failed setting speech rate: $sanitized", e)
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Throwable) {
            Log.w(tag, "Error stopping TTS", e)
        }
        pendingUtteranceIds.clear()
        currentSpeakingMessageId = null
        _activeMessageId.value = null
        _isSpeaking.value = false
        _ttsState.value = TtsState.IDLE
        pendingRequest = null
    }

    override fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.w(tag, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
        _ttsState.value = TtsState.IDLE
    }
}
