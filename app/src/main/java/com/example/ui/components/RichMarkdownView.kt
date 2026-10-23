package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.InlineMarkdownParser
import com.example.data.util.MarkdownBlock
import com.example.data.util.MarkdownBlockParser
import com.example.data.util.MarkdownCodeBlockParser
import com.example.data.util.MarkdownContentBlock
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary

/**
 * High-performance, streaming-compatible Compose view for rendering full assistant Markdown responses.
 * Renders Headings, Paragraphs, Lists, Blockquotes, Tables, Horizontal Rules, and Fenced Code Blocks.
 */
@Composable
fun RichMarkdownView(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f
) {
    val context = LocalContext.current
    val parsedBlocks = remember(content) {
        MarkdownCodeBlockParser.parse(content)
    }

    val onOpenUrl: (String) -> Unit = remember(context) {
        { url ->
            if (InlineMarkdownParser.isValidExternalUrl(url)) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parsedBlocks.forEachIndexed { index, block ->
            val isLastBlock = index == parsedBlocks.lastIndex
            when (block) {
                is MarkdownContentBlock.Code -> {
                    CodeBlockView(
                        codeBlock = block,
                        isStreaming = isStreaming && isLastBlock,
                        streamingCursorAlpha = streamingCursorAlpha,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownContentBlock.Text -> {
                    RichMarkdownTextBlock(
                        text = block.content,
                        isLastBlock = isLastBlock,
                        isStreaming = isStreaming,
                        streamingCursorAlpha = streamingCursorAlpha,
                        onOpenUrl = onOpenUrl
                    )
                }
            }
        }
    }
}

/**
 * Renders the structural blocks within a non-code text section:
 * Headings, Blockquotes, Lists (ordered/unordered with nesting), Tables, Dividers, and Paragraphs.
 */
@Composable
fun RichMarkdownTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    isLastBlock: Boolean = false,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f,
    onOpenUrl: (String) -> Unit = {}
) {
    val blocks = remember(text) {
        MarkdownBlockParser.parseTextToBlocks(text)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastSubBlock = isLastBlock && (index == blocks.lastIndex)
            when (block) {
                is MarkdownBlock.Heading -> {
                    RichHeadingView(
                        heading = block,
                        isLastSubBlock = isLastSubBlock,
                        isStreaming = isStreaming,
                        streamingCursorAlpha = streamingCursorAlpha,
                        onOpenUrl = onOpenUrl
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    RichParagraphView(
                        paragraph = block,
                        isLastSubBlock = isLastSubBlock,
                        isStreaming = isStreaming,
                        streamingCursorAlpha = streamingCursorAlpha,
                        onOpenUrl = onOpenUrl
                    )
                }
                is MarkdownBlock.Blockquote -> {
                    RichBlockquoteView(
                        blockquote = block,
                        isLastSubBlock = isLastSubBlock,
                        isStreaming = isStreaming,
                        streamingCursorAlpha = streamingCursorAlpha,
                        onOpenUrl = onOpenUrl
                    )
                }
                is MarkdownBlock.ListBlock -> {
                    RichListBlockView(
                        listBlock = block,
                        isLastSubBlock = isLastSubBlock,
                        isStreaming = isStreaming,
                        streamingCursorAlpha = streamingCursorAlpha,
                        onOpenUrl = onOpenUrl
                    )
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("markdown_horizontal_rule"),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                is MarkdownBlock.Table -> {
                    RichTableView(
                        table = block,
                        onOpenUrl = onOpenUrl
                    )
                }
            }
        }
    }
}

@Composable
fun RichHeadingView(
    heading: MarkdownBlock.Heading,
    modifier: Modifier = Modifier,
    isLastSubBlock: Boolean = false,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f,
    onOpenUrl: (String) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val inlineCodeBg = if (isDark) Color(0x3300E5FF) else Color(0x1F00E5FF)
    val inlineCodeColor = if (isDark) MinovaCyanPrimary else Color(0xFF0F766E)

    val (fontSize, fontWeight, color, topPadding) = when (heading.level) {
        1 -> Quadruple(22.sp, FontWeight.Bold, MaterialTheme.colorScheme.onBackground, 8.dp)
        2 -> Quadruple(19.sp, FontWeight.Bold, MaterialTheme.colorScheme.onBackground, 8.dp)
        3 -> Quadruple(17.sp, FontWeight.SemiBold, MinovaCyanPrimary, 6.dp)
        4 -> Quadruple(15.sp, FontWeight.SemiBold, MaterialTheme.colorScheme.onBackground, 4.dp)
        5 -> Quadruple(14.sp, FontWeight.Bold, MaterialTheme.colorScheme.onBackground, 4.dp)
        else -> Quadruple(13.sp, FontWeight.Bold, MaterialTheme.colorScheme.onSurfaceVariant, 2.dp)
    }

    val annotated = remember(heading.text, isDark) {
        InlineMarkdownParser.parse(
            text = heading.text,
            linkColor = MinovaCyanPrimary,
            inlineCodeBg = inlineCodeBg,
            inlineCodeColor = inlineCodeColor,
            onLinkClick = onOpenUrl
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp)
            .semantics { this.heading() }
            .testTag("markdown_heading_${heading.level}"),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = annotated,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            lineHeight = (fontSize.value * 1.35).sp
        )
        if (isStreaming && isLastSubBlock) {
            Text(
                text = " ▌",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = MinovaCyanPrimary.copy(alpha = streamingCursorAlpha)
            )
        }
    }
}

@Composable
fun RichParagraphView(
    paragraph: MarkdownBlock.Paragraph,
    modifier: Modifier = Modifier,
    isLastSubBlock: Boolean = false,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f,
    onOpenUrl: (String) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val inlineCodeBg = if (isDark) Color(0x3300E5FF) else Color(0x1F00E5FF)
    val inlineCodeColor = if (isDark) MinovaCyanPrimary else Color(0xFF0F766E)

    val annotated = remember(paragraph.text, isDark) {
        InlineMarkdownParser.parse(
            text = paragraph.text,
            linkColor = MinovaCyanPrimary,
            inlineCodeBg = inlineCodeBg,
            inlineCodeColor = inlineCodeColor,
            onLinkClick = onOpenUrl
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (isStreaming && isLastSubBlock) {
            Text(
                text = " ▌",
                style = MaterialTheme.typography.bodyLarge,
                color = MinovaCyanPrimary.copy(alpha = streamingCursorAlpha)
            )
        }
    }
}

@Composable
fun RichBlockquoteView(
    blockquote: MarkdownBlock.Blockquote,
    modifier: Modifier = Modifier,
    isLastSubBlock: Boolean = false,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f,
    onOpenUrl: (String) -> Unit = {}
) {
    val quoteContent = blockquote.lines.joinToString("\n")
    val isDark = isSystemInDarkTheme()
    val inlineCodeBg = if (isDark) Color(0x3300E5FF) else Color(0x1F00E5FF)
    val inlineCodeColor = if (isDark) MinovaCyanPrimary else Color(0xFF0F766E)

    val annotated = remember(quoteContent, isDark) {
        InlineMarkdownParser.parse(
            text = quoteContent,
            linkColor = MinovaCyanPrimary,
            inlineCodeBg = inlineCodeBg,
            inlineCodeColor = inlineCodeColor,
            onLinkClick = onOpenUrl
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("markdown_blockquote"),
        color = MinovaIndigoSecondary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Cyan vertical quote bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinovaCyanPrimary.copy(alpha = 0.85f))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isStreaming && isLastSubBlock) {
                    Text(
                        text = " ▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MinovaCyanPrimary.copy(alpha = streamingCursorAlpha)
                    )
                }
            }
        }
    }
}

@Composable
fun RichListBlockView(
    listBlock: MarkdownBlock.ListBlock,
    modifier: Modifier = Modifier,
    isLastSubBlock: Boolean = false,
    isStreaming: Boolean = false,
    streamingCursorAlpha: Float = 1.0f,
    onOpenUrl: (String) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val inlineCodeBg = if (isDark) Color(0x3300E5FF) else Color(0x1F00E5FF)
    val inlineCodeColor = if (isDark) MinovaCyanPrimary else Color(0xFF0F766E)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listBlock.items.forEachIndexed { itemIdx, item ->
            val isItemLast = isLastSubBlock && (itemIdx == listBlock.items.lastIndex)
            val indentDp = (item.indentLevel.coerceIn(0, 4) * 18).dp

            val annotated = remember(item.text, isDark) {
                InlineMarkdownParser.parse(
                    text = item.text,
                    linkColor = MinovaCyanPrimary,
                    inlineCodeBg = inlineCodeBg,
                    inlineCodeColor = inlineCodeColor,
                    onLinkClick = onOpenUrl
                )
            }

            val bulletSymbol = if (item.isOrdered) {
                "${item.number ?: (itemIdx + 1)}."
            } else {
                when (item.indentLevel % 3) {
                    0 -> "•"
                    1 -> "◦"
                    else -> "▪"
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentDp)
                    .testTag(if (item.isOrdered) "markdown_ordered_item" else "markdown_bullet_item"),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = bulletSymbol,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MinovaCyanPrimary,
                    modifier = Modifier.width(if (item.isOrdered) 26.dp else 16.dp)
                )
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    )
                    if (isStreaming && isItemLast) {
                        Text(
                            text = " ▌",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MinovaCyanPrimary.copy(alpha = streamingCursorAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RichTableView(
    table: MarkdownBlock.Table,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val inlineCodeBg = if (isDark) Color(0x3300E5FF) else Color(0x1F00E5FF)
    val inlineCodeColor = if (isDark) MinovaCyanPrimary else Color(0xFF0F766E)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(8.dp)
            )
            .horizontalScroll(rememberScrollState())
            .testTag("markdown_table_container")
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            table.headers.forEach { header ->
                val annotatedHeader = remember(header, isDark) {
                    InlineMarkdownParser.parse(
                        text = header,
                        linkColor = MinovaCyanPrimary,
                        inlineCodeBg = inlineCodeBg,
                        inlineCodeColor = inlineCodeColor,
                        onLinkClick = onOpenUrl
                    )
                }
                Box(
                    modifier = Modifier
                        .widthIn(min = 100.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = annotatedHeader,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MinovaCyanPrimary
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            thickness = 1.dp
        )

        // Table Rows
        table.rows.forEachIndexed { rowIdx, rowCells ->
            val rowBg = if (rowIdx % 2 == 1) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            } else {
                Color.Transparent
            }

            Row(
                modifier = Modifier
                    .background(rowBg)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                rowCells.forEach { cell ->
                    val annotatedCell = remember(cell, isDark) {
                        InlineMarkdownParser.parse(
                            text = cell,
                            linkColor = MinovaCyanPrimary,
                            inlineCodeBg = inlineCodeBg,
                            inlineCodeColor = inlineCodeColor,
                            onLinkClick = onOpenUrl
                        )
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 100.dp)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = annotatedCell,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (rowIdx < table.rows.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
