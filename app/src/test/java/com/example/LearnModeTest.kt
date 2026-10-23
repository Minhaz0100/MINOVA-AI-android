package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.LearningGoal
import com.example.data.model.LearningLanguage
import com.example.data.model.LearningLevel
import com.example.data.model.LearningSubjectCategory
import com.example.data.model.UserProfile
import com.example.data.repository.InMemoryLearningRepository
import com.example.navigation.MinovaScreen
import com.example.ui.components.MinovaDrawerContent
import com.example.ui.screens.LearnHomeScreen
import com.example.ui.screens.LearnLessonScreen
import com.example.ui.screens.LearnQuizScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun learningRepository_generatesPlan_tracksLessonProgress_andEvaluatesQuiz() = runTest {
        val repository = InMemoryLearningRepository()

        val result = repository.generateAndSaveLearningPlan(
            topic = "Kotlin Coroutines",
            category = LearningSubjectCategory.PROGRAMMING,
            level = LearningLevel.INTERMEDIATE,
            goal = LearningGoal.LEARN_PROGRAMMING,
            language = LearningLanguage.ENGLISH,
            dailyDurationMinutes = 20
        )

        assertTrue(result.isSuccess)
        val sessionId = result.getOrNull()
        assertNotNull(sessionId)

        val session = repository.observeSession(sessionId!!).first()
        assertNotNull(session)
        assertEquals("Kotlin Coroutines", session!!.topic)
        assertEquals(LearningSubjectCategory.PROGRAMMING, session.subjectCategory)
        assertTrue(session.totalLessons >= 3)
        assertEquals(0, session.completedLessons)
        assertFalse(session.isCompleted)

        val lessons = repository.observeLessonsForSession(sessionId).first()
        assertEquals(session.totalLessons, lessons.size)
        assertNotNull(lessons.first().codeSnippet)

        // Complete first lesson
        repository.markLessonCompleted(sessionId, lessons.first().id, true)
        val updatedSession = repository.observeSession(sessionId).first()!!
        assertEquals(1, updatedSession.completedLessons)

        // Check quiz questions and answer evaluation
        val questions = repository.observeQuizQuestionsForSession(sessionId).first()
        assertTrue(questions.isNotEmpty())

        val firstQuestion = questions.first()
        val isCorrect = repository.submitQuizAnswer(firstQuestion.id, firstQuestion.correctAnswer)
        assertTrue(isCorrect)

        val attempt = repository.recordQuizAttempt(sessionId)
        assertNotNull(attempt)
        assertTrue(attempt!!.score >= 1)
    }

    @Test
    fun drawer_containsLearnModeEntry_andNavigatesToLearnHome() {
        var navigatedScreen: MinovaScreen? = null

        composeRule.setContent {
            MyApplicationTheme {
                MinovaDrawerContent(
                    conversations = emptyList(),
                    activeConversationId = null,
                    userProfile = UserProfile(name = "Minhaz", email = "minhaz@minova.ai", tier = "MINOVA PRO"),
                    onNavigate = { navigatedScreen = it },
                    onSelectConversation = {},
                    onNewChatClick = {},
                    onTogglePin = {}
                )
            }
        }

        val learnNode = composeRule.onNodeWithTag("drawer_nav_learn_mode")
        learnNode.assertIsDisplayed()
        learnNode.performClick()

        assertEquals(MinovaScreen.LearnHome, navigatedScreen)
    }

    @Test
    fun learnHomeScreen_rendersEmptyState_andStartsLearningPlan() {
        var startedTopic: String? = null

        composeRule.setContent {
            MyApplicationTheme {
                LearnHomeScreen(
                    sessions = emptyList(),
                    isGenerating = false,
                    generationError = null,
                    onBack = {},
                    onStartLearningPlan = { topic, _, _, _, _, _ ->
                        startedTopic = topic
                    },
                    onOpenSession = { _, _ -> },
                    onOpenQuiz = {},
                    onDeleteSession = {},
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("learn_home_topic_input").performTextInput("Binary Search Trees")
        composeRule.onNodeWithTag("learn_home_generate_button").performScrollTo().performClick()

        assertEquals("Binary Search Trees", startedTopic)
    }

    @Test
    fun learnLessonAndQuizScreens_renderContent_andSupportInteractions() = runTest {
        val repository = InMemoryLearningRepository()
        val sessionId = repository.generateAndSaveLearningPlan(
            topic = "Linear Algebra Basics",
            category = LearningSubjectCategory.MATHEMATICS,
            level = LearningLevel.BEGINNER,
            goal = LearningGoal.UNDERSTAND_CONCEPT,
            language = LearningLanguage.ENGLISH,
            dailyDurationMinutes = 15
        ).getOrThrow()

        val session = repository.observeSession(sessionId).first()!!
        val lessons = repository.observeLessonsForSession(sessionId).first()

        var openedQuizSessionId: String? = null

        composeRule.setContent {
            MyApplicationTheme {
                LearnLessonScreen(
                    session = session,
                    lessons = lessons,
                    currentLessonIndex = 0,
                    isTutorLoading = false,
                    tutorResponse = null,
                    tutorError = null,
                    onBackToHome = {},
                    onSelectLesson = {},
                    onMarkCompleted = { _, _ -> },
                    onToggleBookmark = { _, _ -> },
                    onOpenQuiz = { openedQuizSessionId = it },
                    onAskTutor = { _, _ -> },
                    onClearTutor = {}
                )
            }
        }

        composeRule.onNodeWithText(lessons.first().title).assertIsDisplayed()
        composeRule.onNodeWithTag("learn_lesson_top_quiz_button").performClick()
        assertEquals(sessionId, openedQuizSessionId)
    }
}
