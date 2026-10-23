package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PromptCategory
import com.example.data.model.PromptItem
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaError
import com.example.ui.theme.MinovaIndigoSecondary
import com.example.ui.theme.MinovaSuccess
import com.example.ui.theme.MinovaVioletAccent
import com.example.ui.theme.MinovaWarning

@Composable
fun PromptLibraryScreen(
    prompts: List<PromptItem>,
    selectedCategory: PromptCategory?,
    searchQuery: String,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onSelectCategory: (PromptCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onUsePrompt: (PromptItem) -> Unit,
    onSaveCustomPrompt: (
        title: String,
        description: String,
        promptText: String,
        category: PromptCategory,
        existingId: String?,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteCustomPrompt: (String) -> Unit,
    onDismissError: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditorDialog by rememberSaveable { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<PromptItem?>(null) }
    var promptPendingDelete by remember { mutableStateOf<PromptItem?>(null) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("prompt_library_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("prompt_library_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Chat",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Prompt Library",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Select a template to populate the chat composer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        editingPrompt = null
                        showEditorDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinovaCyanPrimary,
                        contentColor = Color(0xFF04131E)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_custom_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Prompt",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Error Banner
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                Surface(
                    color = MinovaError.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MinovaError.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("prompt_library_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MinovaError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search coding, study, English, or writing prompts…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search prompts",
                        tint = MinovaCyanPrimary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("prompt_search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    focusedBorderColor = MinovaCyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("prompt_search_input")
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = {
                        Text(
                            text = "All",
                            fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MinovaCyanPrimary.copy(alpha = 0.18f),
                        selectedLabelColor = MinovaCyanPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == null,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        selectedBorderColor = MinovaCyanPrimary
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("prompt_category_All")
                )

                PromptCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    val accent = categoryAccentColor(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectCategory(if (isSelected) null else category)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(category),
                                contentDescription = null,
                                tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = category.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            selectedBorderColor = accent
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("prompt_category_${category.name}")
                    )
                }
            }

            // Content Area (Loading / Empty / List)
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("prompt_library_loading"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MinovaCyanPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading Prompt Library…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                prompts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("prompt_library_empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MinovaCyanPrimary.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Matching Prompts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No prompts matched your current search or category filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (searchQuery.isNotEmpty() || selectedCategory != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                            onSelectCategory(null)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("prompt_reset_filters_button")
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                                Button(
                                    onClick = {
                                        editingPrompt = null
                                        showEditorDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MinovaCyanPrimary,
                                        contentColor = Color(0xFF04131E)
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Create Custom Prompt", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("prompt_library_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = prompts,
                            key = { it.id }
                        ) { prompt ->
                            PromptCardItem(
                                prompt = prompt,
                                onUsePrompt = { onUsePrompt(prompt) },
                                onEditCustomPrompt = if (prompt.isCustom) {
                                    {
                                        editingPrompt = prompt
                                        showEditorDialog = true
                                    }
                                } else null,
                                onDeleteCustomPrompt = if (prompt.isCustom) {
                                    { promptPendingDelete = prompt }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        CustomPromptEditorDialog(
            initialPrompt = editingPrompt,
            defaultCategory = selectedCategory ?: PromptCategory.CODING,
            onDismiss = {
                showEditorDialog = false
                editingPrompt = null
            },
            onSave = { title, description, promptText, category ->
                onSaveCustomPrompt(
                    title,
                    description,
                    promptText,
                    category,
                    editingPrompt?.id
                ) {
                    showEditorDialog = false
                    editingPrompt = null
                }
            }
        )
    }

    val pendingDelete = promptPendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { promptPendingDelete = null },
            title = {
                Text(
                    text = "Delete Custom Prompt",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to delete \"${pendingDelete.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomPrompt(pendingDelete.id)
                        promptPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_custom_prompt_button")
                ) {
                    Text("Delete", color = MinovaError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { promptPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PromptCardItem(
    prompt: PromptItem,
    onUsePrompt: () -> Unit,
    onEditCustomPrompt: (() -> Unit)?,
    onDeleteCustomPrompt: (() -> Unit)?
) {
    val accentColor = categoryAccentColor(prompt.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (prompt.isCustom) {
                    MinovaVioletAccent.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onUsePrompt() }
            .testTag("prompt_card_${prompt.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Category Badge + Custom Badge + Edit/Delete Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIcon(prompt.category),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = prompt.category.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }

                    if (prompt.isCustom) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MinovaIndigoSecondary.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MinovaVioletAccent
                            )
                        }
                    }
                }

                if (prompt.isCustom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEditCustomPrompt != null) {
                            IconButton(
                                onClick = onEditCustomPrompt,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("edit_custom_prompt_${prompt.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit custom prompt",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (onDeleteCustomPrompt != null) {
                            IconButton(
                                onClick = onDeleteCustomPrompt,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("delete_custom_prompt_${prompt.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete custom prompt",
                                    tint = MinovaError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = prompt.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (prompt.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Template snippet box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = prompt.promptText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Use in Chat action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onUsePrompt,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinovaCyanPrimary.copy(alpha = 0.16f),
                        contentColor = MinovaCyanPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("use_prompt_button_${prompt.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Use in Chat",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPromptEditorDialog(
    initialPrompt: PromptItem?,
    defaultCategory: PromptCategory,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, promptText: String, category: PromptCategory) -> Unit
) {
    var title by rememberSaveable(initialPrompt?.id) { mutableStateOf(initialPrompt?.title ?: "") }
    var description by rememberSaveable(initialPrompt?.id) { mutableStateOf(initialPrompt?.description ?: "") }
    var promptText by rememberSaveable(initialPrompt?.id) { mutableStateOf(initialPrompt?.promptText ?: "") }
    var selectedCategory by remember(initialPrompt?.id) {
        mutableStateOf(initialPrompt?.category ?: defaultCategory)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPrompt == null) "New Custom Prompt" else "Edit Custom Prompt",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MinovaError,
                        modifier = Modifier.testTag("custom_prompt_validation_error")
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Title") },
                    placeholder = { Text("e.g., Kotlin Coroutine Reviewer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_prompt_title_input")
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PromptCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.displayName) },
                            modifier = Modifier.testTag("dialog_category_${category.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description (optional)") },
                    placeholder = { Text("What this prompt helps with") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_prompt_description_input")
                )

                OutlinedTextField(
                    value = promptText,
                    onValueChange = {
                        promptText = it
                        validationError = null
                    },
                    label = { Text("Prompt Template") },
                    placeholder = { Text("Enter the reusable prompt text…") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_prompt_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    val trimmedBody = promptText.trim()
                    when {
                        trimmedTitle.isEmpty() -> {
                            validationError = "Please enter a prompt title."
                        }
                        trimmedBody.isEmpty() -> {
                            validationError = "Please enter the prompt template text."
                        }
                        else -> {
                            onSave(trimmedTitle, description.trim(), trimmedBody, selectedCategory)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinovaCyanPrimary,
                    contentColor = Color(0xFF04131E)
                ),
                modifier = Modifier.testTag("save_custom_prompt_button")
            ) {
                Text("Save Prompt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_custom_prompt_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun categoryIcon(category: PromptCategory): ImageVector {
    return when (category) {
        PromptCategory.CODING -> Icons.Default.Code
        PromptCategory.STUDY -> Icons.Default.School
        PromptCategory.ENGLISH -> Icons.Default.Translate
        PromptCategory.WRITING -> Icons.Default.Description
    }
}

private fun categoryAccentColor(category: PromptCategory): Color {
    return when (category) {
        PromptCategory.CODING -> MinovaCyanPrimary
        PromptCategory.STUDY -> MinovaSuccess
        PromptCategory.ENGLISH -> MinovaWarning
        PromptCategory.WRITING -> MinovaAzureLight
    }
}
