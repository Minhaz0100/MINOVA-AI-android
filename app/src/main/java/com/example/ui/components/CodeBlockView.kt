package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.MarkdownContentBlock
import com.example.data.util.MessageClipboardHelper
import com.example.ui.theme.MinovaCyanPrimary
import kotlinx.coroutines.delay

@Composable
fun CodeBlockView(
    codeBlock: MarkdownContentBlock.Code,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val codeBackgroundColor = Color(0xFF0A101D)
    val headerBackgroundColor = Color(0xFF111B30)
    val borderColor = MinovaCyanPrimary.copy(alpha = 0.28f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(codeBackgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .testTag("code_block_container")
    ) {
        // Header Bar: Language Label on Left, Copy Code button on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBackgroundColor)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("code_block_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Language Label
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MinovaCyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = codeBlock.displayLanguage,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.testTag("code_block_language_${codeBlock.displayLanguage.lowercase().replace(" ", "_")}")
                )
            }

            // Copy Code Button (Minimum 48dp touch target for accessibility)
            Surface(
                onClick = {
                    val cleanCode = MessageClipboardHelper.sanitizeForCopy(codeBlock.code)
                    clipboardManager.setText(AnnotatedString(cleanCode))
                    MessageClipboardHelper.copyToClipboard(context, cleanCode, "Code: ${codeBlock.displayLanguage}")
                    isCopied = true
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isCopied) MinovaCyanPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("copy_code_block_button")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (isCopied) "Code copied to clipboard" else "Copy code to clipboard",
                        tint = if (isCopied) MinovaCyanPrimary else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied" else "Copy Code",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isCopied) MinovaCyanPrimary else Color(0xFFCBD5E1),
                        modifier = Modifier.testTag("copy_code_block_status")
                    )
                }
            }
        }

        // Code Body: Monospace typography with horizontal scroll for long lines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("code_block_content")
        ) {
            Row {
                Text(
                    text = codeBlock.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFF1F5F9),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isStreaming && !codeBlock.isComplete) {
                    Text(
                        text = " ▌",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MinovaCyanPrimary.copy(alpha = streamingCursorAlpha)
                    )
                }
            }
        }
    }
}
