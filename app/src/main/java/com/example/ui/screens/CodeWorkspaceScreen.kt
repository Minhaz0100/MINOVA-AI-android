package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CodeActionType
import com.example.data.model.CodeAssistanceResult
import com.example.data.model.CodeLanguage
import com.example.data.model.CodeSnippet
import com.example.data.model.ResponseLanguage
import com.example.ui.components.RichMarkdownView
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeWorkspaceScreen(
    snippets: List<CodeSnippet>,
    isLoading: Boolean,
    assistanceResult: CodeAssistanceResult?,
    errorMessage: String?,
    feedbackMessage: String?,
    isSignedInUser: Boolean,
    onBack: () -> Unit,
    onRunCodeAction: (
        action: CodeActionType,
        language: CodeLanguage,
        code: String,
        contextInput: String,
        responseLanguage: ResponseLanguage
    ) -> Unit,
    onSaveSnippet: (
        title: String,
        language: CodeLanguage,
        code: String,
        description: String,
        existingId: String?,
        onSaved: (CodeSnippet) -> Unit
    ) -> Unit,
    onRenameSnippet: (snippetId: String, newTitle: String) -> Unit,
    onDeleteSnippet: (snippetId: String) -> Unit,
    onClearError: () -> Unit,
    onClearFeedback: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    var selectedLanguage by remember { mutableStateOf(CodeLanguage.KOTLIN) }
    var responseLanguage by remember { mutableStateOf(ResponseLanguage.ENGLISH) }
    var editorCode by remember { mutableStateOf(CodeLanguage.KOTLIN.sampleSnippet) }
    var contextInput by remember { mutableStateOf("") }
    var previousEditorBackup by remember { mutableStateOf<String?>(null) }
    var activeSnippet by remember { mutableStateOf<CodeSnippet?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitleInput by remember { mutableStateOf("") }
    var saveDescriptionInput by remember { mutableStateOf("") }

    var showSnippetsDialog by remember { mutableStateOf(false) }
    var snippetToRename by remember { mutableStateOf<CodeSnippet?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }
    var copiedResultCode by remember { mutableStateOf(false) }

    val lineCount = remember(editorCode) {
        if (editorCode.isEmpty()) 1 else editorCode.count { it == '\n' } + 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("code_workspace_screen")
    ) {
        // Top App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("code_workspace_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Chat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Code Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MinovaCyanPrimary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = selectedLanguage.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MinovaCyanPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (activeSnippet != null) {
                            "Editing: ${activeSnippet!!.title} • Local Snippet"
                        } else {
                            "Explain, Debug, Improve, Review & Generate Code"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Save Snippet Action
                IconButton(
                    onClick = {
                        saveTitleInput = activeSnippet?.title
                            ?: "${selectedLanguage.displayName} Snippet"
                        saveDescriptionInput = activeSnippet?.description ?: ""
                        showSaveDialog = true
                    },
                    modifier = Modifier.testTag("code_workspace_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save Code Snippet",
                        tint = MinovaCyanPrimary
                    )
                }

                // Open Saved Snippets List
                OutlinedButton(
                    onClick = { showSnippetsDialog = true },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("code_workspace_snippets_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MinovaCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Snippets (${snippets.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Error Banner
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_workspace_error_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Feedback Banner
            if (!feedbackMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_workspace_feedback_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MinovaCyanPrimary.copy(alpha = 0.16f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearFeedback,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss feedback",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 1. Language Selector & Explanation Language Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Programming Language",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Explanation language toggle (English / Bengali)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                responseLanguage = if (responseLanguage == ResponseLanguage.ENGLISH) {
                                    ResponseLanguage.BENGALI
                                } else {
                                    ResponseLanguage.ENGLISH
                                }
                            }
                            .testTag("code_response_lang_toggle"),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Explain in: ${responseLanguage.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CodeLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val wasViewingDefaultSample =
                                    CodeLanguage.entries.any { it.sampleSnippet == editorCode.trim() }
                                selectedLanguage = lang
                                if (wasViewingDefaultSample || editorCode.isBlank()) {
                                    editorCode = lang.sampleSnippet
                                }
                            },
                            label = {
                                Text(
                                    text = "${lang.displayName} (${lang.fileExtension})",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinovaCyanPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = MinovaCyanPrimary
                            ),
                            modifier = Modifier.testTag("code_lang_chip_${lang.id}")
                        )
                    }
                }
            }

            // 2. Code Editor Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0B1220)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    // Editor Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111C30))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeSnippet?.let { "${it.title}${selectedLanguage.fileExtension}" }
                                    ?: "workspace${selectedLanguage.fileExtension}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (previousEditorBackup != null) {
                                TextButton(
                                    onClick = {
                                        editorCode = previousEditorBackup!!
                                        previousEditorBackup = null
                                    },
                                    modifier = Modifier.testTag("code_undo_replace_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MinovaAzureLight
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Restore Original",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MinovaAzureLight
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    editorCode = selectedLanguage.sampleSnippet
                                },
                                modifier = Modifier.testTag("code_insert_sample_button")
                            ) {
                                Text(
                                    text = "Sample",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinovaCyanPrimary
                                )
                            }

                            TextButton(
                                onClick = {
                                    editorCode = ""
                                    activeSnippet = null
                                },
                                modifier = Modifier.testTag("code_clear_editor_button")
                            ) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Editable Code Input Area
                    OutlinedTextField(
                        value = editorCode,
                        onValueChange = { editorCode = it },
                        placeholder = {
                            Text(
                                text = "Write or paste your ${selectedLanguage.displayName} code here...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 190.dp, max = 360.dp)
                            .testTag("code_editor_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF0B1220),
                            unfocusedContainerColor = Color(0xFF0B1220)
                        )
                    )

                    // Editor Footer Status Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111C30))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$lineCount lines • ${editorCode.length} chars",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isSignedInUser) "Account Local Workspace" else "Guest Local Workspace",
                            style = MaterialTheme.typography.labelSmall,
                            color = MinovaAzureLight
                        )
                    }
                }
            }

            // 3. Optional Error Log / Generation Prompt Input
            OutlinedTextField(
                value = contextInput,
                onValueChange = { contextInput = it },
                label = {
                    Text("Compiler/Runtime Error Log or Code Generation Prompt")
                },
                placeholder = {
                    Text("Paste compiler/stacktrace errors for Debug, or describe what to build for Generate Code...")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("code_context_input"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinovaCyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            // 4. AI Code Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AI Code Actions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CodeActionType.entries.forEach { action ->
                        val icon = when (action) {
                            CodeActionType.EXPLAIN -> Icons.Default.Lightbulb
                            CodeActionType.DEBUG -> Icons.Default.BugReport
                            CodeActionType.IMPROVE -> Icons.Default.AutoAwesome
                            CodeActionType.REVIEW -> Icons.Default.RateReview
                            CodeActionType.GENERATE -> Icons.Default.EditNote
                        }
                        val isPrimary = action == CodeActionType.GENERATE || action == CodeActionType.DEBUG

                        Button(
                            onClick = {
                                copiedResultCode = false
                                onRunCodeAction(
                                    action,
                                    selectedLanguage,
                                    editorCode,
                                    contextInput,
                                    responseLanguage
                                )
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPrimary) {
                                    MinovaCyanPrimary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (isPrimary) {
                                    Color(0xFF061320)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("code_action_${action.id}")
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 5. Loading / Empty / Result Section
            if (isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_workspace_loading_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MinovaCyanPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Analyzing ${selectedLanguage.displayName} code with MINOVA AI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (assistanceResult != null) {
                // Static Analysis Honesty Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MinovaIndigoSecondary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MinovaAzureLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Static AI analysis & code generation (${assistanceResult.actionType.title}) — code is not executed on a live server.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearResult) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Explanation Section (Displayed separately from code)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_result_explanation_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (assistanceResult.actionType == CodeActionType.DEBUG) {
                                    Icons.Default.BugReport
                                } else {
                                    Icons.Default.Lightbulb
                                },
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (assistanceResult.actionType == CodeActionType.DEBUG) {
                                    "Debugging Analysis (Detected Issues, Causes & Fixes)"
                                } else {
                                    "AI Explanation & Analysis"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        RichMarkdownView(
                            content = assistanceResult.explanationMarkdown,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Changes Summary Card (if present)
                if (!assistanceResult.changesSummary.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_result_changes_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (assistanceResult.actionType == CodeActionType.DEBUG) {
                                    "Changes Explained"
                                } else {
                                    "Changes Between Original & Revised Code"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MinovaCyanPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RichMarkdownView(
                                content = assistanceResult.changesSummary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Generated / Revised Code Block Card (Displayed separately with Copy & Replace actions)
                if (!assistanceResult.extractedCode.isNullOrBlank()) {
                    val resultCode = assistanceResult.extractedCode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_result_code_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0B1220)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MinovaCyanPrimary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            // Header with Copy Code and Replace Editor Content actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111C30))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (assistanceResult.actionType == CodeActionType.DEBUG) {
                                        "Proposed Fixed Code (${assistanceResult.language.displayName})"
                                    } else {
                                        "Proposed ${assistanceResult.language.displayName} Code"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaCyanPrimary
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(resultCode))
                                            copiedResultCode = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("code_result_copy_button")
                                    ) {
                                        Icon(
                                            imageVector = if (copiedResultCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (copiedResultCode) "Copied" else "Copy Code",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            previousEditorBackup = editorCode
                                            editorCode = resultCode
                                            selectedLanguage = assistanceResult.language
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MinovaCyanPrimary,
                                            contentColor = Color(0xFF061320)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("code_result_replace_editor_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Replace Editor",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Readable Monospace Code View
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = resultCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.testTag("code_result_extracted_text")
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state guidance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_workspace_empty_state"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ready for AI Code Assistance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select a language above, edit or paste your code, and choose Explain, Debug, Improve, Review, or Generate Code. Your editor code is preserved until you explicitly choose 'Replace Editor'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Save Snippet Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = if (activeSnippet != null) "Save Snippet" else "Save New Snippet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Snippets are saved locally on this device (${if (isSignedInUser) "isolated to your signed-in account" else "isolated to Guest mode"}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = saveTitleInput,
                        onValueChange = { saveTitleInput = it },
                        label = { Text("Snippet Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_snippet_title_input")
                    )
                    OutlinedTextField(
                        value = saveDescriptionInput,
                        onValueChange = { saveDescriptionInput = it },
                        label = { Text("Notes / Description (optional)") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_snippet_description_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSnippet(
                            saveTitleInput,
                            selectedLanguage,
                            editorCode,
                            saveDescriptionInput,
                            activeSnippet?.id
                        ) { saved ->
                            activeSnippet = saved
                            showSaveDialog = false
                        }
                    },
                    enabled = saveTitleInput.isNotBlank() && editorCode.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_snippet_button")
                ) {
                    Text(if (activeSnippet != null) "Update" else "Save")
                }
            },
            dismissButton = {
                if (activeSnippet != null) {
                    OutlinedButton(
                        onClick = {
                            onSaveSnippet(
                                saveTitleInput,
                                selectedLanguage,
                                editorCode,
                                saveDescriptionInput,
                                null
                            ) { saved ->
                                activeSnippet = saved
                                showSaveDialog = false
                            }
                        }
                    ) {
                        Text("Save as New")
                    }
                }
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Saved Snippets List Dialog
    if (showSnippetsDialog) {
        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showSnippetsDialog = false },
            title = {
                Column {
                    Text(
                        text = "Saved Code Snippets (${snippets.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSignedInUser) {
                            "Stored locally on device for your signed-in account"
                        } else {
                            "Stored locally on device for Guest workspace"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MinovaCyanPrimary
                    )
                }
            },
            text = {
                if (snippets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved snippets yet. Use the bookmark icon in the top bar to save code snippets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        snippets.forEach { snippet ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("snippet_item_${snippet.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = snippet.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${snippet.language.displayName} • ${dateFormat.format(Date(snippet.updatedAt))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MinovaCyanPrimary
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    snippetToRename = snippet
                                                    renameTitleInput = snippet.title
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("rename_snippet_${snippet.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                                    contentDescription = "Rename Snippet",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (activeSnippet?.id == snippet.id) {
                                                        activeSnippet = null
                                                    }
                                                    onDeleteSnippet(snippet.id)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("delete_snippet_${snippet.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Snippet",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (snippet.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = snippet.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            previousEditorBackup = editorCode
                                            selectedLanguage = snippet.language
                                            editorCode = snippet.code
                                            activeSnippet = snippet
                                            showSnippetsDialog = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("reopen_snippet_${snippet.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MinovaCyanPrimary,
                                            contentColor = Color(0xFF061320)
                                        )
                                    ) {
                                        Text("Open in Editor", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnippetsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Rename Snippet Dialog
    if (snippetToRename != null) {
        val target = snippetToRename!!
        AlertDialog(
            onDismissRequest = { snippetToRename = null },
            title = {
                Text(
                    text = "Rename Snippet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_snippet_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSnippet(target.id, renameTitleInput)
                        if (activeSnippet?.id == target.id) {
                            activeSnippet = activeSnippet?.copy(title = renameTitleInput.trim())
                        }
                        snippetToRename = null
                    },
                    enabled = renameTitleInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_rename_snippet_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { snippetToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
