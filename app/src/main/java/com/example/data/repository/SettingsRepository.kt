package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.model.SettingsState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

val Context.minovaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "minova_settings_preferences")

interface SettingsRepository {
    val settingsFlow: Flow<SettingsState>
    suspend fun getSettings(): SettingsState
    suspend fun updateSettings(settings: SettingsState)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateResponseStyle(style: ResponseStyle)
    suspend fun updateResponseLanguage(language: ResponseLanguage)
    suspend fun updateResponseFormat(format: ResponseFormat)
    suspend fun updateVoiceLanguage(language: String)
    suspend fun updateVoiceInputEnabled(enabled: Boolean)
    suspend fun updateVoiceOutputEnabled(enabled: Boolean)
    suspend fun updateSpeechSpeed(speed: Float)
    suspend fun updateEnterToSend(enabled: Boolean)
    suspend fun updateShowTimestamps(enabled: Boolean)
    suspend fun updateAutoFocusComposer(enabled: Boolean)
    suspend fun updateShowAttachmentPreview(enabled: Boolean)
    suspend fun updateWebSearchDefault(enabled: Boolean)
    suspend fun resetToDefaults()
}

object SettingsPreferencesKeys {
    val THEME_MODE = stringPreferencesKey("pref_theme_mode")
    val RESPONSE_STYLE = stringPreferencesKey("pref_response_style")
    val RESPONSE_LANGUAGE = stringPreferencesKey("pref_response_language")
    val RESPONSE_FORMAT = stringPreferencesKey("pref_response_format")
    val VOICE_LANGUAGE = stringPreferencesKey("pref_voice_language")
    val VOICE_INPUT_ENABLED = booleanPreferencesKey("pref_voice_input_enabled")
    val VOICE_OUTPUT_ENABLED = booleanPreferencesKey("pref_voice_output_enabled")
    val SPEECH_SPEED = floatPreferencesKey("pref_speech_speed")
    val ENTER_TO_SEND = booleanPreferencesKey("pref_enter_to_send")
    val SHOW_TIMESTAMPS = booleanPreferencesKey("pref_show_timestamps")
    val AUTO_FOCUS_COMPOSER = booleanPreferencesKey("pref_auto_focus_composer")
    val SHOW_ATTACHMENT_PREVIEW = booleanPreferencesKey("pref_show_attachment_preview")
    val WEB_SEARCH_DEFAULT = booleanPreferencesKey("pref_web_search_default")
    val AI_MODEL = stringPreferencesKey("pref_ai_model")
    val CODE_HIGHLIGHTING = booleanPreferencesKey("pref_code_highlighting")
    val TTS_VOICE = stringPreferencesKey("pref_tts_voice")
}

/**
 * DataStore-backed centralized settings repository for MINOVA AI.
 * Persists all personalization settings across app restarts, recreation, and configuration changes.
 * Does not transmit or store settings in remote backends.
 */
class SettingsRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    override val settingsFlow: Flow<SettingsState> = context.minovaSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapPreferencesToSettings(preferences)
        }
        .flowOn(ioDispatcher)

    override suspend fun getSettings(): SettingsState {
        return withContext(ioDispatcher) {
            settingsFlow.first()
        }
    }

    override suspend fun updateSettings(settings: SettingsState) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.THEME_MODE] = settings.themeMode.name
                prefs[SettingsPreferencesKeys.RESPONSE_STYLE] = settings.responseStyle.name
                prefs[SettingsPreferencesKeys.RESPONSE_LANGUAGE] = settings.responseLanguage.name
                prefs[SettingsPreferencesKeys.RESPONSE_FORMAT] = settings.responseFormat.name
                prefs[SettingsPreferencesKeys.VOICE_LANGUAGE] = settings.voiceLanguage
                prefs[SettingsPreferencesKeys.VOICE_INPUT_ENABLED] = settings.voiceInputEnabled
                prefs[SettingsPreferencesKeys.VOICE_OUTPUT_ENABLED] = settings.voiceOutputEnabled
                prefs[SettingsPreferencesKeys.SPEECH_SPEED] = settings.speechSpeed
                prefs[SettingsPreferencesKeys.ENTER_TO_SEND] = settings.enterToSend
                prefs[SettingsPreferencesKeys.SHOW_TIMESTAMPS] = settings.showTimestamps
                prefs[SettingsPreferencesKeys.AUTO_FOCUS_COMPOSER] = settings.autoFocusComposer
                prefs[SettingsPreferencesKeys.SHOW_ATTACHMENT_PREVIEW] = settings.showAttachmentPreview
                prefs[SettingsPreferencesKeys.WEB_SEARCH_DEFAULT] = settings.webSearchDefault
                prefs[SettingsPreferencesKeys.AI_MODEL] = settings.aiModel
                prefs[SettingsPreferencesKeys.CODE_HIGHLIGHTING] = settings.codeHighlighting
                prefs[SettingsPreferencesKeys.TTS_VOICE] = settings.ttsVoice
            }
        }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.THEME_MODE] = themeMode.name
            }
        }
    }

    override suspend fun updateResponseStyle(style: ResponseStyle) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.RESPONSE_STYLE] = style.name
            }
        }
    }

    override suspend fun updateResponseLanguage(language: ResponseLanguage) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.RESPONSE_LANGUAGE] = language.name
            }
        }
    }

    override suspend fun updateResponseFormat(format: ResponseFormat) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.RESPONSE_FORMAT] = format.name
            }
        }
    }

    override suspend fun updateVoiceLanguage(language: String) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.VOICE_LANGUAGE] = language.ifBlank { "System Default" }
            }
        }
    }

    override suspend fun updateVoiceInputEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.VOICE_INPUT_ENABLED] = enabled
            }
        }
    }

    override suspend fun updateVoiceOutputEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.VOICE_OUTPUT_ENABLED] = enabled
            }
        }
    }

    override suspend fun updateSpeechSpeed(speed: Float) {
        val sanitizedSpeed = if (speed in 0.5f..2.5f) speed else 1.0f
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.SPEECH_SPEED] = sanitizedSpeed
            }
        }
    }

    override suspend fun updateEnterToSend(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.ENTER_TO_SEND] = enabled
            }
        }
    }

    override suspend fun updateShowTimestamps(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.SHOW_TIMESTAMPS] = enabled
            }
        }
    }

    override suspend fun updateAutoFocusComposer(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.AUTO_FOCUS_COMPOSER] = enabled
            }
        }
    }

    override suspend fun updateShowAttachmentPreview(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.SHOW_ATTACHMENT_PREVIEW] = enabled
            }
        }
    }

    override suspend fun updateWebSearchDefault(enabled: Boolean) {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                prefs[SettingsPreferencesKeys.WEB_SEARCH_DEFAULT] = enabled
            }
        }
    }

    override suspend fun resetToDefaults() {
        withContext(ioDispatcher) {
            context.minovaSettingsDataStore.edit { prefs ->
                // Restore all preferences to default values without deleting user data
                prefs.clear()
            }
        }
    }

    companion object {
        fun mapPreferencesToSettings(preferences: Preferences): SettingsState {
            val themeStr = preferences[SettingsPreferencesKeys.THEME_MODE]
            val themeMode = when (themeStr?.uppercase()) {
                "DARK" -> ThemeMode.DARK
                "LIGHT" -> ThemeMode.LIGHT
                "SYSTEM" -> ThemeMode.SYSTEM
                else -> ThemeMode.SYSTEM
            }

            val styleStr = preferences[SettingsPreferencesKeys.RESPONSE_STYLE] ?: "BALANCED"
            val responseStyle = ResponseStyle.fromString(styleStr)

            val langStr = preferences[SettingsPreferencesKeys.RESPONSE_LANGUAGE] ?: "AUTO"
            val responseLanguage = ResponseLanguage.fromString(langStr)

            val formatStr = preferences[SettingsPreferencesKeys.RESPONSE_FORMAT] ?: "AUTO"
            val responseFormat = ResponseFormat.fromString(formatStr)

            val rawSpeed = preferences[SettingsPreferencesKeys.SPEECH_SPEED] ?: 1.0f
            val speechSpeed = if (rawSpeed in 0.5f..2.5f) rawSpeed else 1.0f

            val voiceLanguage = preferences[SettingsPreferencesKeys.VOICE_LANGUAGE]?.takeIf { it.isNotBlank() }
                ?: "System Default"

            val rawAiModel = preferences[SettingsPreferencesKeys.AI_MODEL]
            val resolvedAiModel = if (
                rawAiModel.isNullOrBlank() ||
                rawAiModel.contains("Gemini", ignoreCase = true) ||
                rawAiModel.contains("Google", ignoreCase = true)
            ) {
                "MINOVA AI"
            } else {
                rawAiModel
            }

            return SettingsState(
                themeMode = themeMode,
                responseStyle = responseStyle,
                responseLanguage = responseLanguage,
                responseFormat = responseFormat,
                voiceLanguage = voiceLanguage,
                voiceInputEnabled = preferences[SettingsPreferencesKeys.VOICE_INPUT_ENABLED] ?: true,
                voiceOutputEnabled = preferences[SettingsPreferencesKeys.VOICE_OUTPUT_ENABLED] ?: true,
                speechSpeed = speechSpeed,
                enterToSend = preferences[SettingsPreferencesKeys.ENTER_TO_SEND] ?: true,
                showTimestamps = preferences[SettingsPreferencesKeys.SHOW_TIMESTAMPS] ?: false,
                autoFocusComposer = preferences[SettingsPreferencesKeys.AUTO_FOCUS_COMPOSER] ?: false,
                showAttachmentPreview = preferences[SettingsPreferencesKeys.SHOW_ATTACHMENT_PREVIEW] ?: true,
                webSearchDefault = preferences[SettingsPreferencesKeys.WEB_SEARCH_DEFAULT] ?: false,
                aiModel = resolvedAiModel,
                responsePreference = responseStyle.displayName,
                codeHighlighting = preferences[SettingsPreferencesKeys.CODE_HIGHLIGHTING] ?: true,
                ttsVoice = preferences[SettingsPreferencesKeys.TTS_VOICE] ?: "Minova Azure (Natural)"
            )
        }
    }
}

/**
 * Fast, lightweight in-memory settings repository for unit tests and previews.
 */
class InMemorySettingsRepository(
    initialState: SettingsState = SettingsState()
) : SettingsRepository {

    private val _settingsFlow = MutableStateFlow(initialState)
    override val settingsFlow: Flow<SettingsState> = _settingsFlow.asStateFlow()

    override suspend fun getSettings(): SettingsState = _settingsFlow.value

    override suspend fun updateSettings(settings: SettingsState) {
        _settingsFlow.value = settings
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        _settingsFlow.value = _settingsFlow.value.copy(themeMode = themeMode)
    }

    override suspend fun updateResponseStyle(style: ResponseStyle) {
        _settingsFlow.value = _settingsFlow.value.copy(
            responseStyle = style,
            responsePreference = style.displayName
        )
    }

    override suspend fun updateResponseLanguage(language: ResponseLanguage) {
        _settingsFlow.value = _settingsFlow.value.copy(responseLanguage = language)
    }

    override suspend fun updateResponseFormat(format: ResponseFormat) {
        _settingsFlow.value = _settingsFlow.value.copy(responseFormat = format)
    }

    override suspend fun updateVoiceLanguage(language: String) {
        val resolved = language.ifBlank { "System Default" }
        _settingsFlow.value = _settingsFlow.value.copy(voiceLanguage = resolved)
    }

    override suspend fun updateVoiceInputEnabled(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(voiceInputEnabled = enabled)
    }

    override suspend fun updateVoiceOutputEnabled(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(voiceOutputEnabled = enabled)
    }

    override suspend fun updateSpeechSpeed(speed: Float) {
        val sanitized = if (speed in 0.5f..2.5f) speed else 1.0f
        _settingsFlow.value = _settingsFlow.value.copy(speechSpeed = sanitized)
    }

    override suspend fun updateEnterToSend(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(enterToSend = enabled)
    }

    override suspend fun updateShowTimestamps(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(showTimestamps = enabled)
    }

    override suspend fun updateAutoFocusComposer(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(autoFocusComposer = enabled)
    }

    override suspend fun updateShowAttachmentPreview(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(showAttachmentPreview = enabled)
    }

    override suspend fun updateWebSearchDefault(enabled: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(webSearchDefault = enabled)
    }

    override suspend fun resetToDefaults() {
        _settingsFlow.value = SettingsState()
    }
}
