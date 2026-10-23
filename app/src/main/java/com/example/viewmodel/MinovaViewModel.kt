package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MinovaApplication
import com.example.data.model.Attachment
import com.example.data.model.AttachmentOption
import com.example.data.model.AttachmentType
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.BuiltInPrompts
import com.example.data.model.ConflictResolutionStrategy
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.PromptCategory
import com.example.data.model.PromptItem
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.model.SettingsState
import com.example.data.model.SuggestionCard
import com.example.data.model.SyncConsentDecision
import com.example.data.model.SyncStateSnapshot
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.CloudConversationRepository
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.ConversationRepository
import com.example.data.repository.ConversationRepositoryImpl
import com.example.data.repository.InMemoryAuthRepository
import com.example.data.repository.InMemoryCloudConversationRepository
import com.example.data.repository.InMemoryPromptLibraryRepository
import com.example.data.repository.InMemorySettingsRepository
import com.example.data.repository.NetworkResult
import com.example.data.repository.PromptLibraryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.StreamResult
import com.example.data.util.ContentUriRequestBody
import com.example.data.util.ConversationTitleGenerator
import com.example.data.util.DateTimeFormatterHelper
import com.example.data.util.FileValidator
import com.example.data.model.CodeActionType
import com.example.data.model.CodeAssistanceResult
import com.example.data.model.CodeLanguage
import com.example.data.model.CodeSnippet
import com.example.data.model.DocumentContentBlock
import com.example.data.model.FileStudioMode
import com.example.data.model.GeneratedBinaryFile
import com.example.data.model.ImageAspectRatio
import com.example.data.model.LearningGoal
import com.example.data.model.LearningLanguage
import com.example.data.model.LearningLesson
import com.example.data.model.LearningLevel
import com.example.data.model.LearningQuizAttempt
import com.example.data.model.LearningQuizQuestion
import com.example.data.model.LearningSession
import com.example.data.model.LearningSubjectCategory
import com.example.data.model.SpreadsheetWorksheet
import com.example.data.repository.CodeWorkspaceRepository
import com.example.data.repository.FileStudioRepository
import com.example.data.repository.FileStudioRepositoryImpl
import com.example.data.repository.InMemoryCodeWorkspaceRepository
import com.example.data.repository.InMemoryLearningRepository
import com.example.data.repository.LearningRepository
import com.example.data.voice.AndroidTextToSpeechManager
import com.example.data.voice.AndroidVoiceInputManager
import com.example.data.voice.TextToSpeechManager
import com.example.data.voice.TtsState
import com.example.data.voice.VoiceInputManager
import com.example.data.voice.VoiceState
import com.example.navigation.MinovaScreen
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class MinovaViewModel(
    private val chatRepository: ChatRepository = ChatRepositoryImpl(),
    private val conversationRepository: ConversationRepository = try {
        MinovaApplication.instance.conversationRepository
    } catch (_: Throwable) {
        // Fallback or memory repository for tests/previews if application is not attached
        ConversationRepositoryImpl.getInstance()
    },
    private val appContext: Context? = try {
        MinovaApplication.instance.applicationContext
    } catch (_: Throwable) {
        null
    },
    private val settingsRepository: SettingsRepository = try {
        MinovaApplication.instance.settingsRepository
    } catch (_: Throwable) {
        InMemorySettingsRepository()
    },
    private val promptLibraryRepository: PromptLibraryRepository = try {
        MinovaApplication.instance.promptLibraryRepository
    } catch (_: Throwable) {
        InMemoryPromptLibraryRepository()
    },
    private val voiceInputManager: VoiceInputManager? = appContext?.let { AndroidVoiceInputManager(it) },
    private val textToSpeechManager: TextToSpeechManager? = appContext?.let { AndroidTextToSpeechManager(it) },
    private val authRepository: AuthRepository = try {
        MinovaApplication.instance.authRepository
    } catch (_: Throwable) {
        InMemoryAuthRepository()
    },
    private val cloudConversationRepository: CloudConversationRepository = try {
        MinovaApplication.instance.cloudConversationRepository
    } catch (_: Throwable) {
        InMemoryCloudConversationRepository { authRepository.currentUser?.uid }
    },
    private val learningRepository: LearningRepository = try {
        MinovaApplication.instance.learningRepository
    } catch (_: Throwable) {
        InMemoryLearningRepository()
    },
    private val codeWorkspaceRepository: CodeWorkspaceRepository = try {
        MinovaApplication.instance.codeWorkspaceRepository
    } catch (_: Throwable) {
        InMemoryCodeWorkspaceRepository()
    },
    private val fileStudioRepository: FileStudioRepository = try {
        MinovaApplication.instance.fileStudioRepository
    } catch (_: Throwable) {
        FileStudioRepositoryImpl()
    },
    val cloudSyncManager: CloudSyncManager = CloudSyncManager(
        localRepository = conversationRepository,
        cloudRepository = cloudConversationRepository,
        context = appContext
    )
) : ViewModel() {

    // --- AI File Studio State ---
    private val _isFileStudioLoading = MutableStateFlow(false)
    val isFileStudioLoading: StateFlow<Boolean> = _isFileStudioLoading.asStateFlow()

    private val _generatedStudioFile = MutableStateFlow<GeneratedBinaryFile?>(null)
    val generatedStudioFile: StateFlow<GeneratedBinaryFile?> = _generatedStudioFile.asStateFlow()

    private val _fileStudioError = MutableStateFlow<String?>(null)
    val fileStudioError: StateFlow<String?> = _fileStudioError.asStateFlow()

    private val _fileStudioErrorCode = MutableStateFlow<String?>(null)
    val fileStudioErrorCode: StateFlow<String?> = _fileStudioErrorCode.asStateFlow()

    private val _fileStudioSavedConfirmation = MutableStateFlow<String?>(null)
    val fileStudioSavedConfirmation: StateFlow<String?> = _fileStudioSavedConfirmation.asStateFlow()

    // --- Code Workspace State ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val codeSnippets: StateFlow<List<CodeSnippet>> = authRepository.authState
        .flatMapLatest { state ->
            val ownerUid = (state as? AuthState.Authenticated)?.user?.uid
                ?: CodeSnippet.GUEST_OWNER_UID
            codeWorkspaceRepository.observeSnippets(ownerUid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCodeActionLoading = MutableStateFlow(false)
    val isCodeActionLoading: StateFlow<Boolean> = _isCodeActionLoading.asStateFlow()

    private val _codeAssistanceResult = MutableStateFlow<CodeAssistanceResult?>(null)
    val codeAssistanceResult: StateFlow<CodeAssistanceResult?> = _codeAssistanceResult.asStateFlow()

    private val _codeWorkspaceError = MutableStateFlow<String?>(null)
    val codeWorkspaceError: StateFlow<String?> = _codeWorkspaceError.asStateFlow()

    private val _codeWorkspaceFeedback = MutableStateFlow<String?>(null)
    val codeWorkspaceFeedback: StateFlow<String?> = _codeWorkspaceFeedback.asStateFlow()

    // --- Learn Mode State ---
    val learningSessions: StateFlow<List<LearningSession>> = learningRepository
        .observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeLearningSessionId = MutableStateFlow<String?>(null)
    val activeLearningSessionId: StateFlow<String?> = _activeLearningSessionId.asStateFlow()

    private val _currentLessonIndex = MutableStateFlow(0)
    val currentLessonIndex: StateFlow<Int> = _currentLessonIndex.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeLearningSession: StateFlow<LearningSession?> = _activeLearningSessionId
        .flatMapLatest { id ->
            if (id != null) learningRepository.observeSession(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeLessons: StateFlow<List<LearningLesson>> = _activeLearningSessionId
        .flatMapLatest { id ->
            if (id != null) learningRepository.observeLessonsForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeQuizQuestions: StateFlow<List<LearningQuizQuestion>> = _activeLearningSessionId
        .flatMapLatest { id ->
            if (id != null) learningRepository.observeQuizQuestionsForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeQuizAttempts: StateFlow<List<LearningQuizAttempt>> = _activeLearningSessionId
        .flatMapLatest { id ->
            if (id != null) learningRepository.observeQuizAttempts(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGeneratingPlan = MutableStateFlow(false)
    val isGeneratingPlan: StateFlow<Boolean> = _isGeneratingPlan.asStateFlow()

    private val _planGenerationError = MutableStateFlow<String?>(null)
    val planGenerationError: StateFlow<String?> = _planGenerationError.asStateFlow()

    private val _isLessonTutorLoading = MutableStateFlow(false)
    val isLessonTutorLoading: StateFlow<Boolean> = _isLessonTutorLoading.asStateFlow()

    private val _lessonTutorResponse = MutableStateFlow<String?>(null)
    val lessonTutorResponse: StateFlow<String?> = _lessonTutorResponse.asStateFlow()

    private val _lessonTutorError = MutableStateFlow<String?>(null)
    val lessonTutorError: StateFlow<String?> = _lessonTutorError.asStateFlow()

    private val _quizLastAnswerResult = MutableStateFlow<Boolean?>(null)
    val quizLastAnswerResult: StateFlow<Boolean?> = _quizLastAnswerResult.asStateFlow()

    private val _currentScreen = MutableStateFlow<MinovaScreen>(MinovaScreen.Splash)
    val currentScreen: StateFlow<MinovaScreen> = _currentScreen.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private var streamingJob: Job? = null
    private var messageCollectionJob: Job? = null
    private var lastFailedPrompt: String? = null
    private var lastFailedAttachments: List<Attachment> = emptyList()
    private val pendingMessageIdsToDelete = mutableListOf<String>()
    private var lastFailedAttachment: Attachment?
        get() = lastFailedAttachments.firstOrNull()
        set(value) {
            lastFailedAttachments = if (value != null) listOf(value) else emptyList()
        }

    // Persistent Active Conversations from Room Database
    val conversations: StateFlow<List<Conversation>> = conversationRepository
        .getActiveConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allConversations: StateFlow<List<Conversation>> = conversationRepository
        .getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pinnedConversations: StateFlow<List<Conversation>> = conversationRepository
        .getPinnedConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedConversations: StateFlow<List<Conversation>> = conversationRepository
        .getArchivedConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeMessages: StateFlow<List<Message>> = _activeMessages.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _editingUserMessageId = MutableStateFlow<String?>(null)
    val editingUserMessageId: StateFlow<String?> = _editingUserMessageId.asStateFlow()

    private val _isAttachmentSheetVisible = MutableStateFlow(false)
    val isAttachmentSheetVisible: StateFlow<Boolean> = _isAttachmentSheetVisible.asStateFlow()

    private val _selectedAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val selectedAttachments: StateFlow<List<Attachment>> = _selectedAttachments.asStateFlow()

    private val _selectedAttachment = MutableStateFlow<Attachment?>(null)
    val selectedAttachment: StateFlow<Attachment?> = _selectedAttachment.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceInputManager?.voiceState
        ?: MutableStateFlow(VoiceState.IDLE).asStateFlow()

    val ttsState: StateFlow<TtsState> = textToSpeechManager?.ttsState
        ?: MutableStateFlow(TtsState.IDLE).asStateFlow()

    val ttsActiveMessageId: StateFlow<String?> = textToSpeechManager?.activeMessageId
        ?: MutableStateFlow<String?>(null).asStateFlow()

    val isTtsSpeaking: StateFlow<Boolean> = textToSpeechManager?.isSpeaking
        ?: MutableStateFlow(false).asStateFlow()

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _voiceDurationSeconds = MutableStateFlow(0)
    val voiceDurationSeconds: StateFlow<Int> = _voiceDurationSeconds.asStateFlow()
    private var voiceTimerJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()


    // Dynamic Search Results across titles and message contents
    val searchResults: StateFlow<List<Conversation>> = _searchQuery
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                conversationRepository.getActiveConversations()
            } else {
                conversationRepository.searchConversations(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val _activeResponseStyle = MutableStateFlow(ResponseStyle.BALANCED)
    val activeResponseStyle: StateFlow<ResponseStyle> = _activeResponseStyle.asStateFlow()

    private val _activeResponseLanguage = MutableStateFlow(ResponseLanguage.AUTO)
    val activeResponseLanguage: StateFlow<ResponseLanguage> = _activeResponseLanguage.asStateFlow()

    private val _activeResponseFormat = MutableStateFlow(ResponseFormat.AUTO)
    val activeResponseFormat: StateFlow<ResponseFormat> = _activeResponseFormat.asStateFlow()

    private val _isWebSearchActive = MutableStateFlow(false)
    val isWebSearchActive: StateFlow<Boolean> = _isWebSearchActive.asStateFlow()

    private var hasUserExplicitlyToggledWebSearch: Boolean = false

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState
    val syncState: StateFlow<SyncStateSnapshot> = cloudSyncManager.syncState

    private val _accountFeedbackMessage = MutableStateFlow<String?>(null)
    val accountFeedbackMessage: StateFlow<String?> = _accountFeedbackMessage.asStateFlow()

    val suggestionCards: List<SuggestionCard> = listOf(
        SuggestionCard(
            id = "explain",
            title = "Explain a concept",
            description = "Break down complex topics into clean intuitive mental models.",
            prompt = "Explain how distributed cache consensus works with clear examples."
        ),
        SuggestionCard(
            id = "write_code",
            title = "Write code",
            description = "Generate clean, modern, and production-ready code algorithms.",
            prompt = "Write an idiomatic Kotlin Coroutine flow pipeline with backpressure handling."
        ),
        SuggestionCard(
            id = "debug_code",
            title = "Debug my code",
            description = "Analyze stack traces, memory leaks, and subtle concurrency bugs.",
            prompt = "Help me debug a concurrent modification exception in multi-threaded code."
        ),
        SuggestionCard(
            id = "study",
            title = "Help me study",
            description = "Structure comprehensive review plans, flashcards, and Q&A sessions.",
            prompt = "Help me study computer networking protocols from transport to application layer."
        )
    )

    init {
        // Automatically observe and apply persistent DataStore user preferences
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { persistentSettings ->
                val prevWebSearchDefault = _settings.value.webSearchDefault
                _settings.value = persistentSettings
                _themeMode.value = persistentSettings.themeMode
                textToSpeechManager?.setSpeechRate(persistentSettings.speechSpeed)
                if (!hasUserExplicitlyToggledWebSearch || prevWebSearchDefault != persistentSettings.webSearchDefault) {
                    _isWebSearchActive.value = persistentSettings.webSearchDefault
                }
                if (_activeConversationId.value == null) {
                    _activeResponseStyle.value = persistentSettings.responseStyle
                    _activeResponseLanguage.value = persistentSettings.responseLanguage
                    _activeResponseFormat.value = persistentSettings.responseFormat
                }
            }
        }

        // Automatically restore the latest active conversation on app start if available
        viewModelScope.launch {
            val list = conversationRepository.getActiveConversations().first()
            if (_activeConversationId.value == null && list.isNotEmpty()) {
                selectConversation(list.first().id)
            }
        }

        // Observe authentication state and synchronize with CloudSyncManager
        viewModelScope.launch {
            var lastUid: String? = null
            authRepository.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        val user = state.user
                        _userProfile.update { current ->
                            current.copy(
                                name = user.displayName,
                                email = user.email,
                                tier = if (user.providerId == "google.com") {
                                    "Google Account • Cloud Sync"
                                } else {
                                    "Verified Account • Cloud Sync"
                                }
                            )
                        }
                        if (lastUid != user.uid) {
                            lastUid = user.uid
                            cloudSyncManager.onUserAuthenticated(user.uid)
                        }
                    }
                    is AuthState.Guest -> {
                        _userProfile.update { current ->
                            current.copy(
                                name = "Guest User",
                                email = "Local-only mode • Device storage",
                                tier = "LOCAL MODE • Not Signed In"
                            )
                        }
                        if (lastUid != null) {
                            lastUid = null
                            cloudSyncManager.onUserSignedOut()
                        }
                    }
                    is AuthState.Error -> {
                        val user = state.currentUser
                        if (user == null) {
                            _userProfile.update { current ->
                                current.copy(
                                    name = "Guest User",
                                    email = "Local-only mode • Device storage",
                                    tier = "LOCAL MODE • Not Signed In"
                                )
                            }
                        }
                    }
                    is AuthState.Loading -> {}
                }
            }
        }

        // Keep conversation count in UserProfile synchronized with local Room count
        viewModelScope.launch {
            conversationRepository.getAllConversations().collect { convs ->
                _userProfile.update { it.copy(conversationsCount = convs.size) }
            }
        }
    }

    fun navigateTo(screen: MinovaScreen) {
        _currentScreen.value = screen
    }

    fun dismissSplash() {
        _currentScreen.value = MinovaScreen.Chat(_activeConversationId.value)
    }

    fun startNewChat() {
        textToSpeechManager?.stop()
        messageCollectionJob?.cancel()
        messageCollectionJob = null
        _activeConversationId.value = null
        _activeMessages.value = emptyList()
        _composerText.value = ""
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null
        _activeResponseStyle.value = _settings.value.responseStyle
        _activeResponseLanguage.value = _settings.value.responseLanguage
        _activeResponseFormat.value = _settings.value.responseFormat
        hasUserExplicitlyToggledWebSearch = false
        _isWebSearchActive.value = _settings.value.webSearchDefault
        _currentScreen.value = MinovaScreen.Chat(null)
    }

    fun selectConversation(id: String) {
        if (_activeConversationId.value == id && messageCollectionJob?.isActive == true) {
            _currentScreen.value = MinovaScreen.Chat(id)
            return
        }

        textToSpeechManager?.stop()
        _activeConversationId.value = id
        _composerText.value = ""
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null
        _currentScreen.value = MinovaScreen.Chat(id)

        // Restore conversation-specific response controls from Room if saved, else fall back to global settings
        viewModelScope.launch {
            val conv = conversationRepository.getConversationById(id)
            _activeResponseStyle.value = conv?.responseStyle ?: _settings.value.responseStyle
            _activeResponseLanguage.value = conv?.responseLanguage ?: _settings.value.responseLanguage
            _activeResponseFormat.value = conv?.responseFormat ?: _settings.value.responseFormat
        }

        // Observe persistent messages from Room
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            conversationRepository.getMessagesForConversation(id).collect { messages ->
                // Only update if we are not actively in the middle of streaming a response
                if (!_chatUiState.value.isStreaming) {
                    _activeMessages.value = messages
                }
            }
        }
    }

    fun onComposerTextChange(text: String) {
        _composerText.value = text
    }

    fun selectSuggestion(prompt: String) {
        _composerText.value = prompt
    }

    fun sendMessage() {
        if (_chatUiState.value.isStreaming) return

        val text = _composerText.value.trim()
        val attachments = if (_selectedAttachments.value.isNotEmpty()) {
            _selectedAttachments.value
        } else if (_selectedAttachment.value != null) {
            listOf(_selectedAttachment.value!!)
        } else {
            emptyList()
        }
        if (text.isEmpty() && attachments.isEmpty()) return

        val nowFormatted = DateTimeFormatterHelper.formatTime()
        val isNewConversation = _activeConversationId.value == null
        val conversationId = _activeConversationId.value ?: run {
            val newId = UUID.randomUUID().toString()
            _activeConversationId.value = newId
            newId
        }

        // Clear composer state immediately
        _composerText.value = ""
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null

        val idsToDeleteBeforeSend = mutableListOf<String>()
        if (pendingMessageIdsToDelete.isNotEmpty()) {
            idsToDeleteBeforeSend.addAll(pendingMessageIdsToDelete)
            pendingMessageIdsToDelete.clear()
        }

        val editingId = _editingUserMessageId.value
        _editingUserMessageId.value = null
        if (editingId != null) {
            val currentList = _activeMessages.value
            val editIndex = currentList.indexOfFirst { it.id == editingId }
            if (editIndex != -1) {
                val messagesToDelete = currentList.subList(editIndex, currentList.size)
                idsToDeleteBeforeSend.addAll(messagesToDelete.map { it.id })
                _activeMessages.value = currentList.subList(0, editIndex)
            }
        }

        if (attachments.isNotEmpty()) {
            val primaryAttachment = attachments.first()
            val prompt = if (text.isNotBlank()) {
                text
            } else {
                if (attachments.size == 1) {
                    when (primaryAttachment.type) {
                        AttachmentType.IMAGE, AttachmentType.PHOTO, AttachmentType.CAMERA -> "Analyze this image and describe its key contents."
                        else -> "Please analyze this file and summarize its key insights."
                    }
                } else {
                    "Please analyze these ${attachments.size} attached files and synthesize their key insights."
                }
            }

            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = MessageSender.USER,
                content = text.ifBlank { prompt },
                timestamp = nowFormatted,
                status = MessageStatus.SENT,
                attachmentType = primaryAttachment.type,
                attachmentName = if (attachments.size > 1) "${attachments.size} files" else primaryAttachment.fileName,
                attachment = primaryAttachment,
                attachments = attachments
            )

            val assistantMessageId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantMessageId,
                conversationId = conversationId,
                sender = MessageSender.ASSISTANT,
                content = "",
                timestamp = nowFormatted,
                status = MessageStatus.STREAMING
            )

            lastFailedPrompt = text
            lastFailedAttachments = attachments

            _chatUiState.value = ChatUiState.Loading
            _activeMessages.value = _activeMessages.value.filterNot { it.id in idsToDeleteBeforeSend.toSet() } + userMessage + initialAssistantMessage

            streamingJob?.cancel()
            streamingJob = viewModelScope.launch {
                // If starting a new conversation, create it in Room with automatic title
                if (isNewConversation) {
                    val autoTitle = ConversationTitleGenerator.generateTitle(
                        text.ifBlank {
                            if (attachments.size == 1) primaryAttachment.fileName else "${attachments.size} files"
                        }
                    )
                    conversationRepository.createConversation(
                        id = conversationId,
                        title = autoTitle,
                        responseStyle = _activeResponseStyle.value,
                        responseLanguage = _activeResponseLanguage.value,
                        responseFormat = _activeResponseFormat.value
                    )
                    cloudSyncManager.registerLocalConversationCreated(conversationId)
                    _activeConversationId.value = conversationId
                    messageCollectionJob?.cancel()
                    messageCollectionJob = viewModelScope.launch {
                        conversationRepository.getMessagesForConversation(conversationId).collect { msgs ->
                            if (!_chatUiState.value.isStreaming) {
                                _activeMessages.value = msgs
                            }
                        }
                    }
                }

                for (msgId in idsToDeleteBeforeSend) {
                    conversationRepository.deleteMessage(msgId)
                    cloudSyncManager.onLocalMessageDeleted(conversationId, msgId)
                }

                // Check URI accessibility for all attachments before starting upload
                val context = appContext
                if (context != null) {
                    val hasInaccessible = attachments.any { !FileValidator.isUriAccessible(context, it.uri) }
                    if (hasInaccessible) {
                        _chatUiState.value = ChatUiState.Error("Unable to read one or more attached files. Please choose another file.", canRetry = false)
                        return@launch
                    }
                }

                // Fetch prior valid turns from database
                val priorHistory = conversationRepository.getMessagesSync(conversationId)
                    .filter { it.status != MessageStatus.ERROR && it.status != MessageStatus.STREAMING && it.content.isNotBlank() }

                // Save user message (with attachment metadata) to persistent Room database
                conversationRepository.saveMessage(userMessage)

                val optimisticList = priorHistory + userMessage + initialAssistantMessage
                _activeMessages.value = optimisticList

                // Sequential file analysis with progressive state updates
                val analysisOutputs = mutableListOf<String>()
                var failureMessage: String? = null

                for ((index, att) in attachments.withIndex()) {
                    val stepPrefix = if (attachments.size > 1) "file ${index + 1} of ${attachments.size}: ${att.fileName}" else "attachment: ${att.fileName}"

                    _chatUiState.value = ChatUiState.Analyzing("Analyzing $stepPrefix...")

                    val filePart: MultipartBody.Part = if (context != null) {
                        val uri = Uri.parse(att.uri)
                        val uriRequestBody = ContentUriRequestBody(
                            contentResolver = context.contentResolver,
                            uri = uri,
                            contentType = att.mimeType.toMediaTypeOrNull(),
                            contentLength = att.sizeBytes
                        )
                        MultipartBody.Part.createFormData("file", att.fileName, uriRequestBody)
                    } else {
                        val emptyBody = ByteArray(0).toRequestBody(att.mimeType.toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("file", att.fileName, emptyBody)
                    }

                    val specificPrompt = if (attachments.size > 1) {
                        "$prompt\n[File ${index + 1}/${attachments.size}: ${att.fileName}]"
                    } else {
                        prompt
                    }

                    val analysisResult = chatRepository.analyzeFileMessage(
                        filePart = filePart,
                        message = specificPrompt,
                        conversationHistory = priorHistory,
                        responseStyle = _activeResponseStyle.value,
                        responseLanguage = _activeResponseLanguage.value,
                        responseFormat = _activeResponseFormat.value
                    )

                    when (analysisResult) {
                        is NetworkResult.Success -> {
                            analysisOutputs.add(analysisResult.data)
                        }
                        is NetworkResult.Error -> {
                            failureMessage = analysisResult.userMessage
                            break
                        }
                    }
                }

                if (failureMessage != null) {
                    val errorMsg = Message(
                        id = assistantMessageId,
                        conversationId = conversationId,
                        sender = MessageSender.ASSISTANT,
                        content = failureMessage,
                        timestamp = DateTimeFormatterHelper.formatTime(),
                        status = MessageStatus.ERROR,
                        errorMessage = failureMessage
                    )
                    conversationRepository.saveMessage(errorMsg)
                    _activeMessages.value = _activeMessages.value.map { msg ->
                        if (msg.id == assistantMessageId) errorMsg else msg
                    }
                    _chatUiState.value = ChatUiState.Error(failureMessage)
                    return@launch
                }

                val finalOutput = if (analysisOutputs.size == 1) {
                    analysisOutputs.first()
                } else {
                    buildString {
                        attachments.zip(analysisOutputs).forEachIndexed { idx, (att, out) ->
                            if (idx > 0) appendLine("\n---\n")
                            appendLine("### ${att.fileName}")
                            appendLine(out)
                        }
                    }
                }

                val completedAssistantMsg = Message(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    sender = MessageSender.ASSISTANT,
                    content = finalOutput,
                    timestamp = DateTimeFormatterHelper.formatTime(),
                    status = MessageStatus.SENT
                )
                conversationRepository.saveMessage(completedAssistantMsg)
                conversationRepository.updateConversationLastMessage(conversationId, finalOutput)
                cloudSyncManager.syncLocalConversationIfAuthorized(conversationId)

                val completedList = _activeMessages.value.map { msg ->
                    if (msg.id == assistantMessageId) completedAssistantMsg else msg
                }
                _activeMessages.value = completedList

                lastFailedPrompt = null
                lastFailedAttachments = emptyList()
                _chatUiState.value = ChatUiState.Success(finalOutput)
            }
        } else {
            // Text-only streaming workflow (UNCHANGED)
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = MessageSender.USER,
                content = text,
                timestamp = nowFormatted,
                status = MessageStatus.SENT,
                attachmentType = null,
                attachmentName = null
            )

            val assistantMessageId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantMessageId,
                conversationId = conversationId,
                sender = MessageSender.ASSISTANT,
                content = "",
                timestamp = nowFormatted,
                status = MessageStatus.STREAMING
            )

            lastFailedPrompt = text
            lastFailedAttachment = null

            // Set UI loading state and optimistic messages immediately
            _chatUiState.value = ChatUiState.Loading
            _activeMessages.value = _activeMessages.value.filterNot { it.id in idsToDeleteBeforeSend.toSet() } + userMessage + initialAssistantMessage

            streamingJob?.cancel()
            streamingJob = viewModelScope.launch {
                // If starting a new conversation, create it in Room with automatic title
                if (isNewConversation) {
                    val autoTitle = ConversationTitleGenerator.generateTitle(text)
                    conversationRepository.createConversation(
                        id = conversationId,
                        title = autoTitle,
                        responseStyle = _activeResponseStyle.value,
                        responseLanguage = _activeResponseLanguage.value,
                        responseFormat = _activeResponseFormat.value
                    )
                    cloudSyncManager.registerLocalConversationCreated(conversationId)
                    _activeConversationId.value = conversationId
                    // Start observing messages for the new conversation on viewModelScope
                    messageCollectionJob?.cancel()
                    messageCollectionJob = viewModelScope.launch {
                        conversationRepository.getMessagesForConversation(conversationId).collect { msgs ->
                            if (!_chatUiState.value.isStreaming) {
                                _activeMessages.value = msgs
                            }
                        }
                    }
                }

                for (msgId in idsToDeleteBeforeSend) {
                    conversationRepository.deleteMessage(msgId)
                    cloudSyncManager.onLocalMessageDeleted(conversationId, msgId)
                }

                // Fetch prior valid turns from database
                val priorHistory = conversationRepository.getMessagesSync(conversationId)
                    .filter { it.status != MessageStatus.ERROR && it.status != MessageStatus.STREAMING && it.content.isNotBlank() }

                // Save user message to persistent Room database
                conversationRepository.saveMessage(userMessage)

                // Setup optimistic in-memory active list for responsive streaming UI
                val optimisticList = priorHistory + userMessage + initialAssistantMessage
                _activeMessages.value = optimisticList

                var accumulatedText = ""

                chatRepository.sendChatMessageStream(
                    message = text,
                    conversationHistory = priorHistory,
                    responseStyle = _activeResponseStyle.value,
                    responseLanguage = _activeResponseLanguage.value,
                    responseFormat = _activeResponseFormat.value,
                    webSearch = _isWebSearchActive.value
                ).collect { result ->
                    when (result) {
                        is StreamResult.Chunk -> {
                            accumulatedText += result.text
                            val currentList = _activeMessages.value
                            val hasTarget = currentList.any { it.id == assistantMessageId }
                            val withChunk = if (hasTarget) {
                                currentList.map { msg ->
                                    if (msg.id == assistantMessageId) {
                                        msg.copy(
                                            content = accumulatedText,
                                            status = MessageStatus.STREAMING
                                        )
                                    } else {
                                        msg
                                    }
                                }
                            } else {
                                currentList + initialAssistantMessage.copy(
                                    content = accumulatedText,
                                    status = MessageStatus.STREAMING
                                )
                            }
                            _activeMessages.value = withChunk
                            _chatUiState.value = ChatUiState.Streaming(accumulatedText)
                        }

                        is StreamResult.Complete -> {
                            val finalOutput = result.fullText.ifEmpty { accumulatedText }

                            // Create completed assistant message and save to persistent Room database
                            val completedAssistantMsg = Message(
                                id = assistantMessageId,
                                conversationId = conversationId,
                                sender = MessageSender.ASSISTANT,
                                content = finalOutput,
                                timestamp = DateTimeFormatterHelper.formatTime(),
                                status = MessageStatus.SENT
                            )
                            conversationRepository.saveMessage(completedAssistantMsg)
                            conversationRepository.updateConversationLastMessage(conversationId, finalOutput)
                            cloudSyncManager.syncLocalConversationIfAuthorized(conversationId)

                            val completedList = _activeMessages.value.map { msg ->
                                if (msg.id == assistantMessageId) completedAssistantMsg else msg
                            }
                            _activeMessages.value = completedList

                            lastFailedPrompt = null
                            _chatUiState.value = ChatUiState.Success(finalOutput)
                        }

                        is StreamResult.Error -> {
                            val isWebSearchUnavailable = result.errorCode == "WEB_SEARCH_UNAVAILABLE"
                            val errorMetadata = if (isWebSearchUnavailable) "WEB_SEARCH_UNAVAILABLE: ${result.userMessage}" else result.userMessage

                            if (accumulatedText.isNotBlank()) {
                                // Partial output arrived: save partial response with interruption notice
                                val interruptedText = accumulatedText + "\n\n⚠️ Streaming interrupted: ${result.userMessage}"
                                val interruptedMsg = Message(
                                    id = assistantMessageId,
                                    conversationId = conversationId,
                                    sender = MessageSender.ASSISTANT,
                                    content = interruptedText,
                                    timestamp = DateTimeFormatterHelper.formatTime(),
                                    status = MessageStatus.SENT,
                                    errorMessage = errorMetadata
                                )
                                conversationRepository.saveMessage(interruptedMsg)

                                val withError = _activeMessages.value.map { msg ->
                                    if (msg.id == assistantMessageId) interruptedMsg else msg
                                }
                                _activeMessages.value = withError
                            } else {
                                // Failed upfront before any chunks arrived: keep user & error message in Room, display retry UI
                                val errorMsg = initialAssistantMessage.copy(
                                    content = result.userMessage,
                                    timestamp = DateTimeFormatterHelper.formatTime(),
                                    status = MessageStatus.ERROR,
                                    errorMessage = errorMetadata
                                )
                                conversationRepository.saveMessage(errorMsg)
                                val withError = _activeMessages.value.map { msg ->
                                    if (msg.id == assistantMessageId) errorMsg else msg
                                }
                                _activeMessages.value = withError
                            }
                            _chatUiState.value = ChatUiState.Error(result.userMessage)
                        }
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null

        val conversationId = _activeConversationId.value
        if (conversationId != null) {
            viewModelScope.launch {
                val currentList = _activeMessages.value
                val streamingMsg = currentList.find { it.status == MessageStatus.STREAMING }
                if (streamingMsg != null) {
                    val finalContent = if (streamingMsg.content.isBlank()) "(Response stopped)" else streamingMsg.content
                    val stoppedMsg = streamingMsg.copy(content = finalContent, status = MessageStatus.SENT)
                    conversationRepository.saveMessage(stoppedMsg)

                    val updatedList = currentList.map { msg ->
                        if (msg.id == streamingMsg.id) stoppedMsg else msg
                    }
                    _activeMessages.value = updatedList
                }
            }
        }

        _chatUiState.value = ChatUiState.Idle
    }

    fun retryLastMessage() {
        if (_chatUiState.value.isStreaming) return

        val currentList = _activeMessages.value
        val lastUserMsg = currentList.lastOrNull { it.sender == MessageSender.USER }
        val failedAttachments = lastFailedAttachments.ifEmpty { lastUserMsg?.allAttachments ?: emptyList() }

        val toRemoveIds = mutableListOf<String>()
        var idx = currentList.lastIndex
        while (idx >= 0 && currentList[idx].status == MessageStatus.ERROR) {
            toRemoveIds.add(currentList[idx].id)
            idx--
        }
        if (idx >= 0 && currentList[idx].sender == MessageSender.USER) {
            toRemoveIds.add(currentList[idx].id)
        }

        if (failedAttachments.isNotEmpty()) {
            val context = appContext
            val accessible = context == null || failedAttachments.all { FileValidator.isUriAccessible(context, it.uri) }
            if (!accessible) {
                _chatUiState.value = ChatUiState.Error(
                    message = "File access expired. Please attach the file again.",
                    canRetry = false
                )
                return
            }
            if (_activeConversationId.value != null && toRemoveIds.isNotEmpty()) {
                pendingMessageIdsToDelete.addAll(toRemoveIds)
                _activeMessages.value = currentList.filterNot { it.id in toRemoveIds.toSet() }
            }
            _selectedAttachments.value = failedAttachments
            _selectedAttachment.value = failedAttachments.firstOrNull()
            _composerText.value = lastFailedPrompt ?: lastUserMsg?.content ?: ""
            sendMessage()
            return
        }

        val prompt = lastFailedPrompt?.takeIf { it.isNotBlank() }
            ?: lastUserMsg?.content?.takeIf { it.isNotBlank() }
            ?: return

        if (_activeConversationId.value != null && toRemoveIds.isNotEmpty()) {
            pendingMessageIdsToDelete.addAll(toRemoveIds)
            _activeMessages.value = currentList.filterNot { it.id in toRemoveIds.toSet() }
        }
        _composerText.value = prompt
        sendMessage()
    }

    fun clearError() {
        _chatUiState.value = ChatUiState.Idle
    }

    fun openAttachmentSheet(show: Boolean) {
        _isAttachmentSheetVisible.value = show
    }

    fun selectAttachment(attachment: Attachment?) {
        if (attachment == null) {
            _selectedAttachments.value = emptyList()
            _selectedAttachment.value = null
        } else {
            addAttachment(attachment)
        }
        _isAttachmentSheetVisible.value = false
    }

    fun addAttachment(attachment: Attachment) {
        if (_selectedAttachments.value.size >= FileValidator.MAX_ATTACHMENTS_PER_MESSAGE) {
            _chatUiState.value = ChatUiState.Error(
                message = "Maximum 5 attachments allowed per message.",
                canRetry = false
            )
            return
        }
        if (_selectedAttachments.value.any { it.uri == attachment.uri }) {
            return
        }
        _selectedAttachments.update { it + attachment }
        _selectedAttachment.value = _selectedAttachments.value.firstOrNull()
    }

    fun addAttachments(newAttachments: List<Attachment>) {
        val current = _selectedAttachments.value
        val spaceLeft = FileValidator.MAX_ATTACHMENTS_PER_MESSAGE - current.size
        if (spaceLeft <= 0) {
            _chatUiState.value = ChatUiState.Error(
                message = "Maximum 5 attachments allowed per message.",
                canRetry = false
            )
            return
        }
        val allowed = newAttachments.filter { newAtt -> current.none { it.uri == newAtt.uri } }.take(spaceLeft)
        if (newAttachments.size > spaceLeft) {
            _chatUiState.value = ChatUiState.Error(
                message = "Maximum 5 attachments allowed per message.",
                canRetry = false
            )
        }
        if (allowed.isNotEmpty()) {
            _selectedAttachments.update { it + allowed }
            _selectedAttachment.value = _selectedAttachments.value.firstOrNull()
        }
    }

    fun removeAttachment(attachmentId: String) {
        _selectedAttachments.update { list -> list.filterNot { it.attachmentId == attachmentId } }
        _selectedAttachment.value = _selectedAttachments.value.firstOrNull()
    }

    fun selectAttachment(option: AttachmentOption?) {
        if (option == null) {
            _selectedAttachments.value = emptyList()
            _selectedAttachment.value = null
        }
        _isAttachmentSheetVisible.value = false
    }

    fun onAttachmentError(errorMessage: String) {
        _chatUiState.value = ChatUiState.Error(message = errorMessage, canRetry = false)
    }

    fun clearSelectedAttachment() {
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null
    }

    fun clearSelectedAttachments() {
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null
    }

    fun startVoiceRecording() {
        textToSpeechManager?.stop()
        if (voiceInputManager == null) {
            _chatUiState.value = ChatUiState.Error(
                message = "Speech recognition service is not available.",
                canRetry = false
            )
            return
        }

        if (!voiceInputManager.isRecognitionAvailable()) {
            _chatUiState.value = ChatUiState.Error(
                message = "Speech recognition is not available on this device.",
                canRetry = false
            )
            return
        }

        _isVoiceActive.value = true
        _voiceDurationSeconds.value = 0
        voiceTimerJob?.cancel()
        voiceTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _voiceDurationSeconds.update { it + 1 }
            }
        }

        val baseText = _composerText.value
        val lang = _settings.value.voiceLanguage

        voiceInputManager.startListening(
            baseText = baseText,
            language = lang,
            onTextUpdated = { updatedText ->
                _composerText.value = updatedText
            },
            onError = { error ->
                _chatUiState.value = ChatUiState.Error(message = error, canRetry = false)
                _isVoiceActive.value = false
                voiceTimerJob?.cancel()
            },
            onComplete = { finalMergedText ->
                _composerText.value = finalMergedText
                _isVoiceActive.value = false
                voiceTimerJob?.cancel()
            }
        )
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        _isVoiceActive.value = false
        _voiceDurationSeconds.value = 0
        voiceInputManager?.cancel()
    }

    fun stopVoiceRecording() {
        voiceTimerJob?.cancel()
        _isVoiceActive.value = false
        _voiceDurationSeconds.value = 0
        voiceInputManager?.stopListening()
    }

    /**
     * Toggles speech playback for a specific assistant message.
     * If this message is already playing, stops it.
     * If another message is playing, stops that one and begins reading this message.
     */
    fun toggleSpeech(messageId: String, content: String) {
        if (!_settings.value.voiceOutputEnabled) {
            _chatUiState.value = ChatUiState.Error(
                message = "Voice output is turned off in Settings.",
                canRetry = false
            )
            return
        }

        if (textToSpeechManager == null) {
            _chatUiState.value = ChatUiState.Error(
                message = "Text-to-Speech service is not available on this device.",
                canRetry = false
            )
            return
        }

        val preferredLang = _settings.value.voiceLanguage
        textToSpeechManager.setSpeechRate(_settings.value.speechSpeed)
        textToSpeechManager.speak(
            messageId = messageId,
            text = content,
            preferredLanguage = preferredLang,
            onError = { errorText ->
                _chatUiState.value = ChatUiState.Error(
                    message = errorText,
                    canRetry = false
                )
            }
        )
    }

    /**
     * Stops any currently active speech.
     */
    fun stopSpeech() {
        textToSpeechManager?.stop()
    }

    override fun onCleared() {
        super.onCleared()
        voiceTimerJob?.cancel()
        voiceInputManager?.destroy()
        textToSpeechManager?.shutdown()
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            conversationRepository.togglePin(id)
            cloudSyncManager.syncLocalConversationIfAuthorized(id)
        }
    }

    fun toggleArchive(id: String) {
        viewModelScope.launch {
            conversationRepository.toggleArchive(id)
            cloudSyncManager.syncLocalConversationIfAuthorized(id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            cloudSyncManager.onLocalConversationDeleted(id)
            if (_activeConversationId.value == id) {
                startNewChat()
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        _settings.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.updateThemeMode(mode)
        }
    }

    fun updateSettings(newSettings: SettingsState) {
        val prevWebSearchDefault = _settings.value.webSearchDefault
        _settings.value = newSettings
        _themeMode.value = newSettings.themeMode
        textToSpeechManager?.setSpeechRate(newSettings.speechSpeed)
        if (!hasUserExplicitlyToggledWebSearch || prevWebSearchDefault != newSettings.webSearchDefault) {
            _isWebSearchActive.value = newSettings.webSearchDefault
        }
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
        }
    }

    fun toggleWebSearch(enabled: Boolean? = null) {
        hasUserExplicitlyToggledWebSearch = true
        _isWebSearchActive.value = enabled ?: !_isWebSearchActive.value
    }

    fun continueWithoutWebSearch() {
        hasUserExplicitlyToggledWebSearch = true
        _isWebSearchActive.value = false
        retryLastMessage()
    }

    fun setActiveResponseStyle(style: ResponseStyle) {
        _activeResponseStyle.value = style
        val convId = _activeConversationId.value
        if (convId != null) {
            viewModelScope.launch {
                conversationRepository.updateConversationPreferences(
                    id = convId,
                    style = style,
                    language = _activeResponseLanguage.value,
                    format = _activeResponseFormat.value
                )
            }
        }
    }

    fun setActiveResponseLanguage(language: ResponseLanguage) {
        _activeResponseLanguage.value = language
        val convId = _activeConversationId.value
        if (convId != null) {
            viewModelScope.launch {
                conversationRepository.updateConversationPreferences(
                    id = convId,
                    style = _activeResponseStyle.value,
                    language = language,
                    format = _activeResponseFormat.value
                )
            }
        }
    }

    fun setActiveResponseFormat(format: ResponseFormat) {
        _activeResponseFormat.value = format
        val convId = _activeConversationId.value
        if (convId != null) {
            viewModelScope.launch {
                conversationRepository.updateConversationPreferences(
                    id = convId,
                    style = _activeResponseStyle.value,
                    language = _activeResponseLanguage.value,
                    format = format
                )
            }
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            hasUserExplicitlyToggledWebSearch = false
            _isWebSearchActive.value = false
            if (_activeConversationId.value == null) {
                _activeResponseStyle.value = ResponseStyle.BALANCED
                _activeResponseLanguage.value = ResponseLanguage.AUTO
                _activeResponseFormat.value = ResponseFormat.AUTO
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startEditMessage(message: Message) {
        if (_chatUiState.value.isStreaming) return
        _editingUserMessageId.value = message.id
        _composerText.value = message.content
        if (message.allAttachments.isNotEmpty()) {
            _selectedAttachments.value = message.allAttachments
            _selectedAttachment.value = message.allAttachments.firstOrNull()
        } else {
            _selectedAttachments.value = emptyList()
            _selectedAttachment.value = null
        }
    }

    fun cancelEditMessage() {
        _editingUserMessageId.value = null
        _composerText.value = ""
        _selectedAttachments.value = emptyList()
        _selectedAttachment.value = null
    }

    fun togglePinMessage(messageId: String) {
        viewModelScope.launch {
            conversationRepository.toggleMessagePin(messageId)
            _activeMessages.update { list ->
                list.map { if (it.id == messageId) it.copy(isPinned = !it.isPinned) else it }
            }
            _activeConversationId.value?.let { convId ->
                cloudSyncManager.syncLocalConversationIfAuthorized(convId)
            }
        }
    }

    fun togglePinMessage(message: Message) = togglePinMessage(message.id)

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val convId = _activeConversationId.value
            conversationRepository.deleteMessage(messageId)
            _activeMessages.update { list ->
                list.filterNot { it.id == messageId }
            }
            if (convId != null) {
                cloudSyncManager.onLocalMessageDeleted(convId, messageId)
            }
        }
    }

    fun deleteMessage(message: Message) = deleteMessage(message.id)

    fun regenerateMessage(message: Message) = regenerateMessage(message.id)

    fun regenerateMessage(assistantMessageId: String) {
        if (_chatUiState.value.isStreaming) return
        val currentMessages = _activeMessages.value
        val assistantIndex = currentMessages.indexOfFirst { it.id == assistantMessageId }
        if (assistantIndex == -1) return

        val userMsgIndex = currentMessages.subList(0, assistantIndex).indexOfLast { it.sender == MessageSender.USER }
        if (userMsgIndex == -1) return
        val userMsg = currentMessages[userMsgIndex]
        val conversationId = userMsg.conversationId
        lastFailedPrompt = userMsg.content
        lastFailedAttachments = userMsg.allAttachments

        if (ttsActiveMessageId.value == assistantMessageId) {
            textToSpeechManager?.stop()
        }

        val messagesToDelete = currentMessages.subList(assistantIndex, currentMessages.size)

        val priorHistory = currentMessages.subList(0, userMsgIndex)
        val newAssistantMessageId = UUID.randomUUID().toString()
        val newAssistantMessage = Message(
            id = newAssistantMessageId,
            conversationId = conversationId,
            sender = MessageSender.ASSISTANT,
            content = "",
            timestamp = DateTimeFormatterHelper.formatTime(),
            status = MessageStatus.STREAMING
        )

        _activeMessages.value = currentMessages.subList(0, assistantIndex) + newAssistantMessage

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            for (msg in messagesToDelete) {
                conversationRepository.deleteMessage(msg.id)
            }

            _chatUiState.value = ChatUiState.Loading

            // Attachment analysis if user message had attachments
            if (userMsg.allAttachments.isNotEmpty()) {
                val attachments = userMsg.allAttachments
                val context = appContext
                val analysisOutputs = mutableListOf<String>()
                var failureMessage: String? = null

                for ((index, att) in attachments.withIndex()) {
                    val stepPrefix = if (attachments.size > 1) "file ${index + 1} of ${attachments.size}: ${att.fileName}" else "attachment: ${att.fileName}"
                    _chatUiState.value = ChatUiState.Analyzing("Analyzing $stepPrefix...")

                    val filePart: MultipartBody.Part = if (context != null) {
                        val uri = Uri.parse(att.uri)
                        val uriRequestBody = ContentUriRequestBody(
                            contentResolver = context.contentResolver,
                            uri = uri,
                            contentType = att.mimeType.toMediaTypeOrNull(),
                            contentLength = att.sizeBytes
                        )
                        MultipartBody.Part.createFormData("file", att.fileName, uriRequestBody)
                    } else {
                        val emptyBody = ByteArray(0).toRequestBody(att.mimeType.toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("file", att.fileName, emptyBody)
                    }

                    val specificPrompt = if (attachments.size > 1) {
                        "${userMsg.content}\n[File ${index + 1}/${attachments.size}: ${att.fileName}]"
                    } else {
                        userMsg.content
                    }

                    val analysisResult = chatRepository.analyzeFileMessage(
                        filePart = filePart,
                        message = specificPrompt,
                        conversationHistory = priorHistory,
                        responseStyle = _activeResponseStyle.value,
                        responseLanguage = _activeResponseLanguage.value,
                        responseFormat = _activeResponseFormat.value
                    )

                    when (analysisResult) {
                        is NetworkResult.Success -> analysisOutputs.add(analysisResult.data)
                        is NetworkResult.Error -> {
                            failureMessage = analysisResult.userMessage
                            break
                        }
                    }
                }

                if (failureMessage != null) {
                    val errorMsg = newAssistantMessage.copy(
                        status = MessageStatus.ERROR,
                        errorMessage = failureMessage,
                        content = failureMessage
                    )
                    _activeMessages.value = _activeMessages.value.map { if (it.id == newAssistantMessageId) errorMsg else it }
                    _chatUiState.value = ChatUiState.Error(failureMessage)
                    return@launch
                }

                val finalOutput = if (analysisOutputs.size == 1) {
                    analysisOutputs.first()
                } else {
                    buildString {
                        attachments.zip(analysisOutputs).forEachIndexed { idx, (att, out) ->
                            append("### Analysis for ${att.fileName}:\n")
                            append(out)
                            if (idx < attachments.size - 1) append("\n\n---\n\n")
                        }
                    }
                }

                val completedMessage = newAssistantMessage.copy(
                    content = finalOutput,
                    status = MessageStatus.SENT
                )
                conversationRepository.saveMessage(completedMessage)
                _activeMessages.value = _activeMessages.value.map { if (it.id == newAssistantMessageId) completedMessage else it }
                _chatUiState.value = ChatUiState.Idle
                return@launch
            }

            // Normal text streaming
            val prompt = userMsg.content
            var accumulatedText = ""

            chatRepository.sendChatMessageStream(
                message = prompt,
                conversationHistory = priorHistory,
                responseStyle = _activeResponseStyle.value,
                responseLanguage = _activeResponseLanguage.value,
                responseFormat = _activeResponseFormat.value,
                webSearch = _isWebSearchActive.value
            ).collect { result ->
                when (result) {
                    is StreamResult.Chunk -> {
                        accumulatedText += result.text
                        val currentList = _activeMessages.value
                        val hasTarget = currentList.any { it.id == newAssistantMessageId }
                        val withChunk = if (hasTarget) {
                            currentList.map { msg ->
                                if (msg.id == newAssistantMessageId) {
                                    msg.copy(
                                        content = accumulatedText,
                                        status = MessageStatus.STREAMING
                                    )
                                } else {
                                    msg
                                }
                            }
                        } else {
                            currentList + newAssistantMessage.copy(
                                content = accumulatedText,
                                status = MessageStatus.STREAMING
                            )
                        }
                        _activeMessages.value = withChunk
                        _chatUiState.value = ChatUiState.Streaming(accumulatedText)
                    }
                    is StreamResult.Complete -> {
                        val finalOutput = result.fullText.ifEmpty { accumulatedText }

                        val completedAssistantMsg = Message(
                            id = newAssistantMessageId,
                            conversationId = conversationId,
                            sender = MessageSender.ASSISTANT,
                            content = finalOutput,
                            timestamp = DateTimeFormatterHelper.formatTime(),
                            status = MessageStatus.SENT
                        )
                        conversationRepository.saveMessage(completedAssistantMsg)
                        conversationRepository.updateConversationLastMessage(conversationId, finalOutput)

                        val completedList = _activeMessages.value.map { msg ->
                            if (msg.id == newAssistantMessageId) completedAssistantMsg else msg
                        }
                        _activeMessages.value = completedList
                        _chatUiState.value = ChatUiState.Success(finalOutput)
                    }
                    is StreamResult.Error -> {
                        val isWebSearchUnavailable = result.errorCode == "WEB_SEARCH_UNAVAILABLE"
                        val errorMetadata = if (isWebSearchUnavailable) "WEB_SEARCH_UNAVAILABLE: ${result.userMessage}" else result.userMessage

                        if (accumulatedText.isNotBlank()) {
                            val interruptedText = accumulatedText + "\n\n⚠️ Streaming interrupted: ${result.userMessage}"
                            val interruptedMsg = Message(
                                id = newAssistantMessageId,
                                conversationId = conversationId,
                                sender = MessageSender.ASSISTANT,
                                content = interruptedText,
                                timestamp = DateTimeFormatterHelper.formatTime(),
                                status = MessageStatus.SENT,
                                errorMessage = errorMetadata
                            )
                            conversationRepository.saveMessage(interruptedMsg)
                            _activeMessages.value = _activeMessages.value.map { msg ->
                                if (msg.id == newAssistantMessageId) interruptedMsg else msg
                            }
                        } else {
                            val errorMsg = Message(
                                id = newAssistantMessageId,
                                conversationId = conversationId,
                                sender = MessageSender.ASSISTANT,
                                content = result.userMessage,
                                timestamp = DateTimeFormatterHelper.formatTime(),
                                status = MessageStatus.ERROR,
                                errorMessage = errorMetadata
                            )
                            conversationRepository.saveMessage(errorMsg)
                            _activeMessages.value = _activeMessages.value.map { msg ->
                                if (msg.id == newAssistantMessageId) errorMsg else msg
                            }
                        }
                        _chatUiState.value = ChatUiState.Error(result.userMessage)
                    }
                }
            }
        }
    }

    // --- Prompt Library State & Actions ---

    private val _selectedPromptCategory = MutableStateFlow<PromptCategory?>(null)
    val selectedPromptCategory: StateFlow<PromptCategory?> = _selectedPromptCategory.asStateFlow()

    private val _promptSearchQuery = MutableStateFlow("")
    val promptSearchQuery: StateFlow<String> = _promptSearchQuery.asStateFlow()

    private val _promptLibraryError = MutableStateFlow<String?>(null)
    val promptLibraryError: StateFlow<String?> = _promptLibraryError.asStateFlow()

    private val _promptLibraryLoading = MutableStateFlow(false)
    val promptLibraryLoading: StateFlow<Boolean> = _promptLibraryLoading.asStateFlow()

    val customPrompts: StateFlow<List<PromptItem>> = promptLibraryRepository.observeCustomPrompts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _allPrompts = MutableStateFlow<List<PromptItem>>(BuiltInPrompts.all)
    private val _filteredPrompts = MutableStateFlow<List<PromptItem>>(BuiltInPrompts.all)
    val filteredPrompts: StateFlow<List<PromptItem>> = _filteredPrompts.asStateFlow()

    init {
        viewModelScope.launch {
            promptLibraryRepository.observeAllPrompts().collect { prompts ->
                _allPrompts.value = prompts
                recomputeFilteredPrompts()
            }
        }
    }

    private fun recomputeFilteredPrompts() {
        val category = _selectedPromptCategory.value
        val trimmedQuery = _promptSearchQuery.value.trim().lowercase()
        _filteredPrompts.value = _allPrompts.value.filter { prompt ->
            val matchesCategory = category == null || prompt.category == category
            val matchesSearch = trimmedQuery.isEmpty() ||
                prompt.title.lowercase().contains(trimmedQuery) ||
                prompt.description.lowercase().contains(trimmedQuery) ||
                prompt.promptText.lowercase().contains(trimmedQuery) ||
                prompt.category.displayName.lowercase().contains(trimmedQuery) ||
                prompt.tags.any { it.lowercase().contains(trimmedQuery) }
            matchesCategory && matchesSearch
        }
    }

    fun selectPromptCategory(category: PromptCategory?) {
        _selectedPromptCategory.value = category
        recomputeFilteredPrompts()
    }

    fun updatePromptSearchQuery(query: String) {
        _promptSearchQuery.value = query
        recomputeFilteredPrompts()
    }

    fun clearPromptLibraryError() {
        _promptLibraryError.value = null
    }

    fun selectPromptFromLibrary(prompt: PromptItem) {
        _composerText.value = prompt.promptText
        _currentScreen.value = MinovaScreen.Chat(_activeConversationId.value)
    }

    fun saveCustomPrompt(
        title: String,
        description: String,
        promptText: String,
        category: PromptCategory,
        tags: List<String> = emptyList(),
        existingId: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val cleanTitle = title.trim()
        val cleanPrompt = promptText.trim()
        if (cleanTitle.isBlank()) {
            _promptLibraryError.value = "Prompt title is required."
            return
        }
        if (cleanPrompt.isBlank()) {
            _promptLibraryError.value = "Prompt template text is required."
            return
        }
        _promptLibraryError.value = null
        viewModelScope.launch {
            _promptLibraryLoading.value = true
            try {
                promptLibraryRepository.saveCustomPrompt(
                    title = cleanTitle,
                    description = description.trim().ifBlank { "Custom ${category.displayName} prompt" },
                    promptText = cleanPrompt,
                    category = category,
                    tags = tags,
                    existingId = existingId
                )
                onSuccess()
            } catch (e: Exception) {
                _promptLibraryError.value = e.message ?: "Failed to save custom prompt."
            } finally {
                _promptLibraryLoading.value = false
            }
        }
    }

    fun deleteCustomPrompt(promptId: String) {
        viewModelScope.launch {
            try {
                promptLibraryRepository.deleteCustomPrompt(promptId)
            } catch (e: Exception) {
                _promptLibraryError.value = e.message ?: "Failed to delete custom prompt."
            }
        }
    }

    // --- Account & Cloud Sync Actions (Step 19) ---

    fun signUpWithEmail(email: String, password: String, displayName: String) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signUpWithEmailPassword(email, password, displayName)
            result.onSuccess { user ->
                _accountFeedbackMessage.value = "Account created for ${user.email}."
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithEmailPassword(email, password)
            result.onSuccess { user ->
                _accountFeedbackMessage.value = "Signed in as ${user.email}."
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activityContext)
            result.onSuccess { user ->
                _accountFeedbackMessage.value = "Signed in with Google (${user.email})."
            }
        }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String = "",
        fallbackDisplayName: String = ""
    ) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithGoogleIdToken(idToken, fallbackEmail, fallbackDisplayName)
            result.onSuccess { user ->
                _accountFeedbackMessage.value = "Signed in with Google (${user.email})."
            }
        }
    }

    fun sendPasswordReset(email: String) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _accountFeedbackMessage.value = "Password reset instructions sent to ${email.trim()}."
                },
                onFailure = { err ->
                    _accountFeedbackMessage.value = err.message ?: "Unable to send password reset email."
                }
            )
        }
    }

    fun signOut(context: Context? = null) {
        _accountFeedbackMessage.value = null
        viewModelScope.launch {
            authRepository.signOut(context ?: appContext)
            cloudSyncManager.onUserSignedOut()
            _accountFeedbackMessage.value = "Signed out. Local conversations remain safely stored on this device."
        }
    }

    fun approveLocalChatsSyncConsent() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            cloudSyncManager.setSyncConsent(uid, SyncConsentDecision.APPROVED)
            _accountFeedbackMessage.value = "Consent approved. Local conversations synced to your cloud account."
        }
    }

    fun declineLocalChatsSyncConsent() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            cloudSyncManager.setSyncConsent(uid, SyncConsentDecision.DECLINED_KEEP_LOCAL_ONLY)
            _accountFeedbackMessage.value = "Existing local conversations will remain local-only on this device."
        }
    }

    fun syncNow() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = cloudSyncManager.syncWithCloud(uid)
            result.fold(
                onSuccess = {
                    _accountFeedbackMessage.value = "Cloud synchronization complete."
                },
                onFailure = { err ->
                    _accountFeedbackMessage.value = err.message ?: "Sync paused. Will retry when online."
                }
            )
        }
    }

    fun resolveSyncConflict(conversationId: String, strategy: ConflictResolutionStrategy) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = cloudSyncManager.resolveConflict(uid, conversationId, strategy)
            result.onSuccess {
                _accountFeedbackMessage.value = "Conflict resolved."
            }
        }
    }

    fun deleteAllCloudData(alsoDeleteLocalSynced: Boolean = false) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = cloudSyncManager.deleteAllCloudData(uid, alsoDeleteLocalSynced)
            result.fold(
                onSuccess = {
                    if (alsoDeleteLocalSynced && _activeConversationId.value != null) {
                        val exists = conversationRepository.getConversationById(_activeConversationId.value!!) != null
                        if (!exists) startNewChat()
                    }
                    _accountFeedbackMessage.value = "All cloud conversations have been permanently deleted."
                },
                onFailure = { err ->
                    _accountFeedbackMessage.value = err.message ?: "Failed to delete cloud conversations."
                }
            )
        }
    }

    fun deleteAccountAndCloudData(context: Context? = null) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            cloudSyncManager.deleteAllCloudData(uid, alsoDeleteLocalSynced = false)
            authRepository.deleteCurrentUserAccount(context ?: appContext)
            cloudSyncManager.onUserSignedOut()
            _accountFeedbackMessage.value = "Account and cloud data deleted. Local conversations remain on device."
        }
    }

    fun clearAccountFeedback() {
        _accountFeedbackMessage.value = null
        authRepository.clearAuthError()
    }

    // --- Learn Mode Action Handlers ---

    fun startLearningPlan(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ) {
        viewModelScope.launch {
            _isGeneratingPlan.value = true
            _planGenerationError.value = null
            val result = learningRepository.generateAndSaveLearningPlan(
                topic = topic,
                category = category,
                level = level,
                goal = goal,
                language = language,
                dailyDurationMinutes = dailyDurationMinutes
            )
            _isGeneratingPlan.value = false
            result.fold(
                onSuccess = { sessionId ->
                    _activeLearningSessionId.value = sessionId
                    _currentLessonIndex.value = 0
                    navigateTo(MinovaScreen.LearnSession(sessionId, 0))
                },
                onFailure = { error ->
                    _planGenerationError.value = error.message ?: "Failed to generate learning plan. Please try again."
                }
            )
        }
    }

    fun openLearningSession(sessionId: String, lessonIndex: Int = 0) {
        _activeLearningSessionId.value = sessionId
        _currentLessonIndex.value = lessonIndex
        viewModelScope.launch {
            learningRepository.updateLastOpenedLesson(sessionId, lessonIndex)
        }
        navigateTo(MinovaScreen.LearnSession(sessionId, lessonIndex))
    }

    fun selectLesson(lessonIndex: Int) {
        _currentLessonIndex.value = lessonIndex
        val sid = _activeLearningSessionId.value ?: return
        viewModelScope.launch {
            learningRepository.updateLastOpenedLesson(sid, lessonIndex)
        }
    }

    fun markLessonCompleted(sessionId: String, lessonId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            learningRepository.markLessonCompleted(sessionId, lessonId, isCompleted)
        }
    }

    fun toggleLessonBookmark(lessonId: String, currentBookmarked: Boolean) {
        viewModelScope.launch {
            learningRepository.toggleLessonBookmark(lessonId, currentBookmarked)
        }
    }

    fun openQuiz(sessionId: String) {
        _activeLearningSessionId.value = sessionId
        navigateTo(MinovaScreen.LearnQuiz(sessionId))
    }

    fun submitQuizAnswer(questionId: String, answer: String) {
        viewModelScope.launch {
            val isCorrect = learningRepository.submitQuizAnswer(questionId, answer)
            _quizLastAnswerResult.value = isCorrect
        }
    }

    fun resetQuiz(sessionId: String) {
        viewModelScope.launch {
            learningRepository.resetQuizAnswers(sessionId)
            _quizLastAnswerResult.value = null
        }
    }

    fun finishAndRecordQuiz(sessionId: String) {
        viewModelScope.launch {
            learningRepository.recordQuizAttempt(sessionId)
        }
    }

    fun askLessonTutor(lesson: LearningLesson, question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) return
        viewModelScope.launch {
            _isLessonTutorLoading.value = true
            _lessonTutorError.value = null
            when (val res = learningRepository.askLessonTutor(lesson, cleanQuestion)) {
                is NetworkResult.Success -> {
                    _lessonTutorResponse.value = res.data
                }
                is NetworkResult.Error -> {
                    _lessonTutorError.value = res.userMessage
                }
            }
            _isLessonTutorLoading.value = false
        }
    }

    fun clearLessonTutor() {
        _lessonTutorResponse.value = null
        _lessonTutorError.value = null
        _isLessonTutorLoading.value = false
    }

    fun clearPlanGenerationError() {
        _planGenerationError.value = null
    }

    fun deleteLearningSession(sessionId: String) {
        viewModelScope.launch {
            learningRepository.deleteSession(sessionId)
            if (_activeLearningSessionId.value == sessionId) {
                _activeLearningSessionId.value = null
                _currentLessonIndex.value = 0
                navigateTo(MinovaScreen.LearnHome)
            }
        }
    }

    // --- Code Workspace Action Handlers ---

    private fun currentSnippetOwnerUid(): String {
        return authRepository.currentUser?.uid ?: CodeSnippet.GUEST_OWNER_UID
    }

    fun runCodeWorkspaceAction(
        action: CodeActionType,
        language: CodeLanguage,
        code: String,
        contextInput: String = "",
        responseLanguage: ResponseLanguage = ResponseLanguage.ENGLISH
    ) {
        viewModelScope.launch {
            _isCodeActionLoading.value = true
            _codeWorkspaceError.value = null
            when (
                val res = codeWorkspaceRepository.performCodeAction(
                    action = action,
                    language = language,
                    code = code,
                    contextInput = contextInput,
                    responseLanguage = responseLanguage
                )
            ) {
                is NetworkResult.Success -> {
                    _codeAssistanceResult.value = res.data
                }
                is NetworkResult.Error -> {
                    _codeWorkspaceError.value = res.userMessage
                }
            }
            _isCodeActionLoading.value = false
        }
    }

    fun saveCodeSnippet(
        title: String,
        language: CodeLanguage,
        code: String,
        description: String = "",
        existingId: String? = null,
        onSaved: (CodeSnippet) -> Unit = {}
    ) {
        viewModelScope.launch {
            _codeWorkspaceError.value = null
            val ownerUid = currentSnippetOwnerUid()
            val result = codeWorkspaceRepository.saveSnippet(
                ownerUid = ownerUid,
                title = title,
                language = language,
                code = code,
                description = description,
                existingId = existingId
            )
            result.fold(
                onSuccess = { saved ->
                    _codeWorkspaceFeedback.value = "Snippet '${saved.title}' saved locally."
                    onSaved(saved)
                },
                onFailure = { error ->
                    _codeWorkspaceError.value = error.message ?: "Could not save snippet."
                }
            )
        }
    }

    fun renameCodeSnippet(snippetId: String, newTitle: String) {
        viewModelScope.launch {
            _codeWorkspaceError.value = null
            val result = codeWorkspaceRepository.renameSnippet(snippetId, newTitle)
            result.fold(
                onSuccess = {
                    _codeWorkspaceFeedback.value = "Snippet renamed to '${newTitle.trim()}'."
                },
                onFailure = { error ->
                    _codeWorkspaceError.value = error.message ?: "Could not rename snippet."
                }
            )
        }
    }

    fun deleteCodeSnippet(snippetId: String) {
        viewModelScope.launch {
            codeWorkspaceRepository.deleteSnippet(snippetId)
            _codeWorkspaceFeedback.value = "Snippet deleted."
        }
    }

    fun clearCodeWorkspaceError() {
        _codeWorkspaceError.value = null
    }

    fun clearCodeWorkspaceFeedback() {
        _codeWorkspaceFeedback.value = null
    }

    fun clearCodeAssistanceResult() {
        _codeAssistanceResult.value = null
    }

    // --- AI File Studio Action Handlers ---

    fun generateStudioDocument(
        mode: FileStudioMode,
        title: String,
        subtitle: String,
        author: String,
        blocks: List<DocumentContentBlock>
    ) {
        // Prevent duplicate submissions while a request is running
        if (_isFileStudioLoading.value) return
        viewModelScope.launch {
            _isFileStudioLoading.value = true
            _fileStudioError.value = null
            _fileStudioErrorCode.value = null
            _fileStudioSavedConfirmation.value = null

            when (
                val res = fileStudioRepository.generateDocument(
                    mode = mode,
                    title = title,
                    subtitle = subtitle,
                    author = author,
                    blocks = blocks
                )
            ) {
                is NetworkResult.Success -> {
                    _generatedStudioFile.value = res.data
                }
                is NetworkResult.Error -> {
                    _fileStudioError.value = res.userMessage
                    _fileStudioErrorCode.value = res.errorCode
                }
            }
            _isFileStudioLoading.value = false
        }
    }

    fun generateStudioSpreadsheet(
        workbookTitle: String,
        worksheets: List<SpreadsheetWorksheet>
    ) {
        if (_isFileStudioLoading.value) return
        viewModelScope.launch {
            _isFileStudioLoading.value = true
            _fileStudioError.value = null
            _fileStudioErrorCode.value = null
            _fileStudioSavedConfirmation.value = null

            when (
                val res = fileStudioRepository.generateSpreadsheet(
                    workbookTitle = workbookTitle,
                    worksheets = worksheets
                )
            ) {
                is NetworkResult.Success -> {
                    _generatedStudioFile.value = res.data
                }
                is NetworkResult.Error -> {
                    _fileStudioError.value = res.userMessage
                    _fileStudioErrorCode.value = res.errorCode
                }
            }
            _isFileStudioLoading.value = false
        }
    }

    fun generateStudioImage(
        prompt: String,
        aspectRatio: ImageAspectRatio
    ) {
        if (_isFileStudioLoading.value) return
        viewModelScope.launch {
            _isFileStudioLoading.value = true
            _fileStudioError.value = null
            _fileStudioErrorCode.value = null
            _fileStudioSavedConfirmation.value = null

            when (
                val res = fileStudioRepository.generateImage(
                    prompt = prompt,
                    aspectRatio = aspectRatio
                )
            ) {
                is NetworkResult.Success -> {
                    _generatedStudioFile.value = res.data
                }
                is NetworkResult.Error -> {
                    _fileStudioError.value = res.userMessage
                    _fileStudioErrorCode.value = res.errorCode
                }
            }
            _isFileStudioLoading.value = false
        }
    }

    fun onStudioFileSavedSuccess(confirmationMessage: String) {
        _fileStudioSavedConfirmation.value = confirmationMessage
        _fileStudioError.value = null
        _fileStudioErrorCode.value = null
    }

    fun onStudioFileSaveOrShareError(errorMessage: String) {
        _fileStudioError.value = errorMessage
        _fileStudioErrorCode.value = "FILE_EXPORT_ERROR"
    }

    fun clearFileStudioError() {
        _fileStudioError.value = null
        _fileStudioErrorCode.value = null
    }

    fun clearFileStudioSavedConfirmation() {
        _fileStudioSavedConfirmation.value = null
    }

    fun clearGeneratedStudioFile() {
        _generatedStudioFile.value = null
        _fileStudioSavedConfirmation.value = null
    }
}
