package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AttachmentType
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.util.MarkdownCodeBlockParser
import com.example.data.util.MarkdownContentBlock
import com.example.data.util.MessageClipboardHelper
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary
import com.example.viewmodel.ChatUiState
import kotlinx.coroutines.delay

private fun shareMessage(context: Context, text: String) {
    val sanitized = MessageClipboardHelper.sanitizeForCopy(text)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sanitized)
    }
    val chooser = Intent.createChooser(sendIntent, "Share Message")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationMessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    chatUiState: ChatUiState = ChatUiState.Idle,
    activeSpeakingMessageId: String? = null,
    onToggleSpeech: (messageId: String, content: String) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onEditMessage: (Message) -> Unit = {},
    onDeleteMessage: (Message) -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onRegenerateMessage: (Message) -> Unit = {},
    onContinueWithoutSearch: () -> Unit = {},
    showTimestamps: Boolean = false,
    showAttachmentPreview: Boolean = true,
    isVoiceOutputEnabled: Boolean = true
) {
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    val hasStreamingMessage = messages.any { it.status == MessageStatus.STREAMING }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content, chatUiState) {
        if (messages.isNotEmpty()) {
            if (hasStreamingMessage) {
                listState.scrollToItem(messages.size - 1)
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("conversation_message_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItemRow(
                message = message,
                isSpeaking = activeSpeakingMessageId == message.id,
                onToggleSpeech = { onToggleSpeech(message.id, message.content) },
                onRetry = onRetry,
                onEditMessage = onEditMessage,
                onDeleteMessage = onDeleteMessage,
                onPinMessage = onPinMessage,
                onRegenerateMessage = onRegenerateMessage,
                onContinueWithoutSearch = onContinueWithoutSearch,
                showTimestamps = showTimestamps,
                showAttachmentPreview = showAttachmentPreview,
                isVoiceOutputEnabled = isVoiceOutputEnabled
            )
        }

        val showIndicator = (chatUiState is ChatUiState.Loading || chatUiState is ChatUiState.Uploading || chatUiState is ChatUiState.Analyzing) && !hasStreamingMessage
        if (showIndicator) {
            item(key = "thinking_indicator") {
                val statusText = when (chatUiState) {
                    is ChatUiState.Uploading -> chatUiState.progressText
                    is ChatUiState.Analyzing -> chatUiState.statusText
                    else -> "MINOVA is thinking"
                }
                AssistantThinkingIndicator(statusText = statusText)
            }
        }
    }
}

@Composable
private fun MessageItemRow(
    message: Message,
    isSpeaking: Boolean = false,
    onToggleSpeech: () -> Unit = {},
    onRetry: () -> Unit,
    onEditMessage: (Message) -> Unit = {},
    onDeleteMessage: (Message) -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onRegenerateMessage: (Message) -> Unit = {},
    onContinueWithoutSearch: () -> Unit = {},
    showTimestamps: Boolean = false,
    showAttachmentPreview: Boolean = true,
    isVoiceOutputEnabled: Boolean = true
) {
    val isUser = message.sender == MessageSender.USER
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showUserMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User message bubble
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(MinovaIndigoSecondary.copy(alpha = 0.22f))
                    .border(
                        width = 1.dp,
                        color = MinovaCyanPrimary.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = { showUserMenu = true }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("user_message_bubble_${message.id}")
            ) {
                // Attached files preview if present
                val attachments = message.allAttachments
                if (attachments.isNotEmpty()) {
                    if (!showAttachmentPreview) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MinovaAzureLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${attachments.size} attached file(s): " + attachments.joinToString(", ") { it.fileName },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        attachments.forEach { att ->
                        val isImageAtt = att.type == AttachmentType.IMAGE || att.isImage
                        if (isImageAtt) {
                            val context = LocalContext.current
                            val imageUri = remember(att.uri) {
                                try {
                                    Uri.parse(att.uri)
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                            val imageRequest = remember(att.uri, context) {
                                imageUri?.let { uri ->
                                    ImageRequest.Builder(context)
                                        .data(uri)
                                        .crossfade(true)
                                        .build()
                                }
                            }
                            var isHistoryImageError by remember(att.uri) { mutableStateOf(false) }

                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                if (imageRequest != null && !isHistoryImageError) {
                                    SubcomposeAsyncImage(
                                        model = imageRequest,
                                        contentDescription = "image preview: ${att.fileName}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    ) {
                                        when (painter.state) {
                                            is AsyncImagePainter.State.Loading -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MinovaCyanPrimary
                                                    )
                                                }
                                            }
                                            is AsyncImagePainter.State.Error -> {
                                                isHistoryImageError = true
                                            }
                                            else -> {
                                                SubcomposeAsyncImageContent()
                                            }
                                        }
                                    }
                                }

                                if (isHistoryImageError || imageRequest == null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Preview unavailable",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Preview unavailable" + if (att.formattedSize.isNotBlank()) " • ${att.formattedSize}" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = att.fileName + if (att.formattedSize.isNotBlank()) " (${att.formattedSize})" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        0.5.dp,
                                        MinovaAzureLight.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (att.type == AttachmentType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                    contentDescription = "document attachment",
                                    tint = MinovaAzureLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = att.fileName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (att.formattedSize.isNotBlank()) {
                                        val badge = att.extension.uppercase().ifEmpty { att.type.name }
                                        Text(
                                            text = "$badge • ${att.formattedSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (message.attachmentName != null) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = MinovaAzureLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.attachmentName ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

            }

            // User message action row (timestamp, pinned badge, 3-dot action menu)
            Row(
                modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (message.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned message",
                        tint = MinovaCyanPrimary,
                        modifier = Modifier
                            .size(13.dp)
                            .testTag("pinned_indicator_${message.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (showTimestamps) {
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Box {
                    IconButton(
                        onClick = { showUserMenu = true },
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("user_message_action_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Message options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false },
                        modifier = Modifier.testTag("user_message_dropdown_menu_${message.id}")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit & Resend") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showUserMenu = false
                                onEditMessage(message)
                            },
                            modifier = Modifier.testTag("action_edit_resend_${message.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showUserMenu = false
                                val cleanText = MessageClipboardHelper.sanitizeForCopy(message.content)
                                clipboardManager.setText(AnnotatedString(cleanText))
                                MessageClipboardHelper.copyToClipboard(context, cleanText, "User message")
                            },
                            modifier = Modifier.testTag("action_copy_${message.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                            leadingIcon = { Icon(if (message.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showUserMenu = false
                                onPinMessage(message)
                            },
                            modifier = Modifier.testTag("action_pin_${message.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showUserMenu = false
                                shareMessage(context, message.content)
                            },
                            modifier = Modifier.testTag("action_share_${message.id}")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showUserMenu = false
                                onDeleteMessage(message)
                            },
                            modifier = Modifier.testTag("action_delete_${message.id}")
                        )
                    }
                }
            }
        } else {
            // Assistant message or Error Bubble
            if (message.status == MessageStatus.ERROR) {
                val isWebSearchUnavailable = message.errorMessage?.contains("WEB_SEARCH_UNAVAILABLE", ignoreCase = true) == true ||
                    message.content.contains("WEB_SEARCH_UNAVAILABLE", ignoreCase = true) ||
                    message.content.contains("Web Search is temporarily unavailable", ignoreCase = true)
                AssistantErrorCard(
                    errorMessage = message.content.removePrefix("WEB_SEARCH_UNAVAILABLE: "),
                    timestamp = message.timestamp,
                    showTimestamp = showTimestamps,
                    isWebSearchUnavailable = isWebSearchUnavailable,
                    onRetry = onRetry,
                    onContinueWithoutSearch = onContinueWithoutSearch
                )
            } else {
                AssistantMessageBubble(
                    message = message,
                    isSpeaking = isSpeaking,
                    showTimestamp = showTimestamps,
                    isVoiceOutputEnabled = isVoiceOutputEnabled,
                    onToggleSpeech = onToggleSpeech,
                    onRegenerate = { onRegenerateMessage(message) },
                    onPinMessage = onPinMessage,
                    onDeleteMessage = onDeleteMessage
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: Message,
    isSpeaking: Boolean = false,
    showTimestamp: Boolean = false,
    isVoiceOutputEnabled: Boolean = true,
    onToggleSpeech: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onPinMessage: (Message) -> Unit = {},
    onDeleteMessage: (Message) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isStreaming = message.status == MessageStatus.STREAMING
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    val parsedBlocks = remember(message.content) {
        MarkdownCodeBlockParser.parse(message.content)
    }

    var isMessageCopied by remember { mutableStateOf(false) }
    var showAssistantMenu by remember { mutableStateOf(false) }
    LaunchedEffect(isMessageCopied) {
        if (isMessageCopied) {
            delay(2000)
            isMessageCopied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 580.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .border(
                    1.dp,
                    MinovaCyanPrimary.copy(alpha = if (isStreaming) 0.65f else 0.35f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.minova_icon),
                contentDescription = "MINOVA AI",
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = if (isStreaming) MinovaCyanPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .pointerInput(message.id, isStreaming) {
                    if (!isStreaming) {
                        detectTapGestures(
                            onLongPress = { showAssistantMenu = true }
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("assistant_message_bubble_${message.id}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MINOVA AI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MinovaCyanPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                if (isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MinovaCyanPrimary.copy(alpha = alphaAnim))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (message.content.isEmpty()) "Thinking..." else "Generating...",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MinovaCyanPrimary
                        )
                    }
                }
            }

            if (isStreaming && message.content.isEmpty()) {
                // Animated thinking dots indicator inside bubble
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600, delayMillis = index * 180),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha_$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MinovaCyanPrimary.copy(alpha = dotAlpha))
                        )
                        if (index < 2) Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            } else {
                RichMarkdownView(
                    content = message.content,
                    isStreaming = isStreaming,
                    streamingCursorAlpha = alphaAnim,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val hasContent = message.content.isNotBlank()
            val showSpeaker = !isStreaming && hasContent && isVoiceOutputEnabled
            val showCopy = !isStreaming && hasContent

            if (showSpeaker || showCopy || showTimestamp || message.isPinned) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Speaker TTS button (only when message is complete, not empty, and voice output enabled)
                        if (showSpeaker) {
                            IconButton(
                                onClick = onToggleSpeech,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("tts_speaker_button_${message.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop voice readout" else "Read response aloud",
                                    tint = if (isSpeaking) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Copy Complete Response Button
                        if (showCopy) {
                            Surface(
                                onClick = {
                                    val sanitized = MessageClipboardHelper.sanitizeForCopy(message.content)
                                    clipboardManager.setText(AnnotatedString(sanitized))
                                    MessageClipboardHelper.copyToClipboard(context, sanitized, "MINOVA AI Response")
                                    isMessageCopied = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMessageCopied) MinovaCyanPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("copy_message_button_${message.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isMessageCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                        contentDescription = if (isMessageCopied) "Response copied" else "Copy response",
                                        tint = if (isMessageCopied) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isMessageCopied) "Copied" else "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isMessageCopied) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        modifier = Modifier.testTag("copy_status_${message.id}")
                                    )
                                }
                            }
                        }

                        // Quick Regenerate Button
                        if (!isStreaming && hasContent) {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("regenerate_button_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Regenerate response",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 3-dot Overflow Contextual Menu
                        if (!isStreaming && hasContent) {
                            Box {
                                IconButton(
                                    onClick = { showAssistantMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("assistant_message_action_button_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                val hasCode = remember(message.content) {
                                    MarkdownCodeBlockParser.hasCodeBlocks(message.content)
                                }

                                DropdownMenu(
                                    expanded = showAssistantMenu,
                                    onDismissRequest = { showAssistantMenu = false },
                                    modifier = Modifier.testTag("assistant_message_dropdown_menu_${message.id}")
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Copy Response") },
                                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showAssistantMenu = false
                                            val sanitized = MessageClipboardHelper.sanitizeForCopy(message.content)
                                            clipboardManager.setText(AnnotatedString(sanitized))
                                            MessageClipboardHelper.copyToClipboard(context, sanitized, "MINOVA AI Response")
                                            isMessageCopied = true
                                        },
                                        modifier = Modifier.testTag("action_copy_${message.id}")
                                    )
                                    if (hasCode) {
                                        DropdownMenuItem(
                                            text = { Text("Copy Code") },
                                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                showAssistantMenu = false
                                                val allCode = MarkdownCodeBlockParser.extractAllCode(message.content)
                                                val sanitized = MessageClipboardHelper.sanitizeForCopy(allCode)
                                                clipboardManager.setText(AnnotatedString(sanitized))
                                                MessageClipboardHelper.copyToClipboard(context, sanitized, "Extracted Code")
                                            },
                                            modifier = Modifier.testTag("action_copy_code_${message.id}")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Regenerate") },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showAssistantMenu = false
                                            onRegenerate()
                                        },
                                        modifier = Modifier.testTag("action_regenerate_${message.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                                        leadingIcon = { Icon(if (message.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showAssistantMenu = false
                                            onPinMessage(message)
                                        },
                                        modifier = Modifier.testTag("action_pin_${message.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showAssistantMenu = false
                                            shareMessage(context, message.content)
                                        },
                                        modifier = Modifier.testTag("action_share_${message.id}")
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showAssistantMenu = false
                                            onDeleteMessage(message)
                                        },
                                        modifier = Modifier.testTag("action_delete_${message.id}")
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned message",
                                tint = MinovaCyanPrimary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .testTag("pinned_indicator_${message.id}")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (showTimestamp) {
                            Text(
                                text = message.timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantErrorCard(
    errorMessage: String,
    timestamp: String,
    showTimestamp: Boolean = false,
    isWebSearchUnavailable: Boolean = false,
    onRetry: () -> Unit,
    onContinueWithoutSearch: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 580.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("assistant_error_card")
        ) {
            Text(
                text = if (isWebSearchUnavailable) "Web Search Unavailable" else "Connection / Server Issue",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isWebSearchUnavailable) {
                OutlinedButton(
                    onClick = onContinueWithoutSearch,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MinovaCyanPrimary
                    ),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("continue_without_search_button")
                ) {
                    Text("Continue without Web Search", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                }

                if (showTimestamp) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantThinkingIndicator(statusText: String = "MINOVA is thinking") {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 580.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MinovaCyanPrimary.copy(alpha = 0.15f))
                .border(1.dp, MinovaCyanPrimary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.minova_icon),
                contentDescription = "MINOVA AI",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = MinovaCyanPrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("assistant_thinking_indicator"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MinovaCyanPrimary,
                modifier = Modifier.alpha(alphaAnim)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MinovaCyanPrimary.copy(alpha = alphaAnim))
                    )
                }
            }
        }
    }
}
