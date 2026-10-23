package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LearningLesson
import com.example.data.model.LearningSession
import com.example.data.util.MarkdownContentBlock
import com.example.ui.components.CodeBlockView
import com.example.ui.components.RichMarkdownView
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary

@Composable
fun LearnLessonScreen(
    session: LearningSession?,
    lessons: List<LearningLesson>,
    currentLessonIndex: Int,
    isTutorLoading: Boolean,
    tutorResponse: String?,
    tutorError: String?,
    onBackToHome: () -> Unit,
    onSelectLesson: (Int) -> Unit,
    onMarkCompleted: (lessonId: String, isCompleted: Boolean) -> Unit,
    onToggleBookmark: (lessonId: String, currentBookmarked: Boolean) -> Unit,
    onOpenQuiz: (sessionId: String) -> Unit,
    onAskTutor: (lesson: LearningLesson, question: String) -> Unit,
    onClearTutor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLesson = lessons.getOrNull(currentLessonIndex)
    var showTutorDialog by remember { mutableStateOf(false) }
    var tutorQuestionInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier.testTag("learn_lesson_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Learn Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.topic ?: "Learning Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = if (lessons.isNotEmpty()) "Lesson ${currentLessonIndex + 1} of ${lessons.size}" else "Lessons",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MinovaCyanPrimary
                        )
                    }

                    if (currentLesson != null) {
                        IconButton(
                            onClick = { onToggleBookmark(currentLesson.id, currentLesson.isBookmarked) },
                            modifier = Modifier.testTag("learn_lesson_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (currentLesson.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Lesson",
                                tint = if (currentLesson.isBookmarked) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    session?.let { s ->
                        IconButton(
                            onClick = { onOpenQuiz(s.id) },
                            modifier = Modifier.testTag("learn_lesson_top_quiz_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = "Open Quiz",
                                tint = MinovaAzureLight
                            )
                        }
                    }
                }

                // Progress Indicator
                if (session != null && session.totalLessons > 0) {
                    LinearProgressIndicator(
                        progress = { session.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MinovaCyanPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Lesson Content Body
        if (currentLesson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MinovaCyanPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .navigationBarsPadding()
            ) {
                // Lesson Title & Overview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MinovaCyanPrimary.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MinovaCyanPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${currentLessonIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaCyanPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentLesson.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentLesson.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Markdown Content
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        RichMarkdownView(
                            content = currentLesson.contentMarkdown,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Code Snippet Section (if applicable)
                if (!currentLesson.codeSnippet.isNullOrBlank()) {
                    Text(
                        text = "Practical Implementation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MinovaCyanPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    CodeBlockView(
                        codeBlock = MarkdownContentBlock.Code(
                            code = currentLesson.codeSnippet,
                            language = currentLesson.codeLanguage ?: "kotlin"
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!currentLesson.codeExplanation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MinovaCyanPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Code Explanation",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MinovaCyanPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentLesson.codeExplanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!currentLesson.expectedOutput.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF0F141C)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Expected Output:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaAzureLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentLesson.expectedOutput,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4AF6C3)
                                )
                            }
                        }
                    }

                    if (!currentLesson.debuggingGuidance.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Common Pitfall to Avoid:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentLesson.debuggingGuidance,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Practice Exercise
                if (!currentLesson.practiceExercise.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MinovaIndigoSecondary.copy(alpha = 0.15f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MinovaIndigoSecondary.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = MinovaAzureLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Practice Exercise",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaAzureLight
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentLesson.practiceExercise,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Key Takeaways / Recap
                if (currentLesson.keyTakeaways.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MinovaCyanPrimary.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Key Takeaways & Recap",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaCyanPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentLesson.keyTakeaways,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Mark Completed & Ask AI Tutor Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onMarkCompleted(currentLesson.id, !currentLesson.isCompleted) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("learn_lesson_mark_completed_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLesson.isCompleted) Color(0xFF1E3A2F) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentLesson.isCompleted) Color(0xFF4AF6C3) else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (currentLesson.isCompleted) Icons.Default.Check else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentLesson.isCompleted) "Completed" else "Mark Complete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { showTutorDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("learn_lesson_ask_tutor_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MinovaCyanPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinovaCyanPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ask AI Tutor",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Controls (Previous, Next, Take Quiz)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentLessonIndex > 0) onSelectLesson(currentLessonIndex - 1) },
                        enabled = currentLessonIndex > 0,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("learn_lesson_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous")
                    }

                    if (currentLessonIndex < lessons.size - 1) {
                        Button(
                            onClick = { onSelectLesson(currentLessonIndex + 1) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MinovaCyanPrimary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("learn_lesson_next_button")
                        ) {
                            Text("Next Lesson")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        session?.let { s ->
                            Button(
                                onClick = { onOpenQuiz(s.id) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MinovaCyanPrimary,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("learn_lesson_quiz_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Quiz,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take Practice Quiz")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // AI Tutor Dialog
    if (showTutorDialog && currentLesson != null) {
        AlertDialog(
            onDismissRequest = {
                showTutorDialog = false
                onClearTutor()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MinovaCyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ask AI About This Lesson",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Have questions about \"${currentLesson.title}\"? The AI Tutor explains concepts, syntax, or reasoning specifically in this context.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tutorQuestionInput,
                        onValueChange = { tutorQuestionInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("learn_tutor_question_input"),
                        placeholder = {
                            Text("e.g. Can you explain why we need this step with a real-world example?")
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinovaCyanPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isTutorLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MinovaCyanPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MinovaCyanPrimary
                            )
                        }
                    }

                    if (tutorError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tutorError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!tutorResponse.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MinovaCyanPrimary.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "AI Tutor Explanation:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaCyanPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                RichMarkdownView(
                                    content = tutorResponse,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tutorQuestionInput.isNotBlank()) {
                            onAskTutor(currentLesson, tutorQuestionInput)
                        }
                    },
                    enabled = tutorQuestionInput.isNotBlank() && !isTutorLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinovaCyanPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("learn_tutor_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTutorDialog = false
                        onClearTutor()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
