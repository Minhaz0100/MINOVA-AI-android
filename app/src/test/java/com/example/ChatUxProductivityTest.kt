package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class ChatUxProductivityTest {

    @Test
    fun testParsePlainTextMessage() {
        val message = "Hello! I am MINOVA AI. How may I assist your engineering goals today?"
        val blocks = MarkdownCodeBlockParser.parse(message)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownContentBlock.Text)
        assertEquals(message, (blocks[0] as MarkdownContentBlock.Text).content)
    }

    @Test
    fun testParseSingleCodeBlockWithLanguage() {
        val codeSnippet = """
            ```kotlin
            fun calculateFibonacci(n: Int): Int {
                return if (n <= 1) n else calculateFibonacci(n - 1) + calculateFibonacci(n - 2)
            }
            ```
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(codeSnippet)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownContentBlock.Code)

        val codeBlock = blocks[0] as MarkdownContentBlock.Code
        assertEquals("Kotlin", codeBlock.displayLanguage)
        assertTrue(codeBlock.isComplete)
        assertTrue(codeBlock.code.contains("calculateFibonacci"))
    }

    @Test
    fun testParseCodeBlockWithoutLanguage() {
        val codeSnippet = """
            ```
            SELECT id, title, created_at FROM conversations ORDER BY created_at DESC;
            ```
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(codeSnippet)
        assertEquals(1, blocks.size)
        val codeBlock = blocks[0] as MarkdownContentBlock.Code
        assertEquals("Code", codeBlock.displayLanguage)
        assertTrue(codeBlock.isComplete)
        assertTrue(codeBlock.code.contains("SELECT id"))
    }

    @Test
    fun testParseLanguageAliases() {
        assertEquals("Kotlin", MarkdownContentBlock.Code.formatLanguage("kt"))
        assertEquals("Python", MarkdownContentBlock.Code.formatLanguage("py"))
        assertEquals("JavaScript", MarkdownContentBlock.Code.formatLanguage("js"))
        assertEquals("TypeScript", MarkdownContentBlock.Code.formatLanguage("ts"))
        assertEquals("SQL", MarkdownContentBlock.Code.formatLanguage("sql"))
        assertEquals("JSON", MarkdownContentBlock.Code.formatLanguage("json"))
        assertEquals("Bash", MarkdownContentBlock.Code.formatLanguage("sh"))
        assertEquals("C++", MarkdownContentBlock.Code.formatLanguage("cpp"))
        assertEquals("C#", MarkdownContentBlock.Code.formatLanguage("cs"))
        assertEquals("Code", MarkdownContentBlock.Code.formatLanguage(null))
        assertEquals("Code", MarkdownContentBlock.Code.formatLanguage(""))
    }

    @Test
    fun testParseMultipleCodeBlocksWithInterleavedText() {
        val response = """
            Here is the Kotlin implementation:
            ```kotlin
            val message = "Hello"
            ```
            And here is the corresponding Python version:
            ```python
            message = "Hello"
            ```
            Let me know if you want unit tests!
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(response)
        assertEquals(5, blocks.size)

        assertTrue(blocks[0] is MarkdownContentBlock.Text)
        assertEquals("Here is the Kotlin implementation:", (blocks[0] as MarkdownContentBlock.Text).content)

        assertTrue(blocks[1] is MarkdownContentBlock.Code)
        val b1 = blocks[1] as MarkdownContentBlock.Code
        assertEquals("Kotlin", b1.displayLanguage)
        assertEquals("val message = \"Hello\"", b1.code.trim())

        assertTrue(blocks[2] is MarkdownContentBlock.Text)
        assertEquals("And here is the corresponding Python version:", (blocks[2] as MarkdownContentBlock.Text).content)

        assertTrue(blocks[3] is MarkdownContentBlock.Code)
        val b3 = blocks[3] as MarkdownContentBlock.Code
        assertEquals("Python", b3.displayLanguage)
        assertEquals("message = \"Hello\"", b3.code.trim())

        assertTrue(blocks[4] is MarkdownContentBlock.Text)
        assertEquals("Let me know if you want unit tests!", (blocks[4] as MarkdownContentBlock.Text).content)
    }

    @Test
    fun testParseStreamingIncompleteCodeBlock() {
        val partial = """
            Generating solution:
            ```kotlin
            class Service {
                fun run() {
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(partial)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownContentBlock.Text)

        val code = blocks[1] as MarkdownContentBlock.Code
        assertEquals("Kotlin", code.displayLanguage)
        assertFalse("Unclosed streaming block must have isComplete = false", code.isComplete)
        assertTrue(code.code.contains("class Service"))
    }

    @Test
    fun testParseTildeCodeFence() {
        val response = """
            ~~~python
            print("Tilde fence works")
            ~~~
        """.trimIndent()

        val blocks = MarkdownCodeBlockParser.parse(response)
        assertEquals(1, blocks.size)
        val code = blocks[0] as MarkdownContentBlock.Code
        assertEquals("Python", code.displayLanguage)
        assertEquals("print(\"Tilde fence works\")", code.code.trim())
    }

    @Test
    fun testSanitizeStripsSsePrefixesAndDone() {
        val rawStreamOutput = """
            data: Hello, I am MINOVA.
            data: Here is your code.
            event: message
            data: [DONE]
        """.trimIndent()

        val clean = MessageClipboardHelper.sanitizeForCopy(rawStreamOutput)
        assertFalse("Must not contain SSE data: prefix", clean.contains("data: "))
        assertFalse("Must not contain SSE [DONE] marker", clean.contains("[DONE]"))
        assertFalse("Must not contain event: marker", clean.contains("event: "))
        assertEquals("Hello, I am MINOVA.\nHere is your code.", clean)
    }

    @Test
    fun testSanitizePreservesCodeAndIndentations() {
        val responseWithCode = """
            Here is the algorithm:
            ```kotlin
            fun fib(n: Int): Int {
                if (n <= 1) return n
                return fib(n - 1) + fib(n - 2)
            }
            ```
        """.trimIndent()

        val sanitized = MessageClipboardHelper.sanitizeForCopy(responseWithCode)
        assertEquals(responseWithCode, sanitized)
    }

    @Test
    fun testSanitizeExtractsTextFromLeakedGeminiJson() {
        val rawJson = """{"candidates":[{"content":{"parts":[{"text":"Clean AI response extracted"}]}}]}"""
        val sanitized = MessageClipboardHelper.sanitizeForCopy(rawJson)
        assertEquals("Clean AI response extracted", sanitized)
    }

    @Test
    fun testCopyToClipboardSuccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textToCopy = "MINOVA AI High Quality Response"

        val success = MessageClipboardHelper.copyToClipboard(context, textToCopy, "MINOVA AI")
        assertTrue("Clipboard operation must succeed", success)

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboardManager.primaryClip
        assertNotNull(clip)
        assertEquals(1, clip?.itemCount)
        assertEquals(textToCopy, clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun testHasCodeBlocksAndExtractAllCode() {
        val markdownWithMultipleBlocks = """
            Here is step 1:
            ```kotlin
            val x = 10
            ```
            And here is step 2:
            ```python
            y = 20
            ```
        """.trimIndent()

        assertTrue(MarkdownCodeBlockParser.hasCodeBlocks(markdownWithMultipleBlocks))
        val extractedCode = MarkdownCodeBlockParser.extractAllCode(markdownWithMultipleBlocks)
        assertTrue(extractedCode.contains("val x = 10"))
        assertTrue(extractedCode.contains("y = 20"))

        val plainText = "This message has no code at all."
        assertFalse(MarkdownCodeBlockParser.hasCodeBlocks(plainText))
        assertEquals("", MarkdownCodeBlockParser.extractAllCode(plainText))
    }

    @Test
    fun testMessageIsPinnedDefaultAndMutation() {
        val msg = com.example.data.model.Message(
            id = "msg-123",
            conversationId = "conv-1",
            sender = com.example.data.model.MessageSender.USER,
            content = "Important requirement",
            timestamp = "10:30 AM"
        )
        assertFalse("Message should not be pinned by default", msg.isPinned)

        val pinnedMsg = msg.copy(isPinned = true)
        assertTrue("Message isPinned should be mutable via copy", pinnedMsg.isPinned)
    }
}
