package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AndroidVoiceInputManager(
    context: Context
) : VoiceInputManager {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeBaseText: String = ""
    private var activeTextUpdatedCallback: ((String) -> Unit)? = null
    private var activeErrorCallback: ((String) -> Unit)? = null
    private var activeCompleteCallback: ((String) -> Unit)? = null

    override fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (_: Throwable) {
            false
        }
    }

    override fun startListening(
        baseText: String,
        language: String,
        onTextUpdated: (mergedText: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onComplete: (finalMergedText: String) -> Unit
    ) {
        if (!isRecognitionAvailable()) {
            val msg = "Speech recognition is not available on this device."
            _errorMessage.value = msg
            _voiceState.value = VoiceState.ERROR
            onError(msg)
            _voiceState.value = VoiceState.IDLE
            return
        }

        activeBaseText = baseText
        activeTextUpdatedCallback = onTextUpdated
        activeErrorCallback = onError
        activeCompleteCallback = onComplete
        _errorMessage.value = null

        mainHandler.post {
            try {
                // Ensure any previous instance is cleaned up
                safeDestroyRecognizer()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(createRecognitionListener())

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)

                    val targetLangTag = VoiceTextMerger.resolveLanguageTag(language, Locale.getDefault())
                    val isBengali = targetLangTag.startsWith("bn", ignoreCase = true)

                    if (isBengali) {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                        putExtra("android.speech.extra.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE", true)
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "bn-IN"))
                    } else {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLangTag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLangTag)
                        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                        putExtra("android.speech.extra.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE", true)
                    }
                }

                _voiceState.value = VoiceState.LISTENING
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                Log.e("VoiceInputManager", "Failed to start speech recognition", t)
                val msg = t.message ?: "Failed to start speech recognition."
                _errorMessage.value = msg
                _voiceState.value = VoiceState.ERROR
                onError(msg)
                safeDestroyRecognizer()
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                if (_voiceState.value == VoiceState.LISTENING) {
                    _voiceState.value = VoiceState.PROCESSING
                    speechRecognizer?.stopListening()
                }
            } catch (t: Throwable) {
                Log.w("VoiceInputManager", "Error while stopping listening", t)
                safeDestroyRecognizer()
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (t: Throwable) {
                Log.w("VoiceInputManager", "Error cancelling recognition", t)
            } finally {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    override fun destroy() {
        mainHandler.post {
            safeDestroyRecognizer()
            _voiceState.value = VoiceState.IDLE
            activeTextUpdatedCallback = null
            activeErrorCallback = null
            activeCompleteCallback = null
        }
    }

    private fun safeDestroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (t: Throwable) {
            Log.w("VoiceInputManager", "Error destroying speech recognizer", t)
        } finally {
            speechRecognizer = null
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.value = VoiceState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                _voiceState.value = VoiceState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level updates - available for visualizer if needed
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _voiceState.value = VoiceState.PROCESSING
            }

            override fun onError(error: Int) {
                val errorMsg = VoiceTextMerger.getErrorMessage(error)
                Log.w("VoiceInputManager", "Speech recognition error: $error -> $errorMsg")
                _errorMessage.value = errorMsg
                _voiceState.value = VoiceState.ERROR
                activeErrorCallback?.invoke(errorMsg)
                safeDestroyRecognizer()
                _voiceState.value = VoiceState.IDLE
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull { it.isNotBlank() } ?: ""
                val mergedText = VoiceTextMerger.merge(activeBaseText, recognized)

                if (recognized.isNotBlank()) {
                    activeTextUpdatedCallback?.invoke(mergedText)
                    activeCompleteCallback?.invoke(mergedText)
                }

                _voiceState.value = VoiceState.IDLE
                safeDestroyRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedPartial = matches?.firstOrNull { it.isNotBlank() }
                if (!recognizedPartial.isNullOrBlank()) {
                    val merged = VoiceTextMerger.merge(activeBaseText, recognizedPartial)
                    activeTextUpdatedCallback?.invoke(merged)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
