package com.example.data.repository

import com.example.data.local.dao.CodeSnippetDao
import com.example.data.local.entity.CodeSnippetEntity
import com.example.data.model.CodeActionType
import com.example.data.model.CodeAssistanceResult
import com.example.data.model.CodeLanguage
import com.example.data.model.CodeSnippet
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

interface CodeWorkspaceRepository {
    fun observeSnippets(ownerUid: String): Flow<List<CodeSnippet>>

    suspend fun saveSnippet(
        ownerUid: String,
        title: String,
        language: CodeLanguage,
        code: String,
        description: String = "",
        existingId: String? = null
    ): Result<CodeSnippet>

    suspend fun renameSnippet(
        snippetId: String,
        newTitle: String
    ): Result<Unit>

    suspend fun deleteSnippet(snippetId: String)

    suspend fun performCodeAction(
        action: CodeActionType,
        language: CodeLanguage,
        code: String,
        contextInput: String = "",
        responseLanguage: ResponseLanguage = ResponseLanguage.ENGLISH
    ): NetworkResult<CodeAssistanceResult>
}

class CodeWorkspaceRepositoryImpl(
    private val codeSnippetDao: CodeSnippetDao,
    private val chatRepository: ChatRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CodeWorkspaceRepository {

    override fun observeSnippets(ownerUid: String): Flow<List<CodeSnippet>> {
        val effectiveOwner = ownerUid.ifBlank { CodeSnippet.GUEST_OWNER_UID }
        return codeSnippetDao.observeSnippetsForOwner(effectiveOwner).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveSnippet(
        ownerUid: String,
        title: String,
        language: CodeLanguage,
        code: String,
        description: String,
        existingId: String?
    ): Result<CodeSnippet> = withContext(ioDispatcher) {
        val cleanTitle = title.trim()
        val cleanCode = code.trimEnd()
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Snippet title cannot be empty."))
        }
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Cannot save an empty code snippet."))
        }

        val effectiveOwner = ownerUid.ifBlank { CodeSnippet.GUEST_OWNER_UID }
        val now = System.currentTimeMillis()
        val existing = existingId?.let { codeSnippetDao.getSnippetById(it) }

        val snippet = CodeSnippet(
            id = existing?.id ?: existingId ?: "snippet_${UUID.randomUUID()}",
            ownerUid = effectiveOwner,
            title = cleanTitle,
            language = language,
            code = cleanCode,
            description = description.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        codeSnippetDao.upsertSnippet(CodeSnippetEntity.fromDomain(snippet))
        Result.success(snippet)
    }

    override suspend fun renameSnippet(
        snippetId: String,
        newTitle: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Snippet title cannot be empty."))
        }
        codeSnippetDao.renameSnippet(
            id = snippetId,
            newTitle = cleanTitle,
            updatedAt = System.currentTimeMillis()
        )
        Result.success(Unit)
    }

    override suspend fun deleteSnippet(snippetId: String) = withContext(ioDispatcher) {
        codeSnippetDao.deleteSnippetById(snippetId)
    }

    override suspend fun performCodeAction(
        action: CodeActionType,
        language: CodeLanguage,
        code: String,
        contextInput: String,
        responseLanguage: ResponseLanguage
    ): NetworkResult<CodeAssistanceResult> = withContext(ioDispatcher) {
        val cleanCode = code.trim()
        val cleanContext = contextInput.trim()

        if (action.requiresCodeInEditor && cleanCode.isBlank()) {
            return@withContext NetworkResult.Error(
                userMessage = "Please enter or paste ${language.displayName} code in the editor before running '${action.title}'.",
                errorCode = "ERR_EMPTY_CODE"
            )
        }

        if (action == CodeActionType.GENERATE && cleanContext.isBlank() && cleanCode.isBlank()) {
            return@withContext NetworkResult.Error(
                userMessage = "Please describe what you want to generate in the prompt box.",
                errorCode = "ERR_EMPTY_PROMPT"
            )
        }

        val prompt = buildCodeActionPrompt(
            action = action,
            language = language,
            code = cleanCode,
            contextInput = cleanContext,
            responseLanguage = responseLanguage
        )

        val responseStyleForAction = if (action == CodeActionType.DEBUG) {
            ResponseStyle.DETAILED
        } else {
            ResponseStyle.PROFESSIONAL
        }
        val responseFormatForAction = if (action == CodeActionType.DEBUG) {
            ResponseFormat.AUTO
        } else {
            ResponseFormat.STEP_BY_STEP
        }

        val response = chatRepository.sendChatMessage(
            message = prompt,
            conversationHistory = emptyList(),
            responseStyle = responseStyleForAction,
            responseLanguage = responseLanguage,
            responseFormat = responseFormatForAction
        )

        when (response) {
            is NetworkResult.Success -> {
                val parsed = parseCodeAssistanceResponse(
                    rawResponse = response.data,
                    action = action,
                    language = language
                )
                NetworkResult.Success(parsed)
            }
            is NetworkResult.Error -> response
        }
    }

    companion object {
        fun buildDebugCodePrompt(
            language: CodeLanguage,
            code: String,
            contextInput: String,
            responseLanguage: ResponseLanguage
        ): String {
            val langInstruction = when (responseLanguage) {
                ResponseLanguage.BENGALI -> "Provide all natural-language explanations and analysis in Bengali (বাংলা), while keeping code identifiers, syntax, and section headings accurate."
                else -> "Provide all natural-language explanations and analysis in clear, thorough English."
            }

            val numberedCode = code.lines().mapIndexed { index, line ->
                "${index + 1}: $line"
            }.joinToString("\n")

            val errorLogSection = if (contextInput.isNotBlank()) {
                """
                User-Supplied Compiler / Runtime Error Logs or Symptom Context:
                ```text
                $contextInput
                ```
                Analyze these compiler/runtime error logs together with the submitted ${language.displayName} code to pinpoint the exact failure.
                """.trimIndent()
            } else {
                "No external compiler/runtime log was supplied. Carefully inspect the submitted ${language.displayName} code for syntax errors, logical bugs, off-by-one errors, null/uninitialized memory hazards, type mismatches, resource leaks, and edge-case failures."
            }

            return """
                You are MINOVA AI Code Debugger, a senior software debugging specialist.
                Task: Perform a rigorous static debugging analysis of the submitted ${language.displayName} code.
                $langInstruction

                STRICT DEBUGGING RULES:
                1. Your primary task is DEBUGGING ANALYSIS—identifying actual programming errors, logical bugs, runtime risks, and relevant edge cases.
                2. Do NOT treat ordinary line-by-line code explanation, generic code generation, or simply predicting/printing the program's output as your primary task.
                3. Never claim to have compiled, executed, or tested the code in a live runtime environment. Perform static debugging analysis only.
                4. Do NOT fabricate line numbers, compiler errors, stack traces, test results, or imaginary bugs. Reference only the actual line numbers and code sections present in the numbered reference below.
                5. If the code has NO identifiable bug, explicitly state in "### Detected Issues" that no definite bug was identified, and mention any remaining runtime risks or edge cases without inventing errors. Still provide the verified code under "### Proposed Fixed Code" and note in "### Changes Explained" whether any defensive refinement was made or if no code changes were required.

                $errorLogSection

                You MUST structure your response using these exact 5 markdown headings in this order:

                ### Detected Issues
                List every detected bug, error, or runtime risk separately. For EACH issue, explicitly include:
                - **Problem**: What is wrong?
                - **Location**: Identify the exact line number(s) (using the numbered reference) and code section.
                - **Cause**: Why does the issue happen?
                - **Impact**: What incorrect behavior, crash, or compiler/runtime error can it cause?
                - **Fix**: How should it be corrected?
                (If no definite bug exists, explicitly state that no definite bug was identified and note any edge cases or risks without inventing errors.)

                ### Why They Occur
                Provide a clear technical explanation of the underlying root cause(s) and execution/data-flow mechanics behind the detected issue(s) (or explain why the current logic is sound if no definite bug was found).

                ### How to Fix
                Summarize the concrete corrective steps and defensive practices required to resolve each issue safely.

                ### Proposed Fixed Code
                Provide the complete, corrected ${language.displayName} code inside a single fenced code block (` ```${language.markdownTag} ... ``` `) with NO line-number prefixes inside the code block so it is ready to copy or replace in the editor.

                ### Changes Explained
                Provide clear bullet points explaining the important changes made in the Proposed Fixed Code compared to the original code.

                Numbered Reference (for accurate line location citations only):
                ```text
                $numberedCode
                ```

                Submitted Source Code (${language.displayName}):
                ```${language.markdownTag}
                $code
                ```
            """.trimIndent()
        }

        fun buildCodeActionPrompt(
            action: CodeActionType,
            language: CodeLanguage,
            code: String,
            contextInput: String,
            responseLanguage: ResponseLanguage
        ): String {
            if (action == CodeActionType.DEBUG) {
                return buildDebugCodePrompt(
                    language = language,
                    code = code,
                    contextInput = contextInput,
                    responseLanguage = responseLanguage
                )
            }

            val langInstruction = when (responseLanguage) {
                ResponseLanguage.BENGALI -> "Provide all natural-language explanations in Bengali (বাংলা), while keeping code identifiers and syntax in ${language.displayName}."
                else -> "Provide all natural-language explanations in clear, concise English."
            }

            val honestyConstraint =
                "CRITICAL RULE: Never claim that you compiled, executed, or ran this code in a live runtime environment. Perform static code analysis only."

            return when (action) {
                CodeActionType.EXPLAIN -> """
                    You are MINOVA AI Code Workspace Assistant.
                    Task: Explain the following ${language.displayName} code step by step.
                    $langInstruction
                    $honestyConstraint

                    Structure your response with:
                    ### Overview
                    A concise summary of what the code accomplishes.

                    ### Step-by-Step Walkthrough
                    Walk through the logic, functions, control flow, and key constructs step by step.

                    ### Complexity & Notes
                    Briefly note time/space complexity and any important behavior.
                    ${if (contextInput.isNotBlank()) "\nAdditional User Focus: $contextInput\n" else ""}
                    Source Code (${language.displayName}):
                    ```${language.markdownTag}
                    $code
                    ```
                """.trimIndent()

                CodeActionType.DEBUG -> buildDebugCodePrompt(
                    language = language,
                    code = code,
                    contextInput = contextInput,
                    responseLanguage = responseLanguage
                )

                CodeActionType.IMPROVE -> """
                    You are MINOVA AI Code Workspace Assistant.
                    Task: Refactor and improve the following ${language.displayName} code for readability, idiomatic best practices, and efficiency.
                    $langInstruction
                    $honestyConstraint
                    ${if (contextInput.isNotBlank()) "\nRefactoring Goal: $contextInput\n" else ""}

                    Structure your response in this exact order:
                    ### Improvements Explained
                    Explain how the code can be cleaner, safer, and more idiomatic.

                    ### Changes Summary
                    Bullet points highlighting what changed between the original and improved versions.

                    ### Improved Code
                    Provide the complete improved ${language.displayName} code inside a single fenced code block (` ```${language.markdownTag} ... ``` `).

                    Original Code (${language.displayName}):
                    ```${language.markdownTag}
                    $code
                    ```
                """.trimIndent()

                CodeActionType.REVIEW -> """
                    You are MINOVA AI Code Workspace Assistant.
                    Task: Perform a thorough code review of the following ${language.displayName} code.
                    $langInstruction
                    $honestyConstraint
                    ${if (contextInput.isNotBlank()) "\nReview Focus: $contextInput\n" else ""}

                    Structure your response in this exact order:
                    ### Readability & Maintainability
                    Assess naming, modularity, and clarity.

                    ### Edge Cases & Potential Bugs
                    Identify boundary conditions, error handling gaps, or subtle bugs.

                    ### Changes Summary
                    Summarize recommended fixes.

                    ### Recommended Refactored Code
                    Provide a clean, revised version of the ${language.displayName} code inside a single fenced code block (` ```${language.markdownTag} ... ``` `).

                    Source Code (${language.displayName}):
                    ```${language.markdownTag}
                    $code
                    ```
                """.trimIndent()

                CodeActionType.GENERATE -> """
                    You are MINOVA AI Code Workspace Assistant.
                    Task: Generate clean, idiomatic, production-ready ${language.displayName} code from the user's description.
                    $langInstruction
                    $honestyConstraint

                    User Description / Specification:
                    ${contextInput.ifBlank { code }}

                    ${if (contextInput.isNotBlank() && code.isNotBlank()) "Existing Editor Context (${language.displayName}):\n```${language.markdownTag}\n$code\n```" else ""}

                    Structure your response in this exact order:
                    ### Solution Approach
                    Explain how the generated solution works step by step and any assumptions or edge cases handled.

                    ### Generated Code
                    Provide the complete ${language.displayName} code inside a single fenced code block (` ```${language.markdownTag} ... ``` `).
                """.trimIndent()
            }
        }

        fun parseCodeAssistanceResponse(
            rawResponse: String,
            action: CodeActionType,
            language: CodeLanguage
        ): CodeAssistanceResult {
            val trimmed = rawResponse.trim()
            val codeSectionRegex = Regex(
                "###+\\s*(?:Proposed Fixed Code|Corrected Code|Improved Code|Recommended Refactored Code|Generated Code)\\s*\\n+```(?:[a-zA-Z0-9_+\\-#.]*)?\\s*\\n([\\s\\S]*?)```",
                RegexOption.IGNORE_CASE
            )
            val sectionCodeMatch = codeSectionRegex.find(trimmed)

            val codeBlockRegex = Regex("```(?:[a-zA-Z0-9_+\\-#.]*)?\\s*\\n([\\s\\S]*?)```")
            val matches = codeBlockRegex.findAll(trimmed).toList()

            val selectedCodeText: String?
            val explanationWithoutCode: String

            if (sectionCodeMatch != null) {
                selectedCodeText = sectionCodeMatch.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                explanationWithoutCode = trimmed.removeRange(sectionCodeMatch.range).trim()
            } else {
                val selectedMatch = if (action == CodeActionType.EXPLAIN && matches.size <= 1) {
                    null
                } else {
                    matches.maxByOrNull { it.groupValues[1].trim().length }
                }
                selectedCodeText = selectedMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                explanationWithoutCode = if (selectedMatch != null) {
                    trimmed.removeRange(selectedMatch.range)
                        .replace(
                            Regex(
                                "###+\\s*(?:Proposed Fixed Code|Corrected Code|Improved Code|Recommended Refactored Code|Generated Code)\\s*$",
                                RegexOption.IGNORE_CASE
                            ),
                            ""
                        )
                        .trim()
                } else {
                    trimmed
                }
            }

            // Extract Changes Explained or Changes Summary section if present
            val changesRegex = Regex(
                "###+\\s*(?:Changes Explained|Changes Summary)\\s*\\n([\\s\\S]*?)(?=\\n###+\\s|$)",
                RegexOption.IGNORE_CASE
            )
            val changesMatch = changesRegex.find(explanationWithoutCode)
            val changesSummary = changesMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

            val finalExplanation = if (changesMatch != null) {
                explanationWithoutCode.removeRange(changesMatch.range).trim()
            } else {
                explanationWithoutCode
            }.replace(
                Regex(
                    "###+\\s*(?:Proposed Fixed Code|Corrected Code|Improved Code|Recommended Refactored Code|Generated Code)\\s*(?=\\n###+\\s|$)",
                    RegexOption.IGNORE_CASE
                ),
                ""
            ).trim().ifBlank {
                "Analysis complete for ${language.displayName} (${action.title})."
            }

            return CodeAssistanceResult(
                actionType = action,
                language = language,
                explanationMarkdown = finalExplanation,
                changesSummary = changesSummary,
                extractedCode = selectedCodeText
            )
        }
    }
}

/**
 * In-memory implementation for previews and unit tests.
 */
class InMemoryCodeWorkspaceRepository : CodeWorkspaceRepository {
    private val snippetsState = MutableStateFlow<List<CodeSnippet>>(emptyList())

    override fun observeSnippets(ownerUid: String): Flow<List<CodeSnippet>> {
        val effectiveOwner = ownerUid.ifBlank { CodeSnippet.GUEST_OWNER_UID }
        return snippetsState.map { list ->
            list.filter { it.ownerUid == effectiveOwner }.sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun saveSnippet(
        ownerUid: String,
        title: String,
        language: CodeLanguage,
        code: String,
        description: String,
        existingId: String?
    ): Result<CodeSnippet> {
        val cleanTitle = title.trim()
        val cleanCode = code.trimEnd()
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Snippet title cannot be empty."))
        }
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot save an empty code snippet."))
        }
        val effectiveOwner = ownerUid.ifBlank { CodeSnippet.GUEST_OWNER_UID }
        val now = System.currentTimeMillis()
        val existing = existingId?.let { id -> snippetsState.value.find { it.id == id } }
        val snippet = CodeSnippet(
            id = existing?.id ?: existingId ?: "snippet_${UUID.randomUUID()}",
            ownerUid = effectiveOwner,
            title = cleanTitle,
            language = language,
            code = cleanCode,
            description = description.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        snippetsState.update { list ->
            list.filterNot { it.id == snippet.id } + snippet
        }
        return Result.success(snippet)
    }

    override suspend fun renameSnippet(snippetId: String, newTitle: String): Result<Unit> {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Snippet title cannot be empty."))
        }
        snippetsState.update { list ->
            list.map {
                if (it.id == snippetId) it.copy(title = cleanTitle, updatedAt = System.currentTimeMillis())
                else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteSnippet(snippetId: String) {
        snippetsState.update { list -> list.filterNot { it.id == snippetId } }
    }

    override suspend fun performCodeAction(
        action: CodeActionType,
        language: CodeLanguage,
        code: String,
        contextInput: String,
        responseLanguage: ResponseLanguage
    ): NetworkResult<CodeAssistanceResult> {
        if (action.requiresCodeInEditor && code.isBlank()) {
            return NetworkResult.Error(
                userMessage = "Please enter code in the editor first.",
                errorCode = "ERR_EMPTY_CODE"
            )
        }
        if (action == CodeActionType.GENERATE && contextInput.isBlank() && code.isBlank()) {
            return NetworkResult.Error(
                userMessage = "Please enter a prompt to generate code.",
                errorCode = "ERR_EMPTY_PROMPT"
            )
        }
        if (action == CodeActionType.DEBUG) {
            return NetworkResult.Success(
                CodeAssistanceResult(
                    actionType = action,
                    language = language,
                    explanationMarkdown = """
                        ### Detected Issues
                        - **Problem**: Potential boundary or input validation risk in ${language.displayName} snippet.
                        - **Location**: Line 1
                        - **Cause**: Missing guard condition before processing input.
                        - **Impact**: May produce unexpected runtime behavior on empty or boundary inputs.
                        - **Fix**: Add explicit input and boundary validation.

                        ### Why They Occur
                        Static analysis indicates unguarded assumptions on input state.

                        ### How to Fix
                        Validate inputs and boundary conditions before executing core logic.
                    """.trimIndent(),
                    changesSummary = "- Added defensive boundary check while preserving original logic.",
                    extractedCode = "// Proposed Fixed ${language.displayName} code\n$code"
                )
            )
        }
        val generatedOrRevised = when (action) {
            CodeActionType.EXPLAIN -> null
            CodeActionType.GENERATE -> language.sampleSnippet
            else -> "// Revised ${language.displayName} code\n$code"
        }
        return NetworkResult.Success(
            CodeAssistanceResult(
                actionType = action,
                language = language,
                explanationMarkdown = "### ${action.title} (${language.displayName})\nStep-by-step static analysis completed.",
                changesSummary = if (generatedOrRevised != null && action != CodeActionType.GENERATE) {
                    "- Improved safety and structure while preserving behavior."
                } else null,
                extractedCode = generatedOrRevised
            )
        )
    }
}
