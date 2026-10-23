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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LearningGoal
import com.example.data.model.LearningLanguage
import com.example.data.model.LearningLevel
import com.example.data.model.LearningSession
import com.example.data.model.LearningSubjectCategory
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaIndigoSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnHomeScreen(
    sessions: List<LearningSession>,
    isGenerating: Boolean,
    generationError: String?,
    onBack: () -> Unit,
    onStartLearningPlan: (
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ) -> Unit,
    onOpenSession: (sessionId: String, lessonIndex: Int) -> Unit,
    onOpenQuiz: (sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var topicInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LearningSubjectCategory.PROGRAMMING) }
    var selectedLevel by remember { mutableStateOf(LearningLevel.BEGINNER) }
    var selectedGoal by remember { mutableStateOf(LearningGoal.UNDERSTAND_CONCEPT) }
    var selectedLanguage by remember { mutableStateOf(LearningLanguage.ENGLISH) }
    var selectedDuration by remember { mutableIntStateOf(20) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val activeSessions = sessions.filterNot { it.isCompleted }
    val completedSessions = sessions.filter { it.isCompleted }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("learn_home_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Chat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MinovaCyanPrimary, MinovaIndigoSecondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Learn Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Personalized AI Tutor & Curriculum",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MinovaCyanPrimary
                    )
                }
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // Header Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MinovaCyanPrimary.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Step-by-Step AI Learning",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MinovaCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "What do you want to master today?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick a subject or enter any topic. MINOVA AI generates interactive lessons, code examples, knowledge checks, and a graded practice quiz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Topic Input Section
            Text(
                text = "1. Enter Topic or Concept",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = topicInput,
                onValueChange = {
                    topicInput = it
                    if (validationError != null) validationError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("learn_home_topic_input"),
                placeholder = {
                    Text(
                        text = "e.g., Python Coroutines, Calculus Derivatives, Quantum Computing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (topicInput.isNotBlank()) {
                        IconButton(onClick = { topicInput = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear topic",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinovaCyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                isError = validationError != null
            )

            if (validationError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subject Category Selection
            Text(
                text = "2. Select Subject Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LearningSubjectCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = category.getIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinovaCyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = MinovaCyanPrimary,
                            selectedLeadingIconColor = MinovaCyanPrimary
                        ),
                        modifier = Modifier.testTag("learn_category_chip_${category.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Learning Level & Goal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Level
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3. Level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LearningLevel.entries.forEach { level ->
                        val isSelected = level == selectedLevel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MinovaCyanPrimary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedLevel = level }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = level.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Language & Duration
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "4. Language",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LearningLanguage.entries.forEach { lang ->
                        val isSelected = lang == selectedLanguage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MinovaAzureLight.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MinovaAzureLight else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedLanguage = lang }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MinovaAzureLight else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Goal Selection
            Text(
                text = "5. Primary Goal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LearningGoal.entries.forEach { goal ->
                    val isSelected = goal == selectedGoal
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGoal = goal },
                        label = { Text(goal.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinovaIndigoSecondary.copy(alpha = 0.25f),
                            selectedLabelColor = MinovaAzureLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Daily Duration
            Text(
                text = "6. Daily Study Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 25, 40, 60).forEach { mins ->
                    val isSelected = mins == selectedDuration
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MinovaCyanPrimary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedDuration = mins }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error banner if any
            if (generationError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = generationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearError) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Start Learning Plan Button
            Button(
                onClick = {
                    if (topicInput.isBlank()) {
                        validationError = "Please enter a topic to generate your plan."
                    } else {
                        validationError = null
                        onStartLearningPlan(
                            topicInput.trim(),
                            selectedCategory,
                            selectedLevel,
                            selectedGoal,
                            selectedLanguage,
                            selectedDuration
                        )
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("learn_home_generate_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinovaCyanPrimary,
                    contentColor = Color.Black
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Generating Personalized Curriculum...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Learning Plan",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Learning Section
            if (activeSessions.isNotEmpty()) {
                Text(
                    text = "Continue Learning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                activeSessions.forEach { session ->
                    SessionCard(
                        session = session,
                        onOpenSession = { onOpenSession(session.id, session.lastOpenedLessonIndex) },
                        onOpenQuiz = { onOpenQuiz(session.id) },
                        onDeleteSession = { onDeleteSession(session.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Completed Learning Section
            if (completedSessions.isNotEmpty()) {
                Text(
                    text = "Completed Topics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                completedSessions.forEach { session ->
                    SessionCard(
                        session = session,
                        onOpenSession = { onOpenSession(session.id, 0) },
                        onOpenQuiz = { onOpenQuiz(session.id) },
                        onDeleteSession = { onDeleteSession(session.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Empty State & Suggested Topics
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MinovaCyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No active learning plans yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Or tap a popular starter topic to start right now:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val suggestions = listOf(
                        Triple("Python Asynchronous Programming", LearningSubjectCategory.PROGRAMMING, LearningGoal.LEARN_PROGRAMMING),
                        Triple("Calculus: Intuition of Derivatives", LearningSubjectCategory.MATHEMATICS, LearningGoal.UNDERSTAND_CONCEPT),
                        Triple("Quantum Mechanics Basics", LearningSubjectCategory.SCIENCE, LearningGoal.UNDERSTAND_CONCEPT),
                        Triple("English Grammar: Active vs Passive Voice", LearningSubjectCategory.ENGLISH, LearningGoal.PRACTICE_QUESTIONS)
                    )

                    suggestions.forEach { (topic, cat, goal) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    topicInput = topic
                                    selectedCategory = cat
                                    selectedGoal = goal
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.getIcon(),
                                    contentDescription = null,
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = cat.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Select topic",
                                    tint = MinovaCyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: LearningSession,
    onOpenSession: () -> Unit,
    onOpenQuiz: () -> Unit,
    onDeleteSession: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learn_session_card_${session.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (session.isCompleted) MinovaCyanPrimary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MinovaCyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = session.subjectCategory.getIcon(),
                            contentDescription = null,
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.topic,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${session.level.displayName} • ${session.language.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDeleteSession,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (session.isCompleted) "Completed (${session.totalLessons}/${session.totalLessons} Lessons)"
                    else "${session.completedLessons} of ${session.totalLessons} lessons completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.isCompleted) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${session.progressPercentInt}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.isCompleted) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { session.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (session.isCompleted) MinovaCyanPrimary else MinovaAzureLight,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSession,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (session.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MinovaCyanPrimary,
                        contentColor = if (session.isCompleted) MaterialTheme.colorScheme.onSurface else Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (session.isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (session.isCompleted) "Review Lessons" else "Resume Lesson ${session.lastOpenedLessonIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onOpenQuiz,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MinovaIndigoSecondary.copy(alpha = 0.35f),
                        contentColor = MinovaAzureLight
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Quiz",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
