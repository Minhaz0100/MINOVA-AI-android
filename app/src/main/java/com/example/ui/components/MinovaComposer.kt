package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.data.model.Attachment
import com.example.data.model.AttachmentType
import com.example.data.voice.VoiceState
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.util.FileValidator

@Composable
fun MinovaComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachmentClick: () -> Unit,
    onMicClick: () -> Unit,
    voiceState: VoiceState = VoiceState.IDLE,
    selectedAttachment: Attachment? = null,
    selectedAttachments: List<Attachment> = if (selectedAttachment != null) listOf(selectedAttachment) else emptyList(),
    onClearAttachment: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    isStreaming: Boolean = false,
    onStopStreaming: () -> Unit = {},
    enterToSend: Boolean = true,
    autoFocusComposer: Boolean = false,
    showAttachmentPreview: Boolean = true,
    isWebSearchActive: Boolean = false,
    onToggleWebSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveAttachments = if (selectedAttachments.isNotEmpty()) {
        selectedAttachments
    } else if (selectedAttachment != null) {
        listOf(selectedAttachment)
    } else {
        emptyList()
    }
    val isSendEnabled = text.trim().isNotEmpty() || effectiveAttachments.isNotEmpty()

    val sendBgColor by animateColorAsState(
        targetValue = when {
            isStreaming -> MinovaCyanPrimary
            isSendEnabled -> MinovaCyanPrimary
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        },
        label = "send_bg_anim"
    )
    val sendIconColor by animateColorAsState(
        targetValue = when {
            isStreaming -> Color(0xFF06101E)
            isSendEnabled -> Color(0xFF06101E)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
        label = "send_icon_color_anim"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime)
            ),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selected attachments preview card or row
                AnimatedVisibility(
                    visible = effectiveAttachments.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (!showAttachmentPreview) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${effectiveAttachments.size} attached: " + effectiveAttachments.joinToString(", ") { it.fileName },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { onClearAttachment() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear attachments",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else if (effectiveAttachments.size == 1) {
                        val singleAtt = effectiveAttachments.first()
                        val context = LocalContext.current
                        val isImageAttachment = singleAtt.type == AttachmentType.IMAGE || singleAtt.isImage
                        val imageUri = remember(singleAtt.uri) {
                            try {
                                Uri.parse(singleAtt.uri)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        val imageRequest = remember(singleAtt.uri, context) {
                            imageUri?.let { uri ->
                                ImageRequest.Builder(context)
                                    .data(uri)
                                    .crossfade(true)
                                    .build()
                            }
                        }
                        var isPreviewLoadError by remember(singleAtt.uri) { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MinovaAzureLight.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("composer_attachment_preview"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isImageAttachment) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            0.5.dp,
                                            if (isPreviewLoadError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageRequest != null) {
                                        SubcomposeAsyncImage(
                                            model = imageRequest,
                                            contentDescription = "Image preview: ${singleAtt.fileName}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        ) {
                                            when (painter.state) {
                                                is AsyncImagePainter.State.Loading -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MinovaAzureLight.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MinovaCyanPrimary
                                                        )
                                                    }
                                                }
                                                is AsyncImagePainter.State.Error -> {
                                                    isPreviewLoadError = true
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.BrokenImage,
                                                            contentDescription = "Preview unavailable",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    isPreviewLoadError = false
                                                    SubcomposeAsyncImageContent()
                                                }
                                            }
                                        }
                                    } else {
                                        isPreviewLoadError = true
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Preview unavailable",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MinovaAzureLight.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (singleAtt.type == AttachmentType.PDF) {
                                            Icons.Default.PictureAsPdf
                                        } else {
                                            Icons.Default.Description
                                        },
                                        contentDescription = "document attachment",
                                        tint = MinovaAzureLight,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = singleAtt.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPreviewLoadError) {
                                        Text(
                                            text = "Preview unavailable",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val badgeText = singleAtt.extension.uppercase().ifEmpty {
                                            if (isImageAttachment) "IMAGE" else singleAtt.type.name
                                        }
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MinovaAzureLight
                                        )
                                    }
                                    if (singleAtt.formattedSize.isNotBlank()) {
                                        Text(
                                            text = " • ${singleAtt.formattedSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onRemoveAttachment(singleAtt.attachmentId)
                                        onClearAttachment()
                                    }
                                    .testTag("remove_attachment_button")
                                    .testTag("composer_clear_attachment"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "remove attachment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (effectiveAttachments.size > 1) {
                        // Multi-attachment carousel preview
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("composer_attachment_preview")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Attachments (${effectiveAttachments.size}/${FileValidator.MAX_ATTACHMENTS_PER_MESSAGE})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MinovaAzureLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable {
                                            onClearAttachment()
                                            effectiveAttachments.forEach { onRemoveAttachment(it.attachmentId) }
                                        }
                                        .padding(4.dp)
                                )
                            }

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(effectiveAttachments, key = { it.attachmentId }) { att ->
                                    val context = LocalContext.current
                                    val isImage = att.type == AttachmentType.IMAGE || att.isImage
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

                                    Row(
                                        modifier = Modifier
                                            .widthIn(min = 160.dp, max = 220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                width = 1.dp,
                                                color = MinovaAzureLight.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                                            .testTag("composer_attachment_item_${att.attachmentId}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isImage) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (imageRequest != null) {
                                                    SubcomposeAsyncImage(
                                                        model = imageRequest,
                                                        contentDescription = att.fileName,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Image,
                                                        contentDescription = "Image thumbnail",
                                                        tint = MinovaAzureLight,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MinovaAzureLight.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (att.type == AttachmentType.PDF) {
                                                        Icons.Default.PictureAsPdf
                                                    } else {
                                                        Icons.Default.Description
                                                    },
                                                    contentDescription = "Document icon",
                                                    tint = MinovaAzureLight,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val badgeText = att.extension.uppercase().ifEmpty {
                                                if (isImage) "IMG" else att.type.name
                                            }
                                            Text(
                                                text = "$badgeText • ${att.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onRemoveAttachment(att.attachmentId)
                                                }
                                                .testTag("remove_attachment_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "remove ${att.fileName}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Main Rounded Composer Shell
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button
                    IconButton(
                        onClick = onAttachmentClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("composer_attachment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "attach button",
                            tint = if (selectedAttachment != null) MinovaAzureLight else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Web Search toggle button
                    IconButton(
                        onClick = onToggleWebSearch,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("composer_web_search_toggle")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isWebSearchActive) MinovaCyanPrimary.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isWebSearchActive) MinovaCyanPrimary.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = if (isWebSearchActive) "Web search enabled" else "Web search disabled",
                                tint = if (isWebSearchActive) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Flexible multi-line Text input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(autoFocusComposer) {
                            if (autoFocusComposer) {
                                delay(300)
                                try {
                                    focusRequester.requestFocus()
                                } catch (_: Throwable) {}
                            }
                        }

                        if (text.isEmpty()) {
                            val placeholderText = when (voiceState) {
                                VoiceState.LISTENING -> "Listening..."
                                VoiceState.PROCESSING -> "Transcribing..."
                                VoiceState.ERROR -> "Voice input error"
                                VoiceState.IDLE -> "Ask MINOVA anything..."
                            }
                            val placeholderColor = when (voiceState) {
                                VoiceState.LISTENING -> MinovaCyanPrimary
                                VoiceState.PROCESSING -> MinovaAzureLight
                                VoiceState.ERROR -> MaterialTheme.colorScheme.error
                                VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            }
                            Text(
                                text = placeholderText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = placeholderColor,
                                fontWeight = if (voiceState == VoiceState.LISTENING) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 24.dp, max = 120.dp)
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (enterToSend && event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                                        if (isSendEnabled) {
                                            onSend()
                                            true
                                        } else {
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .testTag("composer_text_input"),
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (isSendEnabled) {
                                        onSend()
                                    }
                                }
                            ),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            ),
                            cursorBrush = SolidColor(MinovaCyanPrimary),
                            maxLines = 5
                        )
                    }

                    // Microphone button
                    val isListening = voiceState == VoiceState.LISTENING
                    val isProcessing = voiceState == VoiceState.PROCESSING
                    val isError = voiceState == VoiceState.ERROR

                    val micBgColor by animateColorAsState(
                        targetValue = when {
                            isListening -> MinovaCyanPrimary.copy(alpha = 0.22f)
                            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            else -> Color.Transparent
                        },
                        label = "mic_bg_anim"
                    )
                    val micIconTint by animateColorAsState(
                        targetValue = when {
                            isListening -> MinovaCyanPrimary
                            isProcessing -> MinovaCyanPrimary
                            isError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "mic_tint_anim"
                    )

                    val micDescription = when (voiceState) {
                        VoiceState.LISTENING -> "Stop voice input"
                        VoiceState.PROCESSING -> "Processing voice input"
                        VoiceState.ERROR -> "Voice input error"
                        VoiceState.IDLE -> "Voice input"
                    }

                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(micBgColor)
                            .testTag("composer_microphone_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MinovaCyanPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = micDescription,
                                tint = micIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Send or Stop Button
                    if (isStreaming) {
                        IconButton(
                            onClick = onStopStreaming,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(sendBgColor)
                                .testTag("composer_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop generating",
                                tint = sendIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { if (isSendEnabled) onSend() },
                            enabled = isSendEnabled,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(sendBgColor)
                                .testTag("composer_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "send button",
                                tint = sendIconColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
