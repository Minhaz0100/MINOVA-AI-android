package com.example

import com.example.data.util.InlineMarkdownParser
import com.example.data.util.MarkdownBlock
import com.example.data.util.MarkdownBlockParser
import com.example.data.util.MarkdownCodeBlockParser
import com.example.data.util.MarkdownContentBlock
import com.example.data.util.MessageClipboardHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkdownRenderingTest {

    // 1. HEADINGS
    @Test
    fun testHeadingsAllLevelsAndBengali() {
        val markdown = """
            # Heading Level 1
            ## Heading Level 2
            ### Heading Level 3
            #### Heading Level 4
            ##### Heading Level 5
            ###### Heading Level 6
            ## বাংলা শিরোনাম (Bengali Heading)
        """.trimIndent()

        val blocks = MarkdownBlockParser.parseTextToBlocks(markdown)
        val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()

        assertEquals(7, headings.size)
        assertEquals(1, headings[0].level)
        assertEquals("Heading Level 1", headings[0].text)

        assertEquals(2, headings[1].level)
        assertEquals("Heading Level 2", headings[1].text)

        assertEquals(3, headings[2].level)
        assertEquals("Heading Level 3", headings[2].text)

        assertEquals(4, headings[3].level)
        assertEquals("Heading Level 4", headings[3].text)

        assertEquals(5, headings[4].level)
        assertEquals("Heading Level 5", headings[4].text)

        assertEquals(6, headings[5].level)
        assertEquals("Heading Level 6", headings[5].text)

        assertEquals(2, headings[6].level)
        assertEquals("বাংলা শিরোনাম (Bengali Heading)", headings[6].text)
    }

    // 2. BOLD, ITALIC, BOLD+ITALIC, STRIKETHROUGH
    @Test
    fun testInlineMarkdownStyles() {
        val boldText = "This is **bold text** in chat"
        val boldAnnotated = InlineMarkdownParser.parse(boldText)
        assertEquals("This is bold text in chat", boldAnnotated.text)
        assertTrue(boldAnnotated.spanStyles.isNotEmpty())

        val italicText = "This is *italic text* in chat"
        val italicAnnotated = InlineMarkdownParser.parse(italicText)
        assertEquals("This is italic text in chat", italicAnnotated.text)
        assertTrue(italicAnnotated.spanStyles.isNotEmpty())

        val strikethroughText = "This is ~~deprecated code~~ here"
        val strikeAnnotated = InlineMarkdownParser.parse(strikethroughText)
        assertEquals("This is deprecated code here", strikeAnnotated.text)
        assertTrue(strikeAnnotated.spanStyles.isNotEmpty())

        val boldItalicText = "This is ***crucial and emphasized*** note"
        val boldItalicAnnotated = InlineMarkdownParser.parse(boldItalicText)
        assertEquals("This is crucial and emphasized note", boldItalicAnnotated.text)
        assertTrue(boldItalicAnnotated.spanStyles.isNotEmpty())
    }

    // 3. LISTS (ORDERED, UNORDERED, NESTED)
    @Test
    fun testUnorderedAndOrderedListsWithNesting() {
        val listMarkdown = """
            - First bullet
            - Second bullet
              - Nested child bullet
            - Third bullet

            1. Step one
            2. Step two
               1. Sub-step A
            3. Step three
        """.trimIndent()

        val blocks = MarkdownBlockParser.parseTextToBlocks(listMarkdown)
        val listBlocks = blocks.filterIsInstance<MarkdownBlock.ListBlock>()
        assertEquals(2, listBlocks.size)

        // Unordered list
        val unordered = listBlocks[0]
        assertFalse(unordered.isOrdered)
        assertEquals(4, unordered.items.size)
        assertEquals("First bullet", unordered.items[0].text)
        assertEquals(0, unordered.items[0].indentLevel)
        assertEquals("Second bullet", unordered.items[1].text)
        assertEquals(0, unordered.items[1].indentLevel)
        assertEquals("Nested child bullet", unordered.items[2].text)
        assertEquals(1, unordered.items[2].indentLevel)
        assertEquals("Third bullet", unordered.items[3].text)
        assertEquals(0, unordered.items[3].indentLevel)

        // Ordered list
        val ordered = listBlocks[1]
        assertTrue(ordered.isOrdered)
        assertEquals(4, ordered.items.size)
        assertEquals("Step one", ordered.items[0].text)
        assertEquals(1, ordered.items[0].number)
        assertEquals(0, ordered.items[0].indentLevel)
        assertEquals("Step two", ordered.items[1].text)
        assertEquals(2, ordered.items[1].number)
        assertEquals(0, ordered.items[1].indentLevel)
        assertEquals("Sub-step A", ordered.items[2].text)
        assertEquals(1, ordered.items[2].number)
        assertEquals(1, ordered.items[2].indentLevel)
        assertEquals("Step three", ordered.items[3].text)
        assertEquals(3, ordered.items[3].number)
        assertEquals(0, ordered.items[3].indentLevel)
    }

    // 4. INLINE CODE
    @Test
    fun testInlineCodeFormatting() {
        val inlineMarkdown = "Use `RecyclerView` and `npm install` for `/api/chat`."
        val annotated = InlineMarkdownParser.parse(inlineMarkdown)

        assertTrue(annotated.text.contains("RecyclerView"))
        assertTrue(annotated.text.contains("npm install"))
        assertTrue(annotated.text.contains("/api/chat"))
        // SpanStyles must be applied for each inline code segment
        assertTrue(annotated.spanStyles.size >= 3)
    }

    // 5. FENCED CODE BLOCKS
    @Test
    fun testFencedCodeBlockPreservesWhitespaceAndIndent() {
        val pythonCode = """
            def fib(n):
                if n <= 1:
                    return n
                return fib(n - 1) + fib(n - 2)
        """.trimIndent()

        val markdown = "```python\n$pythonCode\n```"
        val blocks = MarkdownCodeBlockParser.parse(markdown)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownContentBlock.Code)
        val codeBlock = blocks[0] as MarkdownContentBlock.Code
        assertEquals("Python", codeBlock.displayLanguage)
        assertTrue(codeBlock.isComplete)
        assertEquals(pythonCode, codeBlock.code.trim())
    }

    // 6. LANGUAGE DETECTION & ALIASES
    @Test
    fun testLanguageDetectionForAllStandardLanguages() {
        val testCases = mapOf(
            "cpp" to "C++",
            "java" to "Java",
            "kotlin" to "Kotlin",
            "kt" to "Kotlin",
            "python" to "Python",
            "py" to "Python",
            "javascript" to "JavaScript",
            "js" to "JavaScript",
            "typescript" to "TypeScript",
            "ts" to "TypeScript",
            "html" to "HTML",
            "css" to "CSS",
            "json" to "JSON",
            "bash" to "Bash",
            "sh" to "Bash",
            "shell" to "Bash",
            "sql" to "SQL",
            "xml" to "XML",
            "yaml" to "YAML",
            "yml" to "YAML",
            "c" to "C",
            "csharp" to "C#",
            "cs" to "C#",
            "go" to "Go",
            "rust" to "Rust",
            "rs" to "Rust",
            "ruby" to "Ruby",
            "rb" to "Ruby",
            "swift" to "Swift",
            "dart" to "Dart"
        )

        testCases.forEach { (langKey, expectedDisplay) ->
            val actual = MarkdownContentBlock.Code.formatLanguage(langKey)
            assertEquals("Language alias for '$langKey' failed", expectedDisplay, actual)
        }
    }

    // 7. MULTIPLE CODE BLOCKS
    @Test
    fun testMultipleCodeBlocksInSingleResponse() {
        val response = """
            ### Solution

            First, update build.gradle:
            ```groovy
            implementation("com.squareup.retrofit2:retrofit:2.11.0")
            ```

            Then write the interface:
            ```kotlin
            interface ApiService {
                @GET("v1/users")
                suspend fun getUsers(): List<User>
            }
            ```

            Finally in Python for backend verification:
            ```python
            import requests
            res = requests.get("http://localhost:8080/v1/users")
            ```
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(response)
        val codeBlocks = blocks.filterIsInstance<MarkdownContentBlock.Code>()
        assertEquals(3, codeBlocks.size)

        assertEquals("Groovy", codeBlocks[0].displayLanguage)
        assertEquals("Kotlin", codeBlocks[1].displayLanguage)
        assertEquals("Python", codeBlocks[2].displayLanguage)

        assertTrue(codeBlocks[0].code.contains("retrofit2"))
        assertTrue(codeBlocks[1].code.contains("interface ApiService"))
        assertTrue(codeBlocks[2].code.contains("requests.get"))
    }

    // 8. COPY CODE EXTRACTION
    @Test
    fun testCopyCodeExtractionExcludesFences() {
        val markdown = """
            Here is the code:
            ```javascript
            console.log("MINOVA AI");
            ```
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(markdown)
        val codeBlock = blocks.filterIsInstance<MarkdownContentBlock.Code>().first()

        assertEquals("console.log(\"MINOVA AI\");", codeBlock.code.trim())
        assertFalse(codeBlock.code.contains("```"))
        assertFalse(codeBlock.code.contains("javascript"))
    }

    // 9. STREAMING / INCOMPLETE BLOCKS
    @Test
    fun testStreamingIncompleteMarkdownAndCodeBlocks() {
        val streamingIncompleteCode = """
            Here is the draft:
            ```typescript
            interface Config {
                apiKey: string;
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(streamingIncompleteCode)
        assertEquals(2, blocks.size)
        assertTrue(blocks[1] is MarkdownContentBlock.Code)

        val codeBlock = blocks[1] as MarkdownContentBlock.Code
        assertFalse("Incomplete code block must report isComplete=false", codeBlock.isComplete)
        assertEquals("TypeScript", codeBlock.displayLanguage)
        assertTrue(codeBlock.code.contains("interface Config"))

        // Incomplete inline code during streaming
        val partialInline = "The variable `incompleteCodeName"
        val annotated = InlineMarkdownParser.parse(partialInline)
        assertTrue(annotated.text.contains("incompleteCodeName"))
    }

    // 10. LINKS & URL VALIDATION
    @Test
    fun testLinksAndUrlSafetyValidation() {
        assertTrue(InlineMarkdownParser.isValidExternalUrl("https://developer.android.com"))
        assertTrue(InlineMarkdownParser.isValidExternalUrl("http://example.com/api?q=1"))

        // Malformed / dangerous URLs must be rejected
        assertFalse(InlineMarkdownParser.isValidExternalUrl("javascript:alert(1)"))
        assertFalse(InlineMarkdownParser.isValidExternalUrl("file:///android_asset/config.json"))
        assertFalse(InlineMarkdownParser.isValidExternalUrl("data:text/html,<b>pwned</b>"))
        assertFalse(InlineMarkdownParser.isValidExternalUrl("content://media/external/images/media/1"))
        assertFalse(InlineMarkdownParser.isValidExternalUrl(""))
        assertFalse(InlineMarkdownParser.isValidExternalUrl("htp:/broken"))

        val linkMarkdown = "Visit the [Android Documentation](https://developer.android.com) now."
        val annotated = InlineMarkdownParser.parse(linkMarkdown)
        assertTrue(annotated.text.contains("Visit the Android Documentation now."))
    }

    // 11. MALFORMED MARKDOWN HANDLING
    @Test
    fun testMalformedMarkdownGracefulDegradation() {
        // Empty content
        val emptyBlocks = MarkdownCodeBlockParser.parse("")
        assertTrue(emptyBlocks.isEmpty())

        // Multiple unclosed fences
        val malformedFences = """
            ```kotlin
            fun test() {
            ```
            ```
            unclosed block
        """.trimIndent()
        val blocks = MarkdownCodeBlockParser.parse(malformedFences)
        assertTrue(blocks.isNotEmpty())

        // Mismatched brackets
        val mismatchedBracket = "Text with [broken link without parens"
        val annotated = InlineMarkdownParser.parse(mismatchedBracket)
        assertEquals("Text with [broken link without parens", annotated.text)
    }

    // 12. BENGALI + MARKDOWN
    @Test
    fun testBengaliMarkdownAndCodeMixed() {
        val bengaliResponse = """
            ### পাইথন কোড উদাহরণ (Python Code Example)

            নিচে ফিবোনাচ্চি সংখ্যার একটি সহজ সমাধান দেওয়া হলো:
            ```python
            def fibonacci(n):
                if n <= 0:
                    return 0
                elif n == 1:
                    return 1
                return fibonacci(n - 1) + fibonacci(n - 2)
            ```
            - কোডটি রিকার্সিভ পদ্ধতিতে লেখা হয়েছে।
            - বড় সংখ্যার জন্য মেমোইজেশন ব্যবহার করা ভালো।
        """.trimIndent()

        val parsedBlocks = MarkdownCodeBlockParser.parse(bengaliResponse)
        assertEquals(3, parsedBlocks.size)

        assertTrue(parsedBlocks[0] is MarkdownContentBlock.Text)
        assertTrue(parsedBlocks[1] is MarkdownContentBlock.Code)
        assertTrue(parsedBlocks[2] is MarkdownContentBlock.Text)

        val code = parsedBlocks[1] as MarkdownContentBlock.Code
        assertEquals("Python", code.displayLanguage)
        assertTrue(code.code.contains("def fibonacci"))

        val bulletsBlock = MarkdownBlockParser.parseTextToBlocks((parsedBlocks[2] as MarkdownContentBlock.Text).content)
        val list = bulletsBlock.filterIsInstance<MarkdownBlock.ListBlock>().firstOrNull()
        assertNotNull(list)
        assertEquals(2, list?.items?.size)
        assertTrue(list?.items?.get(0)?.text?.contains("রিকার্সিভ") == true)
    }

    // 13. CLEAN COPY RESPONSE BEHAVIOR
    @Test
    fun testCleanCopyResponseBehavior() {
        val rawStreamOutput = """
            data: {"text":"Hello"}
            data: Here is your answer:
            ```kotlin
            val ok = true
            ```
            data: [DONE]
        """.trimIndent()

        val clean = MessageClipboardHelper.sanitizeForCopy(rawStreamOutput)
        assertFalse(clean.contains("[DONE]"))
        assertTrue(clean.contains("Here is your answer:"))
        assertTrue(clean.contains("val ok = true"))
    }

    // 14. TABLES & HORIZONTAL RULES
    @Test
    fun testTableAndHorizontalRuleParsing() {
        val markdown = """
            ---
            | Feature | Status | Notes |
            | :--- | :---: | ---: |
            | Markdown | Supported | Full support |
            | Code Blocks | Supported | With copy button |
            ***
        """.trimIndent()

        val blocks = MarkdownBlockParser.parseTextToBlocks(markdown)
        val rules = blocks.filterIsInstance<MarkdownBlock.HorizontalRule>()
        val tables = blocks.filterIsInstance<MarkdownBlock.Table>()

        assertEquals(2, rules.size)
        assertEquals(1, tables.size)

        val table = tables[0]
        assertEquals(listOf("Feature", "Status", "Notes"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("Markdown", table.rows[0][0])
        assertEquals("Code Blocks", table.rows[1][0])
    }
}
