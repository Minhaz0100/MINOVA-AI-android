package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.model.ResponseStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.SettingsState
import com.example.data.model.SuggestionCard
import com.example.data.model.UserProfile
import com.example.data.util.FileValidationResult
import com.example.data.util.FileValidator
import com.example.data.util.MultiFileValidationResult
import com.example.data.voice.VoiceState
import com.example.navigation.MinovaScreen
import com.example.ui.components.AttachmentBottomSheet
import com.example.ui.components.ConversationMessageList
import com.example.ui.components.EmptyChatState
import com.example.ui.components.MinovaComposer
import com.example.ui.components.MinovaDrawerContent
import com.example.ui.components.MinovaTopBar
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary
import com.example.viewmodel.ChatUiState
import com.example.viewmodel.isStreaming
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversations: List<Conversation>,
    activeConversationId: String?,
    activeMessages: List<Message>,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    suggestions: List<SuggestionCard>,
    userProfile: UserProfile,
    activeModelName: String,
    isAttachmentSheetVisible: Boolean,
    onOpenAttachmentSheet: (Boolean) -> Unit,
    selectedAttachment: Attachment?,
    selectedAttachments: List<Attachment> = emptyList(),
    onSelectAttachment: (Attachment?) -> Unit,
    onSelectAttachments: (List<Attachment>) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onAttachmentError: (String) -> Unit = {},
    onClearAttachment: () -> Unit,
    isVoiceActive: Boolean,
    voiceDurationSeconds: Int,
    voiceState: VoiceState = VoiceState.IDLE,
    onStartVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onNavigate: (MinovaScreen) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleArchive: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    chatUiState: ChatUiState = ChatUiState.Idle,
    activeSpeakingMessageId: String? = null,
    onToggleSpeech: (messageId: String, content: String) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onStopStreaming: () -> Unit = {},
    editingUserMessageId: String? = null,
    onStartEditMessage: (Message) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDeleteMessage: (Message) -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onRegenerateMessage: (Message) -> Unit = {},
    settings: SettingsState = SettingsState(),
    activeResponseStyle: ResponseStyle = ResponseStyle.BALANCED,
    onSelectResponseStyle: (ResponseStyle) -> Unit = {},
    isWebSearchActive: Boolean = false,
    onToggleWebSearch: () -> Unit = {},
    onContinueWithoutWebSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResponseStyleSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (voiceState == VoiceState.LISTENING) {
                    onCancelVoice()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (voiceState == VoiceState.LISTENING) {
                onCancelVoice()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartVoice()
        } else {
            scope.launch {
                val snackbarResult = snackbarHostState.showSnackbar(
                    message = "Microphone permission is required for voice input.",
                    actionLabel = "Settings",
                    duration = SnackbarDuration.Short
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    LaunchedEffect(chatUiState) {
        if (chatUiState is ChatUiState.Error) {
            snackbarHostState.showSnackbar(message = chatUiState.message)
        }
    }

    // Modern Photo Picker (Zero permission, supports image assets)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = FileValidator.MAX_ATTACHMENTS_PER_MESSAGE)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            when (val result = FileValidator.validateMultiple(context, uris)) {
                is MultiFileValidationResult.Success -> {
                    if (result.validAttachments.isNotEmpty()) {
                        onSelectAttachments(result.validAttachments)
                    }
                    if (result.errorMessages.isNotEmpty()) {
                        onAttachmentError(result.errorMessages.first())
                    }
                }
            }
        }
    }

    // Modern Document Picker (SAF / OpenDocument for PDF, DOCX, TXT, etc.)
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
            }

            when (val result = FileValidator.validateMultiple(context, uris)) {
                is MultiFileValidationResult.Success -> {
                    if (result.validAttachments.isNotEmpty()) {
                        onSelectAttachments(result.validAttachments)
                    }
                    if (result.errorMessages.isNotEmpty()) {
                        onAttachmentError(result.errorMessages.first())
                    }
                }
            }
        }
    }

    val currentTitle = conversations.find { it.id == activeConversationId }?.title ?: "New Conversation"

    // Responsive design: Check available width to decide tablet vs phone layout
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLayout = maxWidth >= 720.dp
        val tabletDrawerWidth = (maxWidth * 0.30f).coerceIn(240.dp, 280.dp)
        val phoneDrawerWidth = (maxWidth * 0.70f).coerceIn(220.dp, 280.dp)

        if (isTabletLayout) {
            // Desktop-like Android tablet layout: Permanent sidebar on the left + conversation on right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("tablet_chat_layout")
            ) {
                // Permanent side navigation drawer / sidebar
                MinovaDrawerContent(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    userProfile = userProfile,
                    onNavigate = onNavigate,
                    onSelectConversation = onSelectConversation,
                    onNewChatClick = onNewChatClick,
                    onTogglePin = onTogglePin,
                    onToggleArchive = onToggleArchive,
                    onDeleteConversation = onDeleteConversation,
                    drawerWidth = tabletDrawerWidth,
                    modifier = Modifier.fillMaxHeight()
                )

                // Main conversation area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                ) {
                    MinovaTopBar(
                        title = currentTitle,
                        subtitle = "MINOVA Personal Assistant",
                        showBackButton = false,
                        onMenuClick = {},
                        onNewChatClick = onNewChatClick,
                        onModelBadgeClick = { onNavigate(MinovaScreen.Settings) },
                        activeModelName = activeModelName,
                        activeResponseStyle = activeResponseStyle,
                        onResponseStyleClick = { showResponseStyleSheet = true }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (activeMessages.isEmpty()) {
                            EmptyChatState(
                                suggestions = suggestions,
                                onSuggestionClick = onSelectSuggestion
                            )
                        } else {
                            ConversationMessageList(
                                messages = activeMessages,
                                chatUiState = chatUiState,
                                activeSpeakingMessageId = activeSpeakingMessageId,
                                onToggleSpeech = onToggleSpeech,
                                onRetry = onRetry,
                                onEditMessage = onStartEditMessage,
                                onDeleteMessage = onDeleteMessage,
                                onPinMessage = onPinMessage,
                                onRegenerateMessage = onRegenerateMessage,
                                onContinueWithoutSearch = onContinueWithoutWebSearch,
                                showTimestamps = settings.showTimestamps,
                                showAttachmentPreview = settings.showAttachmentPreview,
                                isVoiceOutputEnabled = settings.voiceOutputEnabled
                            )
                        }
                    }

                    if (editingUserMessageId != null) {
                        Surface(
                            color = MinovaIndigoSecondary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                            border = BorderStroke(1.dp, MinovaCyanPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("editing_message_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = MinovaCyanPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Editing message",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MinovaCyanPrimary
                                    )
                                }
                                IconButton(
                                    onClick = onCancelEdit,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("cancel_edit_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    MinovaComposer(
                        text = composerText,
                        onTextChange = onComposerTextChange,
                        onSend = onSendMessage,
                        onAttachmentClick = { onOpenAttachmentSheet(true) },
                        onMicClick = onStartVoice,
                        selectedAttachment = selectedAttachment,
                        selectedAttachments = selectedAttachments,
                        onClearAttachment = onClearAttachment,
                        onRemoveAttachment = onRemoveAttachment,
                        isStreaming = chatUiState.isStreaming,
                        onStopStreaming = onStopStreaming,
                        enterToSend = settings.enterToSend,
                        autoFocusComposer = settings.autoFocusComposer,
                        showAttachmentPreview = settings.showAttachmentPreview,
                        isWebSearchActive = isWebSearchActive,
                        onToggleWebSearch = onToggleWebSearch
                    )
                }
            }
        } else {
            // Phone layout: Top app bar, conversation area, bottom message composer, Modal Navigation Drawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    MinovaDrawerContent(
                        conversations = conversations,
                        activeConversationId = activeConversationId,
                        userProfile = userProfile,
                        onNavigate = { screen ->
                            scope.launch { drawerState.close() }
                            onNavigate(screen)
                        },
                        onSelectConversation = { id ->
                            scope.launch { drawerState.close() }
                            onSelectConversation(id)
                        },
                        onNewChatClick = {
                            scope.launch { drawerState.close() }
                            onNewChatClick()
                        },
                        onTogglePin = onTogglePin,
                        onToggleArchive = onToggleArchive,
                        onDeleteConversation = onDeleteConversation,
                        drawerWidth = phoneDrawerWidth,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(phoneDrawerWidth)
                    )
                },
                scrimColor = DrawerDefaults.scrimColor,
                modifier = Modifier.testTag("phone_chat_layout")
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        MinovaTopBar(
                            title = currentTitle,
                            subtitle = if (activeConversationId == null) "One Intelligence. Infinite Possibilities." else null,
                            showBackButton = false,
                            onMenuClick = {
                                scope.launch { drawerState.open() }
                            },
                            onNewChatClick = onNewChatClick,
                            onModelBadgeClick = { onNavigate(MinovaScreen.Settings) },
                            activeModelName = activeModelName,
                            activeResponseStyle = activeResponseStyle,
                            onResponseStyleClick = { showResponseStyleSheet = true }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (activeMessages.isEmpty()) {
                                EmptyChatState(
                                    suggestions = suggestions,
                                    onSuggestionClick = onSelectSuggestion
                                )
                            } else {
                                ConversationMessageList(
                                    messages = activeMessages,
                                    chatUiState = chatUiState,
                                    activeSpeakingMessageId = activeSpeakingMessageId,
                                    onToggleSpeech = onToggleSpeech,
                                    onRetry = onRetry,
                                    onEditMessage = onStartEditMessage,
                                    onDeleteMessage = onDeleteMessage,
                                    onPinMessage = onPinMessage,
                                    onRegenerateMessage = onRegenerateMessage,
                                    onContinueWithoutSearch = onContinueWithoutWebSearch,
                                    showTimestamps = settings.showTimestamps,
                                    showAttachmentPreview = settings.showAttachmentPreview,
                                    isVoiceOutputEnabled = settings.voiceOutputEnabled
                                )
                            }
                        }

                        if (editingUserMessageId != null) {
                            Surface(
                                color = MinovaIndigoSecondary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                border = BorderStroke(1.dp, MinovaCyanPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .testTag("editing_message_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MinovaCyanPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Editing message",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MinovaCyanPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = onCancelEdit,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("cancel_edit_message_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel edit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        MinovaComposer(
                            text = composerText,
                            onTextChange = onComposerTextChange,
                            onSend = onSendMessage,
                            onAttachmentClick = { onOpenAttachmentSheet(true) },
                            onMicClick = {
                                if (voiceState == VoiceState.LISTENING) {
                                    onStopVoice()
                                } else {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasMicPermission) {
                                        onStartVoice()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            voiceState = voiceState,
                            selectedAttachment = selectedAttachment,
                            selectedAttachments = selectedAttachments,
                            onClearAttachment = onClearAttachment,
                            onRemoveAttachment = onRemoveAttachment,
                            isStreaming = chatUiState.isStreaming,
                            onStopStreaming = onStopStreaming,
                            enterToSend = settings.enterToSend,
                            autoFocusComposer = settings.autoFocusComposer,
                            showAttachmentPreview = settings.showAttachmentPreview,
                            isWebSearchActive = isWebSearchActive,
                            onToggleWebSearch = onToggleWebSearch
                        )
                    }
                }
            }
        }

        // Attachment Bottom Sheet Overlay
        if (isAttachmentSheetVisible) {
            AttachmentBottomSheet(
                onDismiss = { onOpenAttachmentSheet(false) },
                onSelectOption = { option ->
                    onOpenAttachmentSheet(false)
                    when (option.type) {
                        AttachmentType.IMAGE, AttachmentType.PHOTO, AttachmentType.CAMERA -> {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        AttachmentType.PDF -> {
                            docPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                        AttachmentType.DOCUMENT -> {
                            docPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/plain",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword"
                                )
                            )
                        }
                        AttachmentType.CODE -> {
                            docPickerLauncher.launch(
                                arrayOf(
                                    "text/*",
                                    "application/*",
                                    "*/*"
                                )
                            )
                        }
                    }
                }
            )
        }

        // Feedback / Error Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        )

        // Quick Response Style Sheet / Dialog
        if (showResponseStyleSheet) {
            AlertDialog(
                onDismissRequest = { showResponseStyleSheet = false },
                title = {
                    Text(
                        text = "Response Style",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResponseStyle.entries.forEach { style ->
                            val isSelected = activeResponseStyle == style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MinovaCyanPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        onSelectResponseStyle(style)
                                        showResponseStyleSheet = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("quick_style_option_${style.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectResponseStyle(style)
                                        showResponseStyleSheet = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showResponseStyleSheet = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
