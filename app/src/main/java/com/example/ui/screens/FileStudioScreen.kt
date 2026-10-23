package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentBlockType
import com.example.data.model.DocumentContentBlock
import com.example.data.model.FileStudioMode
import com.example.data.model.GeneratedBinaryFile
import com.example.data.model.ImageAspectRatio
import com.example.data.model.SpreadsheetWorksheet
import com.example.data.repository.FileStudioRepositoryImpl
import com.example.data.util.GeneratedFileExporter
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileStudioScreen(
    isLoading: Boolean,
    generatedFile: GeneratedBinaryFile?,
    errorMessage: String?,
    errorCode: String?,
    savedConfirmationMessage: String?,
    onBack: () -> Unit,
    onGenerateDocument: (
        mode: FileStudioMode,
        title: String,
        subtitle: String,
        author: String,
        blocks: List<DocumentContentBlock>
    ) -> Unit,
    onGenerateSpreadsheet: (
        workbookTitle: String,
        worksheets: List<SpreadsheetWorksheet>
    ) -> Unit,
    onGenerateImage: (
        prompt: String,
        aspectRatio: ImageAspectRatio
    ) -> Unit,
    onFileSavedSuccess: (String) -> Unit,
    onFileSaveOrShareError: (String) -> Unit,
    onClearError: () -> Unit,
    onClearSavedConfirmation: () -> Unit,
    onClearGeneratedFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(FileStudioMode.PDF) }

    // PDF & Word (DOCX) state
    var docTitle by remember { mutableStateOf("Quarterly Architecture Overview") }
    var docSubtitle by remember { mutableStateOf("System Design & Delivery Report") }
    var docAuthor by remember { mutableStateOf("MINOVA Engineering") }
    var docBlocks by remember {
        mutableStateOf(
            listOf(
                DocumentContentBlock(
                    type = DocumentBlockType.HEADING,
                    headingLevel = 1,
                    text = "Executive Summary"
                ),
                DocumentContentBlock(
                    type = DocumentBlockType.PARAGRAPH,
                    text = "This document outlines key milestones, architecture metrics, and deployment readiness."
                ),
                DocumentContentBlock(
                    type = DocumentBlockType.LIST,
                    listOrdered = false,
                    listItems = listOf(
                        "Modular Android client architecture",
                        "Structured binary file export pipeline"
                    )
                ),
                DocumentContentBlock(
                    type = DocumentBlockType.TABLE,
                    tableHeaders = listOf("Component", "Status", "Owner"),
                    tableRows = listOf(
                        listOf("PDF Engine", "Ready", "Core Team"),
                        listOf("DOCX Builder", "Ready", "Docs Team")
                    )
                )
            )
        )
    }

    // Excel (XLSX) state
    var workbookTitle by remember { mutableStateOf("Q4 Financial & Sprint Metrics") }
    var worksheets by remember {
        mutableStateOf(
            listOf(
                SpreadsheetWorksheet(
                    name = "Summary",
                    headers = listOf("Metric", "Target", "Actual"),
                    rows = listOf(
                        listOf("Sprint Velocity", "45", "48"),
                        listOf("Release Coverage", "90%", "94%")
                    )
                )
            )
        )
    }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }

    // Image state
    var imagePrompt by remember {
        mutableStateOf("A futuristic dark-mode cybernetic command center with glowing cyan holograms")
    }
    var selectedAspectRatio by remember { mutableStateOf(ImageAspectRatio.SQUARE_1_1) }

    // Storage Access Framework launcher for saving the generated binary file
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri ->
        val fileToSave = generatedFile
        if (destinationUri != null && fileToSave != null) {
            coroutineScope.launch {
                val saveResult = GeneratedFileExporter.saveToUri(
                    contentResolver = context.contentResolver,
                    destinationUri = destinationUri,
                    generatedFile = fileToSave
                )
                saveResult.fold(
                    onSuccess = { writtenBytes ->
                        onFileSavedSuccess(
                            "Saved '${fileToSave.fileName}' ($writtenBytes bytes) to device storage."
                        )
                    },
                    onFailure = { err ->
                        onFileSaveOrShareError(
                            err.message ?: "Failed to save '${fileToSave.fileName}'."
                        )
                    }
                )
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("file_studio_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("file_studio_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Chat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AI File Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MinovaCyanPrimary.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedMode.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MinovaCyanPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = "PDF • Word (DOCX) • Excel (XLSX) • Image",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0E1B31),
                                    Color(0xFF132644)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MinovaCyanPrimary.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Structured Document & Spreadsheet Generator",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Build structured PDF, Word, or Excel files via MINOVA AI backend endpoints and export binary files using Android Storage Access Framework.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 1. Mode Selector (PDF, Word DOCX, Excel XLSX, Image)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select File Format Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileStudioMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        val icon = when (mode) {
                            FileStudioMode.PDF -> Icons.Default.PictureAsPdf
                            FileStudioMode.DOCX -> Icons.Default.Description
                            FileStudioMode.XLSX -> Icons.Default.TableChart
                            FileStudioMode.IMAGE -> Icons.Default.Image
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isLoading) {
                                    selectedMode = mode
                                }
                            },
                            enabled = !isLoading,
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = mode.shortLabel,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinovaCyanPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = MinovaCyanPrimary,
                                selectedLeadingIconColor = MinovaCyanPrimary
                            ),
                            modifier = Modifier.testTag("file_studio_mode_${mode.id}")
                        )
                    }
                }
            }

            // 2. Mode-Specific Structured Editor
            when (selectedMode) {
                FileStudioMode.PDF, FileStudioMode.DOCX -> {
                    DocumentStructuredEditor(
                        mode = selectedMode,
                        title = docTitle,
                        subtitle = docSubtitle,
                        author = docAuthor,
                        blocks = docBlocks,
                        enabled = !isLoading,
                        onTitleChange = { docTitle = it },
                        onSubtitleChange = { docSubtitle = it },
                        onAuthorChange = { docAuthor = it },
                        onBlocksChange = { docBlocks = it }
                    )
                }

                FileStudioMode.XLSX -> {
                    SpreadsheetStructuredEditor(
                        workbookTitle = workbookTitle,
                        worksheets = worksheets,
                        selectedSheetIndex = selectedSheetIndex.coerceIn(0, (worksheets.size - 1).coerceAtLeast(0)),
                        enabled = !isLoading,
                        onWorkbookTitleChange = { workbookTitle = it },
                        onSelectSheet = { selectedSheetIndex = it },
                        onWorksheetsChange = { updated ->
                            worksheets = updated
                            if (selectedSheetIndex >= updated.size) {
                                selectedSheetIndex = (updated.size - 1).coerceAtLeast(0)
                            }
                        }
                    )
                }

                FileStudioMode.IMAGE -> {
                    ImageModeEditor(
                        prompt = imagePrompt,
                        selectedAspectRatio = selectedAspectRatio,
                        enabled = !isLoading,
                        onPromptChange = { imagePrompt = it },
                        onSelectAspectRatio = { selectedAspectRatio = it }
                    )
                }
            }

            // 3. Primary Generate Action Button (Disabled while request is running to prevent duplicate submissions)
            Button(
                onClick = {
                    if (!isLoading) {
                        when (selectedMode) {
                            FileStudioMode.PDF, FileStudioMode.DOCX -> {
                                onGenerateDocument(
                                    selectedMode,
                                    docTitle,
                                    docSubtitle,
                                    docAuthor,
                                    docBlocks
                                )
                            }
                            FileStudioMode.XLSX -> {
                                onGenerateSpreadsheet(
                                    workbookTitle,
                                    worksheets
                                )
                            }
                            FileStudioMode.IMAGE -> {
                                onGenerateImage(
                                    imagePrompt,
                                    selectedAspectRatio
                                )
                            }
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinovaCyanPrimary,
                    contentColor = Color(0xFF061320)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("file_studio_generate_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF061320),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Generating ${selectedMode.shortLabel}...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Generate ${selectedMode.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. Loading Card
            if (isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_studio_loading_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MinovaCyanPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Requesting ${selectedMode.displayName} from MINOVA backend...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "POST ${selectedMode.endpointPath} • Duplicate submissions locked",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Dedicated IMAGE_GENERATION_UNAVAILABLE / Error Banner
            if (!errorMessage.isNullOrBlank()) {
                val isImageUnavailable = errorCode == "IMAGE_GENERATION_UNAVAILABLE"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (isImageUnavailable) "file_studio_image_unavailable_card"
                            else "file_studio_error_card"
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isImageUnavailable) {
                            Color(0xFF2B2111)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isImageUnavailable) {
                                        Icons.Default.WarningAmber
                                    } else {
                                        Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = if (isImageUnavailable) {
                                        Color(0xFFFBBF24)
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isImageUnavailable) {
                                        "Image Generation Unavailable (503)"
                                    } else if (errorCode == "VALIDATION_ERROR") {
                                        "Validation Error"
                                    } else {
                                        "File Generation Error"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isImageUnavailable) {
                                        Color(0xFFFDE68A)
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }

                            IconButton(
                                onClick = onClearError,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = if (isImageUnavailable) {
                                        Color(0xFFFDE68A)
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isImageUnavailable) {
                                Color(0xFFFEF3C7)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )

                        if (!errorCode.isNullOrBlank()) {
                            Text(
                                text = "Code: $errorCode",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (isImageUnavailable) {
                                    Color(0xFFFBBF24)
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                }
                            )
                        }
                    }
                }
            }

            // 6. Save Confirmation Banner (Only shown after Storage Access Framework write succeeds)
            if (!savedConfirmationMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_studio_saved_confirmation_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = MinovaCyanPrimary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = savedConfirmationMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onClearSavedConfirmation,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss confirmation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 7. Generated Binary File Ready Card (Save via SAF or Share via FileProvider)
            if (generatedFile != null) {
                val decodedImageBitmap = remember(generatedFile) {
                    if (generatedFile.isImage) {
                        FileStudioRepositoryImpl.decodeBitmapSafely(generatedFile.bytes)
                    } else {
                        null
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_studio_result_card"),
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
                                color = MinovaCyanPrimary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (generatedFile.isImage) "AI Image Generated" else "Binary File Generated",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (savedConfirmationMessage != null) {
                                            "Saved to device storage"
                                        } else {
                                            "Ready in memory — choose 'Save to Device' to write to storage"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MinovaAzureLight
                                    )
                                }
                            }

                            TextButton(onClick = onClearGeneratedFile) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (generatedFile.isImage) {
                            if (decodedImageBitmap != null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MinovaCyanPrimary.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    color = Color(0xFF070D17)
                                ) {
                                    Image(
                                        bitmap = decodedImageBitmap.asImageBitmap(),
                                        contentDescription = "Generated AI image: ${generatedFile.fileName}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 200.dp, max = 360.dp)
                                            .testTag("file_studio_generated_image_preview")
                                    )
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("file_studio_image_decode_warning"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2B2111)
                                ) {
                                    Text(
                                        text = "Binary image received (${generatedFile.formattedSize}), but bitmap preview could not be decoded.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFEF3C7),
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF111C30)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = generatedFile.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("file_studio_result_filename")
                                )
                                val dimensionsSuffix = if (decodedImageBitmap != null) {
                                    " • ${decodedImageBitmap.width}×${decodedImageBitmap.height} px"
                                } else {
                                    ""
                                }
                                Text(
                                    text = "Format: ${generatedFile.mode.displayName} • Size: ${generatedFile.formattedSize} (${generatedFile.sizeBytes} bytes)$dimensionsSuffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "MIME: ${generatedFile.mimeType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    createDocumentLauncher.launch(generatedFile.fileName)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MinovaCyanPrimary,
                                    contentColor = Color(0xFF061320)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("file_studio_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save to Device",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val shareRes = GeneratedFileExporter.shareGeneratedFile(
                                            context = context,
                                            generatedFile = generatedFile
                                        )
                                        shareRes.onFailure { err ->
                                            onFileSaveOrShareError(
                                                err.message ?: "Could not share file."
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("file_studio_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Share File",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentStructuredEditor(
    mode: FileStudioMode,
    title: String,
    subtitle: String,
    author: String,
    blocks: List<DocumentContentBlock>,
    enabled: Boolean,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onBlocksChange: (List<DocumentContentBlock>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${mode.displayName} Metadata",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                enabled = enabled,
                label = { Text("Document Title *") },
                placeholder = { Text("Enter document title...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_studio_doc_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = onSubtitleChange,
                    enabled = enabled,
                    label = { Text("Subtitle (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("file_studio_doc_subtitle_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = author,
                    onValueChange = onAuthorChange,
                    enabled = enabled,
                    label = { Text("Author (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("file_studio_doc_author_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Structured Content (${blocks.size} blocks)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Add Block Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onBlocksChange(
                            blocks + DocumentContentBlock(
                                type = DocumentBlockType.HEADING,
                                headingLevel = 2,
                                text = "New Section Heading"
                            )
                        )
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("file_studio_add_heading_button")
                ) {
                    Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Heading", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        onBlocksChange(
                            blocks + DocumentContentBlock(
                                type = DocumentBlockType.PARAGRAPH,
                                text = "Enter paragraph text..."
                            )
                        )
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("file_studio_add_paragraph_button")
                ) {
                    Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Paragraph", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        onBlocksChange(
                            blocks + DocumentContentBlock(
                                type = DocumentBlockType.LIST,
                                listOrdered = false,
                                listItems = listOf("First item", "Second item")
                            )
                        )
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("file_studio_add_list_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ List", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        onBlocksChange(
                            blocks + DocumentContentBlock(
                                type = DocumentBlockType.TABLE,
                                tableHeaders = listOf("Column 1", "Column 2"),
                                tableRows = listOf(listOf("Row 1A", "Row 1B"))
                            )
                        )
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("file_studio_add_table_button")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Table", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Render each editable block
            blocks.forEachIndexed { index, block ->
                DocumentBlockItemCard(
                    index = index,
                    totalCount = blocks.size,
                    block = block,
                    enabled = enabled,
                    onUpdate = { updatedBlock ->
                        onBlocksChange(
                            blocks.toMutableList().also { it[index] = updatedBlock }
                        )
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = blocks.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index - 1, item)
                            onBlocksChange(mutable)
                        }
                    },
                    onMoveDown = {
                        if (index < blocks.lastIndex) {
                            val mutable = blocks.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index + 1, item)
                            onBlocksChange(mutable)
                        }
                    },
                    onDelete = {
                        onBlocksChange(blocks.filterIndexed { idx, _ -> idx != index })
                    }
                )
            }
        }
    }
}

@Composable
private fun DocumentBlockItemCard(
    index: Int,
    totalCount: Int,
    block: DocumentContentBlock,
    enabled: Boolean,
    onUpdate: (DocumentContentBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_studio_block_$index"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0B1220),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1} • ${block.type.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MinovaCyanPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move block up",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (index < totalCount - 1) {
                        IconButton(
                            onClick = onMoveDown,
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move block down",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = enabled,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove block",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            when (block.type) {
                DocumentBlockType.HEADING -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 2, 3).forEach { lvl ->
                            FilterChip(
                                selected = block.headingLevel == lvl,
                                onClick = { onUpdate(block.copy(headingLevel = lvl)) },
                                enabled = enabled,
                                label = { Text("H$lvl") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onUpdate(block.copy(text = it)) },
                        enabled = enabled,
                        label = { Text("Heading Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DocumentBlockType.PARAGRAPH -> {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onUpdate(block.copy(text = it)) },
                        enabled = enabled,
                        label = { Text("Paragraph Content") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DocumentBlockType.LIST -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !block.listOrdered,
                            onClick = { onUpdate(block.copy(listOrdered = false)) },
                            enabled = enabled,
                            label = { Text("Bulleted") }
                        )
                        FilterChip(
                            selected = block.listOrdered,
                            onClick = { onUpdate(block.copy(listOrdered = true)) },
                            enabled = enabled,
                            label = { Text("Numbered") }
                        )
                    }
                    OutlinedTextField(
                        value = block.listItems.joinToString("\n"),
                        onValueChange = { text ->
                            onUpdate(block.copy(listItems = text.split("\n")))
                        },
                        enabled = enabled,
                        label = { Text("List Items (one item per line)") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DocumentBlockType.TABLE -> {
                    OutlinedTextField(
                        value = block.tableHeaders.joinToString(", "),
                        onValueChange = { headerLine ->
                            val newHeaders = headerLine.split(",").map { it.trimStart() }
                                .ifEmpty { listOf("Column 1") }
                            onUpdate(block.copy(tableHeaders = newHeaders))
                        },
                        enabled = enabled,
                        label = { Text("Table Column Headers (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = block.tableRows.joinToString("\n") { it.joinToString(", ") },
                        onValueChange = { rowsText ->
                            val parsedRows = rowsText.lines()
                                .filter { it.isNotBlank() }
                                .map { line -> line.split(",").map { it.trim() } }
                            onUpdate(block.copy(tableRows = parsedRows))
                        },
                        enabled = enabled,
                        label = { Text("Table Rows (one row per line, comma-separated cells)") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetStructuredEditor(
    workbookTitle: String,
    worksheets: List<SpreadsheetWorksheet>,
    selectedSheetIndex: Int,
    enabled: Boolean,
    onWorkbookTitleChange: (String) -> Unit,
    onSelectSheet: (Int) -> Unit,
    onWorksheetsChange: (List<SpreadsheetWorksheet>) -> Unit
) {
    val activeSheet = worksheets.getOrNull(selectedSheetIndex) ?: SpreadsheetWorksheet()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Excel (XLSX) Workbook Structure",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = workbookTitle,
                onValueChange = onWorkbookTitleChange,
                enabled = enabled,
                label = { Text("Workbook Title *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_studio_xlsx_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Worksheet Tabs + Add Worksheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                worksheets.forEachIndexed { idx, sheet ->
                    FilterChip(
                        selected = idx == selectedSheetIndex,
                        onClick = { onSelectSheet(idx) },
                        enabled = enabled,
                        label = { Text(sheet.name.ifBlank { "Sheet${idx + 1}" }) },
                        modifier = Modifier.testTag("file_studio_sheet_tab_$idx")
                    )
                }

                OutlinedButton(
                    onClick = {
                        val nextNum = worksheets.size + 1
                        val updated = worksheets + SpreadsheetWorksheet(
                            name = "Sheet$nextNum",
                            headers = listOf("Column A", "Column B"),
                            rows = listOf(listOf("", ""))
                        )
                        onWorksheetsChange(updated)
                        onSelectSheet(updated.lastIndex)
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("file_studio_add_sheet_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Sheet", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Active Worksheet Editor
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B1220)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = activeSheet.name,
                            onValueChange = { newName ->
                                val updated = worksheets.toMutableList()
                                if (selectedSheetIndex in updated.indices) {
                                    updated[selectedSheetIndex] = activeSheet.copy(name = newName)
                                    onWorksheetsChange(updated)
                                }
                            },
                            enabled = enabled,
                            label = { Text("Worksheet Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("file_studio_sheet_name_input")
                        )

                        if (worksheets.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val updated = worksheets.filterIndexed { i, _ -> i != selectedSheetIndex }
                                    onWorksheetsChange(updated)
                                },
                                enabled = enabled
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete worksheet",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = activeSheet.headers.joinToString(", "),
                        onValueChange = { headerText ->
                            val newHeaders = headerText.split(",").map { it.trimStart() }
                            val updated = worksheets.toMutableList()
                            if (selectedSheetIndex in updated.indices) {
                                updated[selectedSheetIndex] = activeSheet.copy(headers = newHeaders)
                                onWorksheetsChange(updated)
                            }
                        },
                        enabled = enabled,
                        label = { Text("Column Headers * (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_studio_sheet_headers_input")
                    )

                    // Rows editor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spreadsheet Rows (${activeSheet.rows.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MinovaCyanPrimary
                        )

                        TextButton(
                            onClick = {
                                val emptyRow = List(activeSheet.headers.size.coerceAtLeast(1)) { "" }
                                val updated = worksheets.toMutableList()
                                if (selectedSheetIndex in updated.indices) {
                                    updated[selectedSheetIndex] = activeSheet.copy(
                                        rows = activeSheet.rows + listOf(emptyRow)
                                    )
                                    onWorksheetsChange(updated)
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.testTag("file_studio_sheet_add_row_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Row", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    activeSheet.rows.forEachIndexed { rowIndex, rowCells ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = rowCells.joinToString(", "),
                                onValueChange = { rowLine ->
                                    val newCells = rowLine.split(",").map { it.trimStart() }
                                    val updatedRows = activeSheet.rows.toMutableList()
                                    updatedRows[rowIndex] = newCells
                                    val updatedSheets = worksheets.toMutableList()
                                    if (selectedSheetIndex in updatedSheets.indices) {
                                        updatedSheets[selectedSheetIndex] = activeSheet.copy(rows = updatedRows)
                                        onWorksheetsChange(updatedSheets)
                                    }
                                },
                                enabled = enabled,
                                label = { Text("Row ${rowIndex + 1} (comma-separated)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("file_studio_sheet_row_$rowIndex")
                            )

                            IconButton(
                                onClick = {
                                    val updatedRows = activeSheet.rows.filterIndexed { idx, _ -> idx != rowIndex }
                                    val updatedSheets = worksheets.toMutableList()
                                    if (selectedSheetIndex in updatedSheets.indices) {
                                        updatedSheets[selectedSheetIndex] = activeSheet.copy(rows = updatedRows)
                                        onWorksheetsChange(updatedSheets)
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete row",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageModeEditor(
    prompt: String,
    selectedAspectRatio: ImageAspectRatio,
    enabled: Boolean,
    onPromptChange: (String) -> Unit,
    onSelectAspectRatio: (ImageAspectRatio) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AI Image Generation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Backend endpoint notice
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MinovaIndigoSecondary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MinovaAzureLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generates JPEG images via the MINOVA AI Cloudflare backend (POST /api/image/generate). Returned binary JPEG images are decoded and displayed below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                enabled = enabled,
                label = { Text("Image Prompt *") },
                placeholder = { Text("Describe the image you want to generate...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_studio_image_prompt_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinovaCyanPrimary
                )
            )

            Text(
                text = "Supported Aspect Ratio",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageAspectRatio.entries.forEach { ratio ->
                    val isSelected = selectedAspectRatio == ratio
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectAspectRatio(ratio) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = ratio.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinovaCyanPrimary.copy(alpha = 0.22f),
                            selectedLabelColor = MinovaCyanPrimary
                        ),
                        modifier = Modifier.testTag("file_studio_aspect_${ratio.value}")
                    )
                }
            }
        }
    }
}
