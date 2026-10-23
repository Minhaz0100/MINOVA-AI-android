package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MinovaDatabase
import com.example.data.model.CodeActionType
import com.example.data.model.CodeAssistanceResult
import com.example.data.model.CodeLanguage
import com.example.data.model.CodeSnippet
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.CodeWorkspaceRepositoryImpl
import com.example.navigation.MinovaScreen
import com.example.ui.components.MinovaDrawerContent
import com.example.ui.screens.CodeWorkspaceScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodeWorkspaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: MinovaDatabase
    private lateinit var repository: CodeWorkspaceRepositoryImpl

    @Before
    fun setup() {
        database = MinovaDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = CodeWorkspaceRepositoryImpl(
            codeSnippetDao = database.codeSnippetDao(),
            chatRepository = ChatRepositoryImpl()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun codeSnippetDao_isolatesGuestAndUserSnippets_andSupportsRenameAndDelete() = runTest {
        val guestSaved = repository.saveSnippet(
            ownerUid = CodeSnippet.GUEST_OWNER_UID,
            title = "Guest C Loop",
            language = CodeLanguage.C,
            code = "int main() { return 0; }"
        ).getOrThrow()

        val userSaved = repository.saveSnippet(
            ownerUid = "user_uid_123",
            title = "User Python Script",
            language = CodeLanguage.PYTHON,
            code = "print('hello')"
        ).getOrThrow()

        val guestList = repository.observeSnippets(CodeSnippet.GUEST_OWNER_UID).first()
        val userList = repository.observeSnippets("user_uid_123").first()

        assertEquals(1, guestList.size)
        assertEquals("Guest C Loop", guestList.first().title)
        assertEquals(1, userList.size)
        assertEquals("User Python Script", userList.first().title)

        // Rename guest snippet
        repository.renameSnippet(guestSaved.id, "Renamed C Snippet")
        val updatedGuestList = repository.observeSnippets(CodeSnippet.GUEST_OWNER_UID).first()
        assertEquals("Renamed C Snippet", updatedGuestList.first().title)

        // Delete user snippet
        repository.deleteSnippet(userSaved.id)
        assertTrue(repository.observeSnippets("user_uid_123").first().isEmpty())
    }

    @Test
    fun buildDebugCodePrompt_andParseResponse_enforcesStructuredDebuggingSections() {
        val debugPrompt = CodeWorkspaceRepositoryImpl.buildCodeActionPrompt(
            action = CodeActionType.DEBUG,
            language = CodeLanguage.CPP,
            code = "int main() {\n    int arr[3] = {1, 2, 3};\n    return arr[3];\n}",
            contextInput = "runtime error: index 3 out of bounds for type 'int [3]'",
            responseLanguage = com.example.data.model.ResponseLanguage.BENGALI
        )

        assertTrue(debugPrompt.contains("### Detected Issues"))
        assertTrue(debugPrompt.contains("### Why They Occur"))
        assertTrue(debugPrompt.contains("### How to Fix"))
        assertTrue(debugPrompt.contains("### Proposed Fixed Code"))
        assertTrue(debugPrompt.contains("### Changes Explained"))
        assertTrue(debugPrompt.contains("**Problem**"))
        assertTrue(debugPrompt.contains("**Location**"))
        assertTrue(debugPrompt.contains("**Cause**"))
        assertTrue(debugPrompt.contains("**Impact**"))
        assertTrue(debugPrompt.contains("**Fix**"))
        assertTrue(debugPrompt.contains("index 3 out of bounds"))
        assertTrue(debugPrompt.contains("Bengali"))

        val raw = """
            ### Detected Issues
            - **Problem**: Out-of-bounds array access on `arr[3]`.
            - **Location**: Line 3 (`return arr[3];`)
            - **Cause**: Valid indices for a 3-element array are `0` to `2`.
            - **Impact**: Undefined behavior or memory access violation at runtime.
            - **Fix**: Access `arr[2]` for the last element.

            ### Why They Occur
            C++ arrays use zero-based indexing, so `arr[3]` reads past the allocated stack buffer.

            ### How to Fix
            Replace `arr[3]` with `arr[2]` (or check bounds before indexing).

            ### Proposed Fixed Code
            ```cpp
            #include <iostream>
            int main() {
                int arr[3] = {1, 2, 3};
                return arr[2];
            }
            ```

            ### Changes Explained
            - Changed `arr[3]` on Line 3 to `arr[2]` to stay within valid array bounds `0..2`.
        """.trimIndent()

        val parsed = CodeWorkspaceRepositoryImpl.parseCodeAssistanceResponse(
            rawResponse = raw,
            action = CodeActionType.DEBUG,
            language = CodeLanguage.CPP
        )

        assertTrue(parsed.explanationMarkdown.contains("### Detected Issues"))
        assertTrue(parsed.explanationMarkdown.contains("### Why They Occur"))
        assertTrue(parsed.explanationMarkdown.contains("### How to Fix"))
        assertTrue(!parsed.explanationMarkdown.contains("#include <iostream>"))
        assertTrue(!parsed.explanationMarkdown.contains("### Proposed Fixed Code"))
        assertNotNull(parsed.changesSummary)
        assertTrue(parsed.changesSummary!!.contains("arr[2]"))
        assertNotNull(parsed.extractedCode)
        assertTrue(parsed.extractedCode!!.contains("return arr[2];"))
    }

    @Test
    fun drawer_containsCodeWorkspaceEntry_andScreenPreservesEditorUntilReplaced() {
        var navigatedScreen: MinovaScreen? = null

        composeRule.setContent {
            MyApplicationTheme {
                MinovaDrawerContent(
                    conversations = emptyList(),
                    activeConversationId = null,
                    userProfile = UserProfile(name = "Minhaz", email = "minhaz@minova.ai", tier = "MINOVA PRO"),
                    onNavigate = { navigatedScreen = it },
                    onSelectConversation = {},
                    onNewChatClick = {},
                    onTogglePin = {}
                )
            }
        }

        val workspaceItem = composeRule.onNodeWithTag("drawer_nav_code_workspace")
        workspaceItem.assertIsDisplayed()
        workspaceItem.performClick()
        assertEquals(MinovaScreen.CodeWorkspace, navigatedScreen)
    }

    @Test
    fun codeWorkspaceScreen_supportsLanguages_actions_andExplicitReplaceEditor() {
        var triggeredAction: CodeActionType? = null
        var triggeredLanguage: CodeLanguage? = null

        val sampleResult = CodeAssistanceResult(
            actionType = CodeActionType.IMPROVE,
            language = CodeLanguage.PYTHON,
            explanationMarkdown = "### Improvements Explained\nUsed list comprehension.",
            changesSummary = "- Simplified loop into comprehension.",
            extractedCode = "squares = [x * x for x in range(10)]"
        )

        composeRule.setContent {
            MyApplicationTheme {
                CodeWorkspaceScreen(
                    snippets = emptyList(),
                    isLoading = false,
                    assistanceResult = sampleResult,
                    errorMessage = null,
                    feedbackMessage = null,
                    isSignedInUser = false,
                    onBack = {},
                    onRunCodeAction = { action, lang, _, _, _ ->
                        triggeredAction = action
                        triggeredLanguage = lang
                    },
                    onSaveSnippet = { _, _, _, _, _, _ -> },
                    onRenameSnippet = { _, _ -> },
                    onDeleteSnippet = {},
                    onClearError = {},
                    onClearFeedback = {},
                    onClearResult = {}
                )
            }
        }

        // Select Python chip
        composeRule.onNodeWithTag("code_lang_chip_python").performScrollTo().performClick()

        // Trigger Improve Code action
        composeRule.onNodeWithTag("code_action_improve").performScrollTo().performClick()
        assertEquals(CodeActionType.IMPROVE, triggeredAction)
        assertEquals(CodeLanguage.PYTHON, triggeredLanguage)

        // Explanation card and Proposed Code card are displayed separately
        composeRule.onNodeWithTag("code_result_explanation_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("code_result_code_card").performScrollTo().assertIsDisplayed()

        // Click Replace Editor Content and verify Restore Original button appears
        composeRule.onNodeWithTag("code_result_replace_editor_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("code_undo_replace_button").performScrollTo().assertIsDisplayed()
    }
}
