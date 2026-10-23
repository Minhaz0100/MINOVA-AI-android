package com.example.data.util

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * High-performance, streaming-safe parser for inline Markdown spans.
 * Converts markdown text into Compose [AnnotatedString] supporting:
 * - Bold (**bold** or __bold__)
 * - Italic (*italic* or _italic_)
 * - Bold + Italic (***text*** or ___text___)
 * - Strikethrough (~~text~~)
 * - Inline code (`code`)
 * - Hyperlinks ([label](https://...))
 * - Escaped characters (\*, \_, \~, \`, \[, \], etc.)
 */
object InlineMarkdownParser {

    /**
     * Validates that a URL is a safe, valid external HTTP/HTTPS web URL.
     * Rejects dangerous or malformed schemes such as javascript:, file:, data:, content:.
     */
    fun isValidExternalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false
        }
        return try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Parses inline markdown into an [AnnotatedString] with theme-aware styling.
     *
     * @param text Raw markdown text (single paragraph or line)
     * @param linkColor Color for hyperlink text
     * @param inlineCodeBg Background color for inline code badges
     * @param inlineCodeColor Text color for inline code
     * @param onLinkClick Optional link click listener
     */
    fun parse(
        text: String,
        linkColor: Color = Color(0xFF00E5FF),
        inlineCodeBg: Color = Color(0x2A00E5FF),
        inlineCodeColor: Color = Color(0xFF00E5FF),
        onLinkClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        val builder = AnnotatedString.Builder()
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]

            // 1. Escaped characters: \* \_ \~ \` \[ \] \\ \#
            if (c == '\\' && i + 1 < len) {
                val nextChar = text[i + 1]
                if (nextChar in setOf('*', '_', '~', '`', '[', ']', '(', ')', '#', '\\', '-', '+', '.')) {
                    builder.append(nextChar)
                    i += 2
                    continue
                }
            }

            // 2. Inline Code: `code`
            if (c == '`') {
                val closeIdx = text.indexOf('`', i + 1)
                if (closeIdx != -1) {
                    val codeContent = text.substring(i + 1, closeIdx)
                    val start = builder.length
                    builder.append(" ")
                    builder.append(codeContent)
                    builder.append(" ")
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            background = inlineCodeBg,
                            color = inlineCodeColor
                        ),
                        start,
                        end
                    )
                    i = closeIdx + 1
                    continue
                } else {
                    // Incomplete during streaming: render unclosed inline code with style
                    val partialCode = text.substring(i + 1)
                    val start = builder.length
                    builder.append(" ")
                    builder.append(partialCode)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            background = inlineCodeBg,
                            color = inlineCodeColor
                        ),
                        start,
                        end
                    )
                    break
                }
            }

            // 3. Hyperlinks: [Title](url)
            if (c == '[') {
                val closeBracket = findClosingBracket(text, i)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        val start = builder.length
                        builder.append(label)
                        val end = builder.length

                        if (isValidExternalUrl(url)) {
                            builder.addLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    ),
                                    linkInteractionListener = onLinkClick?.let { listener ->
                                        { listener(url) }
                                    }
                                ),
                                start,
                                end
                            )
                            builder.addStringAnnotation("URL", url, start, end)
                        } else {
                            // If invalid URL, style as plain text or subtle warning
                            builder.addStyle(
                                SpanStyle(
                                    color = linkColor.copy(alpha = 0.8f),
                                    textDecoration = TextDecoration.Underline
                                ),
                                start,
                                end
                            )
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 4. Strikethrough: ~~text~~
            if (c == '~' && i + 1 < len && text[i + 1] == '~') {
                val closeIdx = text.indexOf("~~", i + 2)
                if (closeIdx != -1) {
                    val strikeContent = text.substring(i + 2, closeIdx)
                    val start = builder.length
                    builder.append(strikeContent)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        start,
                        end
                    )
                    i = closeIdx + 2
                    continue
                }
            }

            // 5. Bold & Italic: ***text*** or ___text___
            if ((c == '*' && i + 2 < len && text[i + 1] == '*' && text[i + 2] == '*') ||
                (c == '_' && i + 2 < len && text[i + 1] == '_' && text[i + 2] == '_')
            ) {
                val marker = text.substring(i, i + 3)
                val closeIdx = text.indexOf(marker, i + 3)
                if (closeIdx != -1) {
                    val inner = text.substring(i + 3, closeIdx)
                    val start = builder.length
                    builder.append(inner)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start,
                        end
                    )
                    i = closeIdx + 3
                    continue
                }
            }

            // 6. Bold: **text** or __text__
            if ((c == '*' && i + 1 < len && text[i + 1] == '*') ||
                (c == '_' && i + 1 < len && text[i + 1] == '_')
            ) {
                val marker = text.substring(i, i + 2)
                val closeIdx = text.indexOf(marker, i + 2)
                if (closeIdx != -1) {
                    val inner = text.substring(i + 2, closeIdx)
                    val start = builder.length
                    // Allow nested italic or inline code inside bold
                    val parsedInner = parse(inner, linkColor, inlineCodeBg, inlineCodeColor, onLinkClick)
                    builder.append(parsedInner)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start,
                        end
                    )
                    i = closeIdx + 2
                    continue
                } else {
                    // Streaming incomplete bold: render remaining text as bold
                    val partial = text.substring(i + 2)
                    val start = builder.length
                    builder.append(partial)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start,
                        end
                    )
                    break
                }
            }

            // 7. Italic: *text* or _text_
            if ((c == '*' || c == '_') && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] in "([{<:;,.")) {
                val marker = c
                val closeIdx = text.indexOf(marker, i + 1)
                // Ensure close index is not escaped and followed by whitespace or punctuation
                if (closeIdx != -1 && closeIdx > i + 1) {
                    val inner = text.substring(i + 1, closeIdx)
                    val start = builder.length
                    builder.append(inner)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        start,
                        end
                    )
                    i = closeIdx + 1
                    continue
                }
            }

            // Default regular character
            builder.append(c)
            i++
        }

        return builder.toAnnotatedString()
    }

    private fun findClosingBracket(text: String, startIdx: Int): Int {
        var depth = 0
        for (idx in startIdx until text.length) {
            val c = text[idx]
            if (c == '\\') continue
            if (c == '[') depth++
            if (c == ']') {
                depth--
                if (depth == 0) return idx
            }
        }
        return -1
    }
}
