package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.LearningLessonEntity
import com.example.data.local.entity.LearningQuizAttemptEntity
import com.example.data.local.entity.LearningQuizQuestionEntity
import com.example.data.local.entity.LearningSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {

    // --- Learning Sessions ---

    @Query("SELECT * FROM learning_sessions ORDER BY updatedAt DESC")
    fun observeAllSessions(): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<LearningSessionEntity?>

    @Query("SELECT * FROM learning_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): LearningSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: LearningSessionEntity)

    @Query(
        """
        UPDATE learning_sessions
        SET completedLessons = :completedLessons,
            lastOpenedLessonIndex = :lastOpenedIndex,
            isCompleted = :isCompleted,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionProgress(
        sessionId: String,
        completedLessons: Int,
        lastOpenedIndex: Int,
        isCompleted: Boolean,
        updatedAt: Long
    )

    @Query("UPDATE learning_sessions SET lastOpenedLessonIndex = :lastOpenedIndex, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateLastOpenedLesson(sessionId: String, lastOpenedIndex: Int, updatedAt: Long)

    @Query("DELETE FROM learning_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    // --- Learning Lessons ---

    @Query("SELECT * FROM learning_lessons WHERE sessionId = :sessionId ORDER BY lessonIndex ASC")
    fun observeLessonsForSession(sessionId: String): Flow<List<LearningLessonEntity>>

    @Query("SELECT * FROM learning_lessons WHERE sessionId = :sessionId ORDER BY lessonIndex ASC")
    suspend fun getLessonsForSession(sessionId: String): List<LearningLessonEntity>

    @Query("SELECT * FROM learning_lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLessonById(lessonId: String): LearningLessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessons(lessons: List<LearningLessonEntity>)

    @Query("UPDATE learning_lessons SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :lessonId")
    suspend fun updateLessonCompletion(lessonId: String, isCompleted: Boolean, updatedAt: Long)

    @Query("UPDATE learning_lessons SET isBookmarked = :isBookmarked WHERE id = :lessonId")
    suspend fun updateLessonBookmark(lessonId: String, isBookmarked: Boolean)

    // --- Learning Quiz Questions ---

    @Query("SELECT * FROM learning_quiz_questions WHERE sessionId = :sessionId ORDER BY lessonIndex ASC, questionIndex ASC")
    fun observeQuizQuestionsForSession(sessionId: String): Flow<List<LearningQuizQuestionEntity>>

    @Query("SELECT * FROM learning_quiz_questions WHERE sessionId = :sessionId ORDER BY lessonIndex ASC, questionIndex ASC")
    suspend fun getQuizQuestionsForSession(sessionId: String): List<LearningQuizQuestionEntity>

    @Query("SELECT * FROM learning_quiz_questions WHERE id = :questionId LIMIT 1")
    suspend fun getQuestionById(questionId: String): LearningQuizQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizQuestions(questions: List<LearningQuizQuestionEntity>)

    @Query(
        """
        UPDATE learning_quiz_questions
        SET userAnswer = :userAnswer,
            isAnswered = 1,
            isCorrect = :isCorrect,
            attemptCount = :attemptCount
        WHERE id = :questionId
        """
    )
    suspend fun updateQuestionAnswer(
        questionId: String,
        userAnswer: String,
        isCorrect: Boolean,
        attemptCount: Int
    )

    @Query(
        """
        UPDATE learning_quiz_questions
        SET userAnswer = NULL,
            isAnswered = 0,
            isCorrect = 0
        WHERE sessionId = :sessionId
        """
    )
    suspend fun resetQuizAnswers(sessionId: String)

    // --- Quiz Attempts ---

    @Query("SELECT * FROM learning_quiz_attempts WHERE sessionId = :sessionId ORDER BY attemptedAt DESC")
    fun observeQuizAttempts(sessionId: String): Flow<List<LearningQuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: LearningQuizAttemptEntity)

    // --- Full Transaction Save Plan ---

    @Transaction
    suspend fun saveCompleteLearningPlan(
        session: LearningSessionEntity,
        lessons: List<LearningLessonEntity>,
        questions: List<LearningQuizQuestionEntity>
    ) {
        upsertSession(session)
        upsertLessons(lessons)
        upsertQuizQuestions(questions)
    }
}
