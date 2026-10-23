package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.LearningQuizAttempt

@Entity(
    tableName = "learning_quiz_attempts",
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
        Index("attemptedAt")
    ]
)
data class LearningQuizAttemptEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Int,
    val attemptedAt: Long
) {
    fun toDomain(): LearningQuizAttempt {
        return LearningQuizAttempt(
            id = id,
            sessionId = sessionId,
            score = score,
            totalQuestions = totalQuestions,
            percentage = percentage,
            attemptedAt = attemptedAt
        )
    }

    companion object {
        fun fromDomain(domain: LearningQuizAttempt): LearningQuizAttemptEntity {
            return LearningQuizAttemptEntity(
                id = domain.id,
                sessionId = domain.sessionId,
                score = domain.score,
                totalQuestions = domain.totalQuestions,
                percentage = domain.percentage,
                attemptedAt = domain.attemptedAt
            )
        }
    }
}
