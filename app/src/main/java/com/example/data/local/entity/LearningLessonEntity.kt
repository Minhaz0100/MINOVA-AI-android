package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.LearningLesson

@Entity(
    tableName = "learning_lessons",
    foreignKeys = [
        ForeignKey(
            entity = LearningSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "lessonIndex"])
    ]
)
data class LearningLessonEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val lessonIndex: Int,
    val title: String,
    val overview: String,
    val contentMarkdown: String,
    val keyTakeaways: String,
    val codeSnippet: String?,
    val codeLanguage: String?,
    val codeExplanation: String?,
    val expectedOutput: String?,
    val debuggingGuidance: String?,
    val practiceExercise: String?,
    val isCompleted: Boolean,
    val isBookmarked: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): LearningLesson {
        return LearningLesson(
            id = id,
            sessionId = sessionId,
            lessonIndex = lessonIndex,
            title = title,
            overview = overview,
            contentMarkdown = contentMarkdown,
            keyTakeaways = keyTakeaways,
            codeSnippet = codeSnippet,
            codeLanguage = codeLanguage,
            codeExplanation = codeExplanation,
            expectedOutput = expectedOutput,
            debuggingGuidance = debuggingGuidance,
            practiceExercise = practiceExercise,
            isCompleted = isCompleted,
            isBookmarked = isBookmarked,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: LearningLesson): LearningLessonEntity {
            return LearningLessonEntity(
                id = domain.id,
                sessionId = domain.sessionId,
                lessonIndex = domain.lessonIndex,
                title = domain.title,
                overview = domain.overview,
                contentMarkdown = domain.contentMarkdown,
                keyTakeaways = domain.keyTakeaways,
                codeSnippet = domain.codeSnippet,
                codeLanguage = domain.codeLanguage,
                codeExplanation = domain.codeExplanation,
                expectedOutput = domain.expectedOutput,
                debuggingGuidance = domain.debuggingGuidance,
                practiceExercise = domain.practiceExercise,
                isCompleted = domain.isCompleted,
                isBookmarked = domain.isBookmarked,
                updatedAt = domain.updatedAt
            )
        }
    }
}
