package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.LearningQuizQuestion
import com.example.data.model.QuizQuestionType
import org.json.JSONArray

@Entity(
    tableName = "learning_quiz_questions",
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
        Index(value = ["sessionId", "lessonIndex", "questionIndex"])
    ]
)
data class LearningQuizQuestionEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val lessonIndex: Int,
    val questionIndex: Int,
    val questionType: String,
    val questionText: String,
    val optionsJson: String,
    val correctAnswer: String,
    val explanation: String,
    val userAnswer: String?,
    val isAnswered: Boolean,
    val isCorrect: Boolean,
    val attemptCount: Int
) {
    fun toDomain(): LearningQuizQuestion {
        val optionsList = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(optionsJson)
            for (i in 0 until jsonArray.length()) {
                optionsList.add(jsonArray.optString(i))
            }
        } catch (_: Exception) {
            if (optionsJson.isNotBlank()) {
                optionsList.addAll(optionsJson.split("\n---opt---\n").filter { it.isNotBlank() })
            }
        }

        return LearningQuizQuestion(
            id = id,
            sessionId = sessionId,
            lessonIndex = lessonIndex,
            questionIndex = questionIndex,
            questionType = QuizQuestionType.fromString(questionType),
            questionText = questionText,
            options = optionsList,
            correctAnswer = correctAnswer,
            explanation = explanation,
            userAnswer = userAnswer,
            isAnswered = isAnswered,
            isCorrect = isCorrect,
            attemptCount = attemptCount
        )
    }

    companion object {
        fun fromDomain(domain: LearningQuizQuestion): LearningQuizQuestionEntity {
            val jsonArray = JSONArray()
            domain.options.forEach { jsonArray.put(it) }
            return LearningQuizQuestionEntity(
                id = domain.id,
                sessionId = domain.sessionId,
                lessonIndex = domain.lessonIndex,
                questionIndex = domain.questionIndex,
                questionType = domain.questionType.name,
                questionText = domain.questionText,
                optionsJson = jsonArray.toString(),
                correctAnswer = domain.correctAnswer,
                explanation = domain.explanation,
                userAnswer = domain.userAnswer,
                isAnswered = domain.isAnswered,
                isCorrect = domain.isCorrect,
                attemptCount = domain.attemptCount
            )
        }
    }
}
