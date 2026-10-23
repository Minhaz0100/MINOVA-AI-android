package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Topic
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Predefined learning categories for MINOVA AI Learn Mode.
 */
enum class LearningSubjectCategory(
    val id: String,
    val displayName: String,
    val description: String
) {
    PROGRAMMING(
        id = "programming",
        displayName = "Programming & CS",
        description = "Algorithms, Kotlin, Python, Web & Data structures"
    ),
    MATHEMATICS(
        id = "math",
        displayName = "Mathematics",
        description = "Calculus, Algebra, Statistics & Geometry"
    ),
    SCIENCE(
        id = "science",
        displayName = "Science & Engineering",
        description = "Physics, Chemistry, Electronics & Systems"
    ),
    ENGLISH(
        id = "english",
        displayName = "English & Communication",
        description = "Grammar, Vocabulary, Writing & Speaking"
    ),
    GENERAL_KNOWLEDGE(
        id = "general_knowledge",
        displayName = "General Knowledge",
        description = "World history, Geography, Economics & Tech"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Topic",
        description = "Any specialized topic, skill or concept"
    );

    fun getIcon(): ImageVector = when (this) {
        PROGRAMMING -> Icons.Default.Code
        MATHEMATICS -> Icons.Default.Calculate
        SCIENCE -> Icons.Default.Science
        ENGLISH -> Icons.Default.Language
        GENERAL_KNOWLEDGE -> Icons.Default.Public
        CUSTOM -> Icons.Default.Topic
    }

    companion object {
        fun fromId(id: String): LearningSubjectCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CUSTOM
        }
    }
}

/**
 * Learner proficiency level.
 */
enum class LearningLevel(val displayName: String, val promptHint: String) {
    BEGINNER("Beginner", "Foundational concepts, plain analogies, step-by-step reasoning"),
    INTERMEDIATE("Intermediate", "Practical implementations, architectural reasoning, trade-offs"),
    ADVANCED("Advanced", "Deep mechanics, optimization, edge cases, production best practices");

    companion object {
        fun fromString(value: String): LearningLevel {
            return entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: BEGINNER
        }
    }
}

/**
 * Learning objective / goal.
 */
enum class LearningGoal(val displayName: String, val promptHint: String) {
    UNDERSTAND_CONCEPT("Understand a Concept", "Focus on clear mental models, visual analogies, and core definitions"),
    EXAM_PREP("Prepare for an Exam", "Focus on high-yield exam questions, key definitions, and quick review points"),
    LEARN_PROGRAMMING("Learn Programming", "Focus on runnable syntax, code blocks, output, common bugs, and practical exercises"),
    PRACTICE_QUESTIONS("Practice Questions", "Focus on challenging drill questions, immediate feedback, and worked explanations");

    companion object {
        fun fromString(value: String): LearningGoal {
            return entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: UNDERSTAND_CONCEPT
        }
    }
}

/**
 * Explanation language.
 */
enum class LearningLanguage(val displayName: String, val languageCode: String) {
    ENGLISH("English", "en"),
    BENGALI("Bengali", "bn");

    companion object {
        fun fromString(value: String): LearningLanguage {
            return entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) || it.languageCode.equals(value, ignoreCase = true) }
                ?: ENGLISH
        }
    }
}

/**
 * Domain model representing a structured Learning Session.
 */
data class LearningSession(
    val id: String,
    val topic: String,
    val subjectCategory: LearningSubjectCategory,
    val level: LearningLevel,
    val goal: LearningGoal,
    val language: LearningLanguage,
    val dailyDurationMinutes: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val lastOpenedLessonIndex: Int,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    val progressPercent: Float
        get() = if (totalLessons > 0) {
            (completedLessons.toFloat() / totalLessons.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt()
}

/**
 * Domain model representing an individual sequential lesson within a learning session.
 */
data class LearningLesson(
    val id: String,
    val sessionId: String,
    val lessonIndex: Int,
    val title: String,
    val overview: String,
    val contentMarkdown: String,
    val keyTakeaways: String,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val codeExplanation: String? = null,
    val expectedOutput: String? = null,
    val debuggingGuidance: String? = null,
    val practiceExercise: String? = null,
    val isCompleted: Boolean = false,
    val isBookmarked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Quiz question types.
 */
enum class QuizQuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_ANSWER;

    companion object {
        fun fromString(value: String): QuizQuestionType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MULTIPLE_CHOICE
        }
    }
}

/**
 * Domain model representing a quiz question in a learning session.
 */
data class LearningQuizQuestion(
    val id: String,
    val sessionId: String,
    val lessonIndex: Int, // -1 for final comprehensive quiz, or >= 0 for lesson-specific check
    val questionIndex: Int,
    val questionType: QuizQuestionType,
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val userAnswer: String? = null,
    val isAnswered: Boolean = false,
    val isCorrect: Boolean = false,
    val attemptCount: Int = 0
)

/**
 * Domain model for a recorded quiz attempt.
 */
data class LearningQuizAttempt(
    val id: String,
    val sessionId: String,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Int,
    val attemptedAt: Long
)
