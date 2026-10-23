package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.LearningGoal
import com.example.data.model.LearningLanguage
import com.example.data.model.LearningLevel
import com.example.data.model.LearningSession
import com.example.data.model.LearningSubjectCategory

@Entity(
    tableName = "learning_sessions",
    indices = [
        Index("updatedAt"),
        Index("subjectCategory")
    ]
)
data class LearningSessionEntity(
    @PrimaryKey
    val id: String,
    val topic: String,
    val subjectCategory: String,
    val level: String,
    val goal: String,
    val language: String,
    val dailyDurationMinutes: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val lastOpenedLessonIndex: Int,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): LearningSession {
        return LearningSession(
            id = id,
            topic = topic,
            subjectCategory = LearningSubjectCategory.fromId(subjectCategory),
            level = LearningLevel.fromString(level),
            goal = LearningGoal.fromString(goal),
            language = LearningLanguage.fromString(language),
            dailyDurationMinutes = dailyDurationMinutes,
            totalLessons = totalLessons,
            completedLessons = completedLessons,
            lastOpenedLessonIndex = lastOpenedLessonIndex,
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: LearningSession): LearningSessionEntity {
            return LearningSessionEntity(
                id = domain.id,
                topic = domain.topic,
                subjectCategory = domain.subjectCategory.id,
                level = domain.level.name,
                goal = domain.goal.name,
                language = domain.language.name,
                dailyDurationMinutes = domain.dailyDurationMinutes,
                totalLessons = domain.totalLessons,
                completedLessons = domain.completedLessons,
                lastOpenedLessonIndex = domain.lastOpenedLessonIndex,
                isCompleted = domain.isCompleted,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }
    }
}
