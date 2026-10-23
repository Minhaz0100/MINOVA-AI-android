package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LearningQuizAttempt
import com.example.data.model.LearningQuizQuestion
import com.example.data.model.LearningSession
import com.example.data.model.QuizQuestionType
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LearnQuizScreen(
    session: LearningSession?,
    questions: List<LearningQuizQuestion>,
    attempts: List<LearningQuizAttempt>,
    onBackToSession: () -> Unit,
    onBackToHome: () -> Unit,
    onSubmitAnswer: (questionId: String, answer: String) -> Unit,
    onResetQuiz: (sessionId: String) -> Unit,
    onFinishQuiz: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var showResultsSummary by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf("") }

    val safeIndex = if (questions.isNotEmpty()) {
        currentQuestionIndex.coerceIn(0, questions.lastIndex)
    } else 0
    val currentQuestion = questions.getOrNull(safeIndex)

    LaunchedEffect(safeIndex, currentQuestion?.id, currentQuestion?.userAnswer) {
        selectedAnswer = currentQuestion?.userAnswer ?: ""
    }

    val answeredCount = questions.count { it.isAnswered }
    val correctCount = questions.count { it.isAnswered && it.isCorrect }
    val allAnswered = questions.isNotEmpty() && answeredCount == questions.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("learn_quiz_screen")
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
                        onClick = onBackToSession,
                        modifier = Modifier.testTag("learn_quiz_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Lesson",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.topic ?: "Knowledge Check & Quiz",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (showResultsSummary) {
                                "Quiz Summary • Score: $correctCount/${questions.size}"
                            } else if (questions.isNotEmpty()) {
                                "Question ${safeIndex + 1} of ${questions.size} • $answeredCount Answered"
                            } else {
                                "Practice Quiz"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MinovaCyanPrimary
                        )
                    }

                    if (session != null && questions.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onResetQuiz(session.id)
                                currentQuestionIndex = 0
                                showResultsSummary = false
                                selectedAnswer = ""
                            },
                            modifier = Modifier.testTag("learn_quiz_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Quiz",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (questions.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = {
                            if (questions.isEmpty()) 0f
                            else answeredCount.toFloat() / questions.size.toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MinovaCyanPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = MinovaCyanPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No quiz questions available for this session.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackToSession) {
                        Text("Return to Lessons")
                    }
                }
            }
        } else if (showResultsSummary) {
            // Quiz Results & Weak Area Review
            val scorePercentage = if (questions.isNotEmpty()) {
                ((correctCount.toFloat() / questions.size.toFloat()) * 100).toInt()
            } else 0
            val incorrectQuestions = questions.filter { it.isAnswered && !it.isCorrect }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quiz_summary_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MinovaCyanPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "$scorePercentage%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MinovaCyanPrimary
                        )

                        Text(
                            text = "You answered $correctCount out of ${questions.size} questions correctly",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                scorePercentage >= 80 -> "Excellent mastery! You are ready to advance."
                                scorePercentage >= 50 -> "Good progress! Review the missed concepts below to strengthen weak areas."
                                else -> "Keep practicing! Re-read the lessons and retry the quiz to build confidence."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    session?.let { onResetQuiz(it.id) }
                                    currentQuestionIndex = 0
                                    showResultsSummary = false
                                    selectedAnswer = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quiz_retry_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Quiz")
                            }

                            Button(
                                onClick = onBackToSession,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quiz_back_to_lessons_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MinovaCyanPrimary,
                                    contentColor = Color(0xFF061320)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lessons", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Weak Areas / Missed Questions Breakdown
                if (incorrectQuestions.isNotEmpty()) {
                    Text(
                        text = "Concepts to Review (${incorrectQuestions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    incorrectQuestions.forEach { q ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = q.questionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Your answer: ${q.userAnswer ?: "None"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Correct answer: ${q.correctAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4ADE80)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = q.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Past Quiz Attempts History
                if (attempts.isNotEmpty()) {
                    Text(
                        text = "Attempt History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                    attempts.take(5).forEach { attempt ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormat.format(Date(attempt.attemptedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${attempt.score}/${attempt.totalQuestions} (${attempt.percentage}%)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MinovaCyanPrimary
                                )
                            }
                        }
                    }
                }
            }
        } else if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Question Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MinovaIndigoSecondary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = when (currentQuestion.questionType) {
                            QuizQuestionType.MULTIPLE_CHOICE -> "Multiple Choice"
                            QuizQuestionType.TRUE_FALSE -> "True / False"
                            QuizQuestionType.SHORT_ANSWER -> "Short Answer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MinovaAzureLight,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Question Text Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentQuestion.questionText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                            modifier = Modifier.testTag("quiz_question_text")
                        )
                    }
                }

                // Answer Input / Options
                when (currentQuestion.questionType) {
                    QuizQuestionType.MULTIPLE_CHOICE, QuizQuestionType.TRUE_FALSE -> {
                        val optionsToRender = if (currentQuestion.options.isNotEmpty()) {
                            currentQuestion.options
                        } else {
                            listOf("True", "False")
                        }

                        optionsToRender.forEachIndexed { idx, option ->
                            val isSelected = selectedAnswer.equals(option, ignoreCase = true)
                            val isCorrectOption = currentQuestion.isAnswered &&
                                (option.equals(currentQuestion.correctAnswer, ignoreCase = true) ||
                                    option.trim().startsWith(currentQuestion.correctAnswer.trim(), ignoreCase = true))
                            val isWrongSelection = currentQuestion.isAnswered && isSelected && !currentQuestion.isCorrect

                            val borderColor = when {
                                isCorrectOption -> Color(0xFF4ADE80)
                                isWrongSelection -> MaterialTheme.colorScheme.error
                                isSelected -> MinovaCyanPrimary
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            }

                            val bgColor = when {
                                isCorrectOption -> Color(0xFF4ADE80).copy(alpha = 0.14f)
                                isWrongSelection -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                isSelected -> MinovaCyanPrimary.copy(alpha = 0.14f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !currentQuestion.isAnswered) {
                                        selectedAnswer = option
                                    }
                                    .testTag("quiz_option_$idx"),
                                color = bgColor,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MinovaCyanPrimary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ('A' + idx).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF061320)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    QuizQuestionType.SHORT_ANSWER -> {
                        OutlinedTextField(
                            value = selectedAnswer,
                            onValueChange = {
                                if (!currentQuestion.isAnswered) {
                                    selectedAnswer = it
                                }
                            },
                            enabled = !currentQuestion.isAnswered,
                            label = { Text("Your Answer") },
                            placeholder = { Text("Type your answer here...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_short_answer_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MinovaCyanPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                // Submit Answer Button (if not yet answered)
                if (!currentQuestion.isAnswered) {
                    Button(
                        onClick = {
                            if (selectedAnswer.isNotBlank()) {
                                onSubmitAnswer(currentQuestion.id, selectedAnswer)
                            }
                        },
                        enabled = selectedAnswer.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("quiz_submit_answer_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinovaCyanPrimary,
                            contentColor = Color(0xFF061320)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Check Answer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Instant Evaluation & Explanation Card
                if (currentQuestion.isAnswered) {
                    val feedbackColor = if (currentQuestion.isCorrect) {
                        Color(0xFF4ADE80)
                    } else {
                        MaterialTheme.colorScheme.error
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_feedback_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = feedbackColor.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (currentQuestion.isCorrect) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = feedbackColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentQuestion.isCorrect) {
                                        "Correct! Well done."
                                    } else {
                                        "Not quite right"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = feedbackColor
                                )
                            }

                            if (!currentQuestion.isCorrect) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Correct Answer: ${currentQuestion.correctAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentQuestion.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Bar for Questions
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (safeIndex > 0) {
                                currentQuestionIndex = safeIndex - 1
                            }
                        },
                        enabled = safeIndex > 0,
                        modifier = Modifier.testTag("quiz_prev_question_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Previous")
                    }

                    if (safeIndex < questions.lastIndex) {
                        Button(
                            onClick = {
                                currentQuestionIndex = safeIndex + 1
                            },
                            modifier = Modifier.testTag("quiz_next_question_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MinovaCyanPrimary,
                                contentColor = Color(0xFF061320)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (allAnswered && session != null) {
                                    onFinishQuiz(session.id)
                                }
                                showResultsSummary = true
                            },
                            modifier = Modifier.testTag("quiz_finish_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MinovaCyanPrimary,
                                contentColor = Color(0xFF061320)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Score", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
