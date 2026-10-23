package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.model.AuthState
import com.example.navigation.MinovaScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeWorkspaceScreen
import com.example.ui.screens.FileStudioScreen
import com.example.ui.screens.LearnHomeScreen
import com.example.ui.screens.LearnLessonScreen
import com.example.ui.screens.LearnQuizScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.PromptLibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MinovaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MinovaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MinovaApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MinovaApp(viewModel: MinovaViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val pinnedConversations by viewModel.pinnedConversations.collectAsState()
    val archivedConversations by viewModel.archivedConversations.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeConversationId by viewModel.activeConversationId.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val composerText by viewModel.composerText.collectAsState()
    val isAttachmentSheetVisible by viewModel.isAttachmentSheetVisible.collectAsState()
    val selectedAttachment by viewModel.selectedAttachment.collectAsState()
    val selectedAttachments by viewModel.selectedAttachments.collectAsState()
    val isVoiceActive by viewModel.isVoiceActive.collectAsState()
    val voiceDurationSeconds by viewModel.voiceDurationSeconds.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val chatUiState by viewModel.chatUiState.collectAsState()
    val activeSpeakingMessageId by viewModel.ttsActiveMessageId.collectAsState()
    val editingUserMessageId by viewModel.editingUserMessageId.collectAsState()
    val activeResponseStyle by viewModel.activeResponseStyle.collectAsState()
    val isWebSearchActive by viewModel.isWebSearchActive.collectAsState()
    val filteredPrompts by viewModel.filteredPrompts.collectAsState()
    val selectedPromptCategory by viewModel.selectedPromptCategory.collectAsState()
    val promptSearchQuery by viewModel.promptSearchQuery.collectAsState()
    val promptLibraryLoading by viewModel.promptLibraryLoading.collectAsState()
    val promptLibraryError by viewModel.promptLibraryError.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val accountFeedbackMessage by viewModel.accountFeedbackMessage.collectAsState()

    // Learn Mode States
    val learningSessions by viewModel.learningSessions.collectAsState()
    val activeLearningSession by viewModel.activeLearningSession.collectAsState()
    val activeLessons by viewModel.activeLessons.collectAsState()
    val activeQuizQuestions by viewModel.activeQuizQuestions.collectAsState()
    val activeQuizAttempts by viewModel.activeQuizAttempts.collectAsState()
    val currentLessonIndex by viewModel.currentLessonIndex.collectAsState()
    val isGeneratingPlan by viewModel.isGeneratingPlan.collectAsState()
    val planGenerationError by viewModel.planGenerationError.collectAsState()
    val isLessonTutorLoading by viewModel.isLessonTutorLoading.collectAsState()
    val lessonTutorResponse by viewModel.lessonTutorResponse.collectAsState()
    val lessonTutorError by viewModel.lessonTutorError.collectAsState()

    // Code Workspace States
    val codeSnippets by viewModel.codeSnippets.collectAsState()
    val isCodeActionLoading by viewModel.isCodeActionLoading.collectAsState()
    val codeAssistanceResult by viewModel.codeAssistanceResult.collectAsState()
    val codeWorkspaceError by viewModel.codeWorkspaceError.collectAsState()
    val codeWorkspaceFeedback by viewModel.codeWorkspaceFeedback.collectAsState()

    // AI File Studio States
    val isFileStudioLoading by viewModel.isFileStudioLoading.collectAsState()
    val generatedStudioFile by viewModel.generatedStudioFile.collectAsState()
    val fileStudioError by viewModel.fileStudioError.collectAsState()
    val fileStudioErrorCode by viewModel.fileStudioErrorCode.collectAsState()
    val fileStudioSavedConfirmation by viewModel.fileStudioSavedConfirmation.collectAsState()

    // Handle Android system back gesture
    if (currentScreen != MinovaScreen.Splash && currentScreen !is MinovaScreen.Chat) {
        BackHandler {
            when (val s = currentScreen) {
                is MinovaScreen.LearnQuiz -> {
                    viewModel.openLearningSession(s.sessionId, currentLessonIndex)
                }
                is MinovaScreen.LearnSession -> {
                    viewModel.navigateTo(MinovaScreen.LearnHome)
                }
                else -> {
                    viewModel.navigateTo(MinovaScreen.Chat(activeConversationId))
                }
            }
        }
    }

    when (val screen = currentScreen) {
        is MinovaScreen.Splash -> {
            SplashScreen(
                onContinue = { viewModel.dismissSplash() }
            )
        }

        is MinovaScreen.Chat -> {
            ChatScreen(
                conversations = conversations,
                activeConversationId = activeConversationId,
                activeMessages = activeMessages,
                composerText = composerText,
                onComposerTextChange = { viewModel.onComposerTextChange(it) },
                onSendMessage = { viewModel.sendMessage() },
                onSelectSuggestion = { viewModel.selectSuggestion(it) },
                suggestions = viewModel.suggestionCards,
                userProfile = userProfile,
                activeModelName = settings.aiModel.substringBefore(" (").let { label ->
                    if (label.isBlank() || label.contains("Gemini", ignoreCase = true) || label.contains("Google", ignoreCase = true)) {
                        "MINOVA AI"
                    } else {
                        label
                    }
                },
                isAttachmentSheetVisible = isAttachmentSheetVisible,
                onOpenAttachmentSheet = { viewModel.openAttachmentSheet(it) },
                selectedAttachment = selectedAttachment,
                selectedAttachments = selectedAttachments,
                onSelectAttachment = { viewModel.selectAttachment(it) },
                onSelectAttachments = { viewModel.addAttachments(it) },
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                onAttachmentError = { viewModel.onAttachmentError(it) },
                onClearAttachment = { viewModel.clearSelectedAttachments() },
                isVoiceActive = isVoiceActive,
                voiceDurationSeconds = voiceDurationSeconds,
                voiceState = voiceState,
                onStartVoice = { viewModel.startVoiceRecording() },
                onCancelVoice = { viewModel.cancelVoiceRecording() },
                onStopVoice = { viewModel.stopVoiceRecording() },
                onNavigate = { viewModel.navigateTo(it) },
                onSelectConversation = { viewModel.selectConversation(it) },
                onNewChatClick = { viewModel.startNewChat() },
                onTogglePin = { viewModel.togglePin(it) },
                onToggleArchive = { viewModel.toggleArchive(it) },
                onDeleteConversation = { viewModel.deleteConversation(it) },
                chatUiState = chatUiState,
                activeSpeakingMessageId = activeSpeakingMessageId,
                onToggleSpeech = { messageId, content -> viewModel.toggleSpeech(messageId, content) },
                onRetry = { viewModel.retryLastMessage() },
                onStopStreaming = { viewModel.stopStreaming() },
                editingUserMessageId = editingUserMessageId,
                onStartEditMessage = { viewModel.startEditMessage(it) },
                onCancelEdit = { viewModel.cancelEditMessage() },
                onDeleteMessage = { viewModel.deleteMessage(it) },
                onPinMessage = { viewModel.togglePinMessage(it) },
                onRegenerateMessage = { viewModel.regenerateMessage(it) },
                settings = settings,
                activeResponseStyle = activeResponseStyle,
                onSelectResponseStyle = { viewModel.setActiveResponseStyle(it) },
                isWebSearchActive = isWebSearchActive,
                onToggleWebSearch = { viewModel.toggleWebSearch() },
                onContinueWithoutWebSearch = { viewModel.continueWithoutWebSearch() }
            )
        }

        is MinovaScreen.Settings -> {
            SettingsScreen(
                settings = settings,
                onSettingsChange = { viewModel.updateSettings(it) },
                onResetSettings = { viewModel.resetSettings() },
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) },
                onNavigate = { viewModel.navigateTo(it) }
            )
        }

        is MinovaScreen.Profile -> {
            ProfileScreen(
                profile = userProfile,
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) },
                authState = authState,
                syncState = syncState,
                feedbackMessage = accountFeedbackMessage,
                onSignInWithEmail = { email, password ->
                    viewModel.signInWithEmail(email, password)
                },
                onSignUpWithEmail = { email, password, name ->
                    viewModel.signUpWithEmail(email, password, name)
                },
                onGoogleSignIn = { activityContext ->
                    viewModel.signInWithGoogle(activityContext)
                },
                onSendPasswordReset = { email ->
                    viewModel.sendPasswordReset(email)
                },
                onSignOut = { activityContext ->
                    viewModel.signOut(activityContext)
                },
                onApproveSyncConsent = {
                    viewModel.approveLocalChatsSyncConsent()
                },
                onDeclineSyncConsent = {
                    viewModel.declineLocalChatsSyncConsent()
                },
                onSyncNow = {
                    viewModel.syncNow()
                },
                onResolveConflict = { conversationId, strategy ->
                    viewModel.resolveSyncConflict(conversationId, strategy)
                },
                onDeleteCloudData = { alsoDeleteLocalSynced ->
                    viewModel.deleteAllCloudData(alsoDeleteLocalSynced)
                },
                onDeleteAccount = { activityContext ->
                    viewModel.deleteAccountAndCloudData(activityContext)
                },
                onClearFeedback = {
                    viewModel.clearAccountFeedback()
                }
            )
        }

        is MinovaScreen.Search -> {
            SearchScreen(
                title = "Search Conversations",
                conversations = searchResults,
                searchQuery = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                },
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) }
            )
        }

        is MinovaScreen.Pinned -> {
            SearchScreen(
                title = "Pinned Conversations",
                conversations = pinnedConversations,
                searchQuery = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                },
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) }
            )
        }

        is MinovaScreen.Archived -> {
            SearchScreen(
                title = "Archived Conversations",
                conversations = archivedConversations,
                searchQuery = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                },
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) }
            )
        }

        is MinovaScreen.PromptLibrary -> {
            PromptLibraryScreen(
                prompts = filteredPrompts,
                selectedCategory = selectedPromptCategory,
                searchQuery = promptSearchQuery,
                isLoading = promptLibraryLoading,
                errorMessage = promptLibraryError,
                onSelectCategory = { viewModel.selectPromptCategory(it) },
                onSearchQueryChange = { viewModel.updatePromptSearchQuery(it) },
                onUsePrompt = { viewModel.selectPromptFromLibrary(it) },
                onSaveCustomPrompt = { title, description, promptText, category, existingId, onDone ->
                    viewModel.saveCustomPrompt(
                        title = title,
                        description = description,
                        promptText = promptText,
                        category = category,
                        existingId = existingId,
                        onSuccess = onDone
                    )
                },
                onDeleteCustomPrompt = { viewModel.deleteCustomPrompt(it) },
                onDismissError = { viewModel.clearPromptLibraryError() },
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) }
            )
        }

        is MinovaScreen.LearnHome -> {
            LearnHomeScreen(
                sessions = learningSessions,
                isGenerating = isGeneratingPlan,
                generationError = planGenerationError,
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) },
                onStartLearningPlan = { topic, category, level, goal, language, duration ->
                    viewModel.startLearningPlan(
                        topic = topic,
                        category = category,
                        level = level,
                        goal = goal,
                        language = language,
                        dailyDurationMinutes = duration
                    )
                },
                onOpenSession = { sessionId, lessonIndex ->
                    viewModel.openLearningSession(sessionId, lessonIndex)
                },
                onOpenQuiz = { sessionId ->
                    viewModel.openQuiz(sessionId)
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteLearningSession(sessionId)
                },
                onClearError = {
                    viewModel.clearPlanGenerationError()
                }
            )
        }

        is MinovaScreen.LearnSession -> {
            LearnLessonScreen(
                session = activeLearningSession,
                lessons = activeLessons,
                currentLessonIndex = currentLessonIndex,
                isTutorLoading = isLessonTutorLoading,
                tutorResponse = lessonTutorResponse,
                tutorError = lessonTutorError,
                onBackToHome = { viewModel.navigateTo(MinovaScreen.LearnHome) },
                onSelectLesson = { idx -> viewModel.selectLesson(idx) },
                onMarkCompleted = { lessonId, completed ->
                    viewModel.markLessonCompleted(screen.sessionId, lessonId, completed)
                },
                onToggleBookmark = { lessonId, currentBookmarked ->
                    viewModel.toggleLessonBookmark(lessonId, currentBookmarked)
                },
                onOpenQuiz = { sessionId ->
                    viewModel.openQuiz(sessionId)
                },
                onAskTutor = { lesson, question ->
                    viewModel.askLessonTutor(lesson, question)
                },
                onClearTutor = {
                    viewModel.clearLessonTutor()
                }
            )
        }

        is MinovaScreen.LearnQuiz -> {
            LearnQuizScreen(
                session = activeLearningSession,
                questions = activeQuizQuestions,
                attempts = activeQuizAttempts,
                onBackToSession = {
                    viewModel.openLearningSession(screen.sessionId, currentLessonIndex)
                },
                onBackToHome = {
                    viewModel.navigateTo(MinovaScreen.LearnHome)
                },
                onSubmitAnswer = { questionId, answer ->
                    viewModel.submitQuizAnswer(questionId, answer)
                },
                onResetQuiz = { sessionId ->
                    viewModel.resetQuiz(sessionId)
                },
                onFinishQuiz = { sessionId ->
                    viewModel.finishAndRecordQuiz(sessionId)
                }
            )
        }

        is MinovaScreen.CodeWorkspace -> {
            CodeWorkspaceScreen(
                snippets = codeSnippets,
                isLoading = isCodeActionLoading,
                assistanceResult = codeAssistanceResult,
                errorMessage = codeWorkspaceError,
                feedbackMessage = codeWorkspaceFeedback,
                isSignedInUser = authState is AuthState.Authenticated,
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) },
                onRunCodeAction = { action, language, code, contextInput, responseLanguage ->
                    viewModel.runCodeWorkspaceAction(
                        action = action,
                        language = language,
                        code = code,
                        contextInput = contextInput,
                        responseLanguage = responseLanguage
                    )
                },
                onSaveSnippet = { title, language, code, description, existingId, onSaved ->
                    viewModel.saveCodeSnippet(
                        title = title,
                        language = language,
                        code = code,
                        description = description,
                        existingId = existingId,
                        onSaved = onSaved
                    )
                },
                onRenameSnippet = { snippetId, newTitle ->
                    viewModel.renameCodeSnippet(snippetId, newTitle)
                },
                onDeleteSnippet = { snippetId ->
                    viewModel.deleteCodeSnippet(snippetId)
                },
                onClearError = { viewModel.clearCodeWorkspaceError() },
                onClearFeedback = { viewModel.clearCodeWorkspaceFeedback() },
                onClearResult = { viewModel.clearCodeAssistanceResult() }
            )
        }

        is MinovaScreen.FileStudio -> {
            FileStudioScreen(
                isLoading = isFileStudioLoading,
                generatedFile = generatedStudioFile,
                errorMessage = fileStudioError,
                errorCode = fileStudioErrorCode,
                savedConfirmationMessage = fileStudioSavedConfirmation,
                onBack = { viewModel.navigateTo(MinovaScreen.Chat(activeConversationId)) },
                onGenerateDocument = { mode, title, subtitle, author, blocks ->
                    viewModel.generateStudioDocument(
                        mode = mode,
                        title = title,
                        subtitle = subtitle,
                        author = author,
                        blocks = blocks
                    )
                },
                onGenerateSpreadsheet = { workbookTitle, worksheets ->
                    viewModel.generateStudioSpreadsheet(
                        workbookTitle = workbookTitle,
                        worksheets = worksheets
                    )
                },
                onGenerateImage = { prompt, aspectRatio ->
                    viewModel.generateStudioImage(
                        prompt = prompt,
                        aspectRatio = aspectRatio
                    )
                },
                onFileSavedSuccess = { confirmation ->
                    viewModel.onStudioFileSavedSuccess(confirmation)
                },
                onFileSaveOrShareError = { errorMsg ->
                    viewModel.onStudioFileSaveOrShareError(errorMsg)
                },
                onClearError = { viewModel.clearFileStudioError() },
                onClearSavedConfirmation = { viewModel.clearFileStudioSavedConfirmation() },
                onClearGeneratedFile = { viewModel.clearGeneratedStudioFile() }
            )
        }
    }
}
