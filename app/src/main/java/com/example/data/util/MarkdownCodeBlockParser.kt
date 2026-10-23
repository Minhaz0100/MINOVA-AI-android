package com.example.data.util

sealed interface MarkdownContentBlock {
    data class Text(val content: String) : MarkdownContentBlock

    data class Code(
        val language: String?,
        val code: String,
        val isComplete: Boolean = true
    ) : MarkdownContentBlock {
        val displayLanguage: String
            get() = formatLanguage(language)

        companion object {
            fun formatLanguage(raw: String?): String {
                if (raw.isNullOrBlank()) return "Code"
                val cleaned = raw.trim().lowercase()
                return when (cleaned) {
                    "kotlin", "kt" -> "Kotlin"
                    "java" -> "Java"
                    "python", "py" -> "Python"
                    "javascript", "js" -> "JavaScript"
                    "typescript", "ts" -> "TypeScript"
                    "html", "htm" -> "HTML"
                    "css" -> "CSS"
                    "json" -> "JSON"
                    "sql" -> "SQL"
                    "xml" -> "XML"
                    "bash", "sh", "shell", "zsh" -> "Bash"
                    "c" -> "C"
                    "cpp", "c++" -> "C++"
                    "csharp", "cs", "c#" -> "C#"
                    "go", "golang" -> "Go"
                    "rust", "rs" -> "Rust"
                    "swift" -> "Swift"
                    "dart" -> "Dart"
                    "ruby", "rb" -> "Ruby"
                    "php" -> "PHP"
                    "yaml", "yml" -> "YAML"
                    "markdown", "md" -> "Markdown"
                    "text", "plaintext", "txt" -> "Plain Text"
                    "dockerfile", "docker" -> "Dockerfile"
                    "graphql", "gql" -> "GraphQL"
                    "r" -> "R"
                    "scala" -> "Scala"
                    "toml" -> "TOML"
                    "lua" -> "Lua"
                    else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }
}

/**
 * Structural blocks found inside markdown text sections (between or outside code blocks).
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Blockquote(val lines: List<String>) : MarkdownBlock
    data class ListItem(
        val text: String,
        val isOrdered: Boolean,
        val number: Int? = null,
        val indentLevel: Int = 0
    )
    data class ListBlock(val items: List<ListItem>, val isOrdered: Boolean) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

object MarkdownCodeBlockParser {

    /**
     * Parses markdown text into ordered text and fenced code segments.
     * Handles both completed fenced blocks and unclosed fences during active streaming.
     */
    fun parse(content: String): List<MarkdownContentBlock> {
        if (content.isEmpty()) return emptyList()

        val blocks = mutableListOf<MarkdownContentBlock>()
        val lines = content.lines()
        var inCodeBlock = false
        var currentLanguage: String? = null
        val currentBuffer = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trimStart()

            if (!inCodeBlock) {
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    // Flush accumulated text before code block
                    if (currentBuffer.isNotEmpty()) {
                        blocks.add(MarkdownContentBlock.Text(currentBuffer.toString()))
                        currentBuffer.clear()
                    }
                    inCodeBlock = true
                    val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                    val rawLang = trimmed.removePrefix(fence).trim().ifEmpty { null }
                    currentLanguage = rawLang
                } else {
                    if (currentBuffer.isNotEmpty()) currentBuffer.append("\n")
                    currentBuffer.append(line)
                }
            } else {
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    // Code block closed
                    blocks.add(
                        MarkdownContentBlock.Code(
                            language = currentLanguage,
                            code = currentBuffer.toString(),
                            isComplete = true
                        )
                    )
                    currentBuffer.clear()
                    currentLanguage = null
                    inCodeBlock = false
                } else {
                    if (currentBuffer.isNotEmpty()) currentBuffer.append("\n")
                    currentBuffer.append(line)
                }
            }
        }

        if (inCodeBlock) {
            // Streaming or unclosed fence
            blocks.add(
                MarkdownContentBlock.Code(
                    language = currentLanguage,
                    code = currentBuffer.toString(),
                    isComplete = false
                )
            )
        } else if (currentBuffer.isNotEmpty()) {
            blocks.add(MarkdownContentBlock.Text(currentBuffer.toString()))
        }

        return blocks
    }

    /**
     * Checks if the markdown content contains at least one fenced code block.
     */
    fun hasCodeBlocks(content: String): Boolean {
        return parse(content).any { it is MarkdownContentBlock.Code && it.code.isNotBlank() }
    }

    /**
     * Extracts and combines all code block snippets from the markdown content.
     */
    fun extractAllCode(content: String): String {
        val codeBlocks = parse(content).filterIsInstance<MarkdownContentBlock.Code>()
        return codeBlocks.joinToString("\n\n") { it.code }
    }
}

/**
 * Parser that breaks non-code markdown text into structural elements:
 * Headings, Blockquotes, Lists (ordered/unordered with nesting),
 * Horizontal Rules, Tables, and Paragraphs.
 */
object MarkdownBlockParser {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val HORIZONTAL_RULE_REGEX = Regex("^(?:---+|\\*\\*\\*+|___+)\\s*$")
    private val BULLET_ITEM_REGEX = Regex("^(\\s*)[-*+]\\s+(.+)$")
    private val NUMBERED_ITEM_REGEX = Regex("^(\\s*)(\\d+)[.)]\\s+(.+)$")
    private val BLOCKQUOTE_REGEX = Regex("^>\\s*(.*)$")

    /**
     * Parses a text block into structured Markdown blocks.
     */
    fun parseTextToBlocks(rawText: String): List<MarkdownBlock> {
        if (rawText.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val lines = rawText.lines()
        var i = 0
        val totalLines = lines.size

        val paragraphBuffer = StringBuilder()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val content = paragraphBuffer.toString().trim()
                if (content.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(content))
                }
                paragraphBuffer.clear()
            }
        }

        while (i < totalLines) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Blank line flushes paragraph
            if (trimmed.isEmpty()) {
                flushParagraph()
                i++
                continue
            }

            // 2. Horizontal Rule: ---, ***, ___
            if (HORIZONTAL_RULE_REGEX.matches(trimmed)) {
                flushParagraph()
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 3. Headings: # Heading
            val headingMatch = HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Heading(level, title))
                i++
                continue
            }

            // 4. Blockquotes: > quote
            if (BLOCKQUOTE_REGEX.matches(line.trimStart())) {
                flushParagraph()
                val quoteLines = mutableListOf<String>()
                while (i < totalLines) {
                    val qLine = lines[i]
                    val qMatch = BLOCKQUOTE_REGEX.matchEntire(qLine.trimStart())
                    if (qMatch != null) {
                        quoteLines.add(qMatch.groupValues[1])
                        i++
                    } else if (qLine.trim().isEmpty() && i + 1 < totalLines && BLOCKQUOTE_REGEX.matches(lines[i + 1].trimStart())) {
                        quoteLines.add("")
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.Blockquote(quoteLines))
                continue
            }

            // 5. Tables: lines starting with | or having table format
            if (isTableStart(lines, i)) {
                flushParagraph()
                val (table, nextIdx) = parseTable(lines, i)
                if (table != null) {
                    blocks.add(table)
                    i = nextIdx
                    continue
                }
            }

            // 6. Bullet Lists
            val bulletMatch = BULLET_ITEM_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                val listItems = mutableListOf<MarkdownBlock.ListItem>()
                while (i < totalLines) {
                    val bLine = lines[i]
                    val bMatch = BULLET_ITEM_REGEX.matchEntire(bLine)
                    if (bMatch != null) {
                        val indentSpaces = bMatch.groupValues[1].length
                        val text = bMatch.groupValues[2]
                        listItems.add(
                            MarkdownBlock.ListItem(
                                text = text,
                                isOrdered = false,
                                indentLevel = indentSpaces / 2
                            )
                        )
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.ListBlock(listItems, isOrdered = false))
                continue
            }

            // 7. Numbered Lists
            val numMatch = NUMBERED_ITEM_REGEX.matchEntire(line)
            if (numMatch != null) {
                flushParagraph()
                val listItems = mutableListOf<MarkdownBlock.ListItem>()
                while (i < totalLines) {
                    val nLine = lines[i]
                    val nMatch = NUMBERED_ITEM_REGEX.matchEntire(nLine)
                    if (nMatch != null) {
                        val indentSpaces = nMatch.groupValues[1].length
                        val number = nMatch.groupValues[2].toIntOrNull() ?: 1
                        val text = nMatch.groupValues[3]
                        listItems.add(
                            MarkdownBlock.ListItem(
                                text = text,
                                isOrdered = true,
                                number = number,
                                indentLevel = indentSpaces / 2
                            )
                        )
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.ListBlock(listItems, isOrdered = true))
                continue
            }

            // Regular paragraph line
            if (paragraphBuffer.isNotEmpty()) {
                paragraphBuffer.append("\n")
            }
            paragraphBuffer.append(line)
            i++
        }

        flushParagraph()
        return blocks
    }

    private fun isTableStart(lines: List<String>, idx: Int): Boolean {
        if (idx + 1 >= lines.size) return false
        val line1 = lines[idx].trim()
        val line2 = lines[idx + 1].trim()
        val isHeader = line1.contains("|")
        val isDivider = line2.contains("|") && line2.replace("|", "").replace(":", "").replace("-", "").trim().isEmpty()
        return isHeader && isDivider
    }

    private fun parseTable(lines: List<String>, startIdx: Int): Pair<MarkdownBlock.Table?, Int> {
        try {
            val headerLine = lines[startIdx].trim()
            val headers = splitTableRow(headerLine)
            if (headers.isEmpty()) return Pair(null, startIdx)

            var i = startIdx + 2 // skip header and divider lines
            val rows = mutableListOf<List<String>>()

            while (i < lines.size) {
                val rowLine = lines[i].trim()
                if (rowLine.isEmpty() || !rowLine.contains("|")) break
                val cells = splitTableRow(rowLine)
                if (cells.isNotEmpty()) {
                    rows.add(cells)
                }
                i++
            }

            return Pair(MarkdownBlock.Table(headers, rows), i)
        } catch (_: Exception) {
            return Pair(null, startIdx)
        }
    }

    private fun splitTableRow(row: String): List<String> {
        val trimmed = row.removePrefix("|").removeSuffix("|")
        return trimmed.split("|").map { it.trim() }
    }
}
