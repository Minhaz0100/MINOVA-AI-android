package com.example.data.repository

import com.example.data.local.dao.LearningDao
import com.example.data.local.entity.LearningLessonEntity
import com.example.data.local.entity.LearningQuizAttemptEntity
import com.example.data.local.entity.LearningQuizQuestionEntity
import com.example.data.local.entity.LearningSessionEntity
import com.example.data.model.LearningGoal
import com.example.data.model.LearningLanguage
import com.example.data.model.LearningLesson
import com.example.data.model.LearningLevel
import com.example.data.model.LearningQuizAttempt
import com.example.data.model.LearningQuizQuestion
import com.example.data.model.LearningSession
import com.example.data.model.LearningSubjectCategory
import com.example.data.model.QuizQuestionType
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

interface LearningRepository {
    fun observeAllSessions(): Flow<List<LearningSession>>
    fun observeSession(sessionId: String): Flow<LearningSession?>
    fun observeLessonsForSession(sessionId: String): Flow<List<LearningLesson>>
    fun observeQuizQuestionsForSession(sessionId: String): Flow<List<LearningQuizQuestion>>
    fun observeQuizAttempts(sessionId: String): Flow<List<LearningQuizAttempt>>

    suspend fun generateAndSaveLearningPlan(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ): Result<String>

    suspend fun markLessonCompleted(sessionId: String, lessonId: String, isCompleted: Boolean)
    suspend fun toggleLessonBookmark(lessonId: String, currentBookmarked: Boolean)
    suspend fun updateLastOpenedLesson(sessionId: String, lessonIndex: Int)
    suspend fun submitQuizAnswer(questionId: String, selectedAnswer: String): Boolean
    suspend fun resetQuizAnswers(sessionId: String)
    suspend fun recordQuizAttempt(sessionId: String): LearningQuizAttempt?
    suspend fun askLessonTutor(lesson: LearningLesson, question: String): NetworkResult<String>
    suspend fun deleteSession(sessionId: String)
}

class LearningRepositoryImpl(
    private val learningDao: LearningDao,
    private val chatRepository: ChatRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LearningRepository {

    override fun observeAllSessions(): Flow<List<LearningSession>> {
        return learningDao.observeAllSessions().map { list -> list.map { it.toDomain() } }
    }

    override fun observeSession(sessionId: String): Flow<LearningSession?> {
        return learningDao.observeSessionById(sessionId).map { it?.toDomain() }
    }

    override fun observeLessonsForSession(sessionId: String): Flow<List<LearningLesson>> {
        return learningDao.observeLessonsForSession(sessionId).map { list -> list.map { it.toDomain() } }
    }

    override fun observeQuizQuestionsForSession(sessionId: String): Flow<List<LearningQuizQuestion>> {
        return learningDao.observeQuizQuestionsForSession(sessionId).map { list -> list.map { it.toDomain() } }
    }

    override fun observeQuizAttempts(sessionId: String): Flow<List<LearningQuizAttempt>> {
        return learningDao.observeQuizAttempts(sessionId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun generateAndSaveLearningPlan(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ): Result<String> = withContext(ioDispatcher) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a topic to start learning."))
        }

        val sessionId = "session_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        // 1. Prompt the AI for structured curriculum
        val prompt = buildCurriculumPrompt(cleanTopic, category, level, goal, language, dailyDurationMinutes)
        val networkResult = chatRepository.sendChatMessage(
            message = prompt,
            conversationHistory = emptyList(),
            responseStyle = ResponseStyle.EDUCATIONAL,
            responseLanguage = if (language == LearningLanguage.BENGALI) ResponseLanguage.BENGALI else ResponseLanguage.ENGLISH,
            responseFormat = ResponseFormat.AUTO
        )

        val planSpec: CurriculumPlanSpec = when (networkResult) {
            is NetworkResult.Success -> {
                parseCurriculumJson(networkResult.data, cleanTopic, category, level, goal, language, dailyDurationMinutes)
                    ?: generateCuratedPlan(cleanTopic, category, level, goal, language, dailyDurationMinutes)
            }
            is NetworkResult.Error -> {
                // Safe, rich offline/curated fallback curriculum ensuring 0 dead ends
                generateCuratedPlan(cleanTopic, category, level, goal, language, dailyDurationMinutes)
            }
        }

        // 2. Map to Entities
        val sessionEntity = LearningSessionEntity(
            id = sessionId,
            topic = cleanTopic,
            subjectCategory = category.id,
            level = level.name,
            goal = goal.name,
            language = language.name,
            dailyDurationMinutes = dailyDurationMinutes,
            totalLessons = planSpec.lessons.size,
            completedLessons = 0,
            lastOpenedLessonIndex = 0,
            isCompleted = false,
            createdAt = now,
            updatedAt = now
        )

        val lessonEntities = planSpec.lessons.mapIndexed { idx, item ->
            LearningLessonEntity(
                id = "${sessionId}_lesson_$idx",
                sessionId = sessionId,
                lessonIndex = idx,
                title = item.title,
                overview = item.overview,
                contentMarkdown = item.contentMarkdown,
                keyTakeaways = item.keyTakeaways,
                codeSnippet = item.codeSnippet,
                codeLanguage = item.codeLanguage,
                codeExplanation = item.codeExplanation,
                expectedOutput = item.expectedOutput,
                debuggingGuidance = item.debuggingGuidance,
                practiceExercise = item.practiceExercise,
                isCompleted = false,
                isBookmarked = false,
                updatedAt = now
            )
        }

        val questionEntities = planSpec.questions.mapIndexed { idx, q ->
            val jsonOptions = JSONArray()
            q.options.forEach { jsonOptions.put(it) }
            LearningQuizQuestionEntity(
                id = "${sessionId}_q_$idx",
                sessionId = sessionId,
                lessonIndex = q.lessonIndex,
                questionIndex = idx,
                questionType = q.questionType.name,
                questionText = q.questionText,
                optionsJson = jsonOptions.toString(),
                correctAnswer = q.correctAnswer,
                explanation = q.explanation,
                userAnswer = null,
                isAnswered = false,
                isCorrect = false,
                attemptCount = 0
            )
        }

        // 3. Save into Room transactionally
        learningDao.saveCompleteLearningPlan(sessionEntity, lessonEntities, questionEntities)
        Result.success(sessionId)
    }

    override suspend fun markLessonCompleted(sessionId: String, lessonId: String, isCompleted: Boolean) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        learningDao.updateLessonCompletion(lessonId, isCompleted, now)

        val lessons = learningDao.getLessonsForSession(sessionId)
        val completedCount = lessons.count { if (it.id == lessonId) isCompleted else it.isCompleted }
        val allCompleted = completedCount == lessons.size && lessons.isNotEmpty()
        val currentSession = learningDao.getSessionById(sessionId)
        val lastOpened = currentSession?.lastOpenedLessonIndex ?: 0

        learningDao.updateSessionProgress(
            sessionId = sessionId,
            completedLessons = completedCount,
            lastOpenedIndex = lastOpened,
            isCompleted = allCompleted,
            updatedAt = now
        )
    }

    override suspend fun toggleLessonBookmark(lessonId: String, currentBookmarked: Boolean) = withContext(ioDispatcher) {
        learningDao.updateLessonBookmark(lessonId, !currentBookmarked)
    }

    override suspend fun updateLastOpenedLesson(sessionId: String, lessonIndex: Int) = withContext(ioDispatcher) {
        learningDao.updateLastOpenedLesson(sessionId, lessonIndex, System.currentTimeMillis())
    }

    override suspend fun submitQuizAnswer(questionId: String, selectedAnswer: String): Boolean = withContext(ioDispatcher) {
        val question = learningDao.getQuestionById(questionId) ?: return@withContext false
        val cleanAnswer = selectedAnswer.trim()
        val isCorrect = evaluateAnswer(question.questionType, cleanAnswer, question.correctAnswer)
        val newAttemptCount = question.attemptCount + 1

        learningDao.updateQuestionAnswer(
            questionId = questionId,
            userAnswer = cleanAnswer,
            isCorrect = isCorrect,
            attemptCount = newAttemptCount
        )
        isCorrect
    }

    override suspend fun resetQuizAnswers(sessionId: String) = withContext(ioDispatcher) {
        learningDao.resetQuizAnswers(sessionId)
    }

    override suspend fun recordQuizAttempt(sessionId: String): LearningQuizAttempt? = withContext(ioDispatcher) {
        val questions = learningDao.getQuizQuestionsForSession(sessionId)
        if (questions.isEmpty()) return@withContext null

        val correctCount = questions.count { it.isCorrect }
        val total = questions.size
        val percentage = ((correctCount.toFloat() / total.toFloat()) * 100).toInt()
        val attemptId = "attempt_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        val entity = LearningQuizAttemptEntity(
            id = attemptId,
            sessionId = sessionId,
            score = correctCount,
            totalQuestions = total,
            percentage = percentage,
            attemptedAt = now
        )
        learningDao.insertQuizAttempt(entity)
        entity.toDomain()
    }

    override suspend fun askLessonTutor(lesson: LearningLesson, question: String): NetworkResult<String> = withContext(ioDispatcher) {
        val tutorPrompt = """
            You are MINOVA AI Learn Mode Tutor. The user is currently studying the lesson:
            "${lesson.title}"

            Lesson Overview:
            ${lesson.overview}

            Lesson Content:
            ${lesson.contentMarkdown.take(1500)}

            ${if (!lesson.codeSnippet.isNullOrBlank()) "Code Example:\n```${lesson.codeLanguage ?: ""}\n${lesson.codeSnippet}\n```" else ""}

            Student's Question:
            "$question"

            Instruction:
            Explain directly, patiently, and clearly in the context of this specific lesson. Provide clear analogies, explain any confusing code or terms, and encourage the student. Keep it conversational, helpful, and concise.
        """.trimIndent()

        chatRepository.sendChatMessage(
            message = tutorPrompt,
            conversationHistory = emptyList(),
            responseStyle = ResponseStyle.BALANCED,
            responseLanguage = ResponseLanguage.AUTO,
            responseFormat = ResponseFormat.AUTO
        )
    }

    override suspend fun deleteSession(sessionId: String) = withContext(ioDispatcher) {
        learningDao.deleteSessionById(sessionId)
    }

    private fun evaluateAnswer(questionType: String, userAnswer: String, correctAnswer: String): Boolean {
        return when (questionType.uppercase()) {
            "MULTIPLE_CHOICE" -> {
                val cleanUser = userAnswer.trim()
                val cleanCorrect = correctAnswer.trim()
                cleanUser.equals(cleanCorrect, ignoreCase = true) ||
                    (cleanUser.length == 1 && cleanCorrect.startsWith(cleanUser, ignoreCase = true)) ||
                    (cleanCorrect.length == 1 && cleanUser.startsWith(cleanCorrect, ignoreCase = true))
            }
            "TRUE_FALSE" -> {
                userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
            }
            "SHORT_ANSWER" -> {
                val cleanUser = userAnswer.trim().lowercase()
                val cleanCorrect = correctAnswer.trim().lowercase()
                cleanUser == cleanCorrect ||
                    cleanUser.contains(cleanCorrect) ||
                    cleanCorrect.contains(cleanUser) ||
                    cleanCorrect.split(" ", ",", ";").any { it.isNotBlank() && cleanUser.contains(it) }
            }
            else -> userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
        }
    }

    private fun buildCurriculumPrompt(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        durationMinutes: Int
    ): String {
        val languageInstruction = if (language == LearningLanguage.BENGALI) {
            "Explain all lessons, code comments, and questions in fluent, natural Bengali (Bangla)."
        } else {
            "Explain all lessons and questions in clear, standard English."
        }

        return """
            You are MINOVA AI Learn Mode, an elite personalized AI tutor.
            Generate a complete, structured, sequential learning curriculum for:
            Topic: "$topic"
            Subject Category: ${category.displayName}
            Proficiency Level: ${level.displayName} (${level.promptHint})
            Learning Goal: ${goal.displayName} (${goal.promptHint})
            Daily Study Time: $durationMinutes minutes.
            Language: $languageInstruction

            Provide 4 to 5 sequential lessons, moving step-by-step from foundational concepts to advanced practical mastery.
            For programming topics, include code snippets, explanations, expected outputs, common bugs to avoid, and small exercises.
            Also provide 4 to 6 quiz questions (multiple choice, true/false, and short answer) to evaluate mastery.

            IMPORTANT: You MUST respond ONLY with a raw valid JSON object (no introductory text or conversational markdown before or after). Format:
            {
              "topicTitle": "$topic",
              "overview": "Summary of what the student will master",
              "lessons": [
                {
                  "title": "Lesson 1: Title",
                  "overview": "Short 1-sentence intro",
                  "contentMarkdown": "Full lesson content in markdown with headings, bold terms, bullet points, and clear real-world analogies",
                  "keyTakeaways": "Bullet points of the 2-3 most critical points",
                  "codeSnippet": "Runnable sample code (or empty string if non-coding)",
                  "codeLanguage": "kotlin/python/javascript/etc",
                  "codeExplanation": "Step by step breakdown of how the code works",
                  "expectedOutput": "Expected output if executed",
                  "debuggingGuidance": "Common mistakes and how to fix them",
                  "practiceExercise": "Mini exercise for the student to practice"
                }
              ],
              "quiz": [
                {
                  "questionText": "Question description",
                  "questionType": "MULTIPLE_CHOICE",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctAnswer": "Option A",
                  "explanation": "Why this answer is correct"
                },
                {
                  "questionText": "Is statement X true?",
                  "questionType": "TRUE_FALSE",
                  "options": ["True", "False"],
                  "correctAnswer": "True",
                  "explanation": "Explanation here"
                }
              ]
            }
        """.trimIndent()
    }

    private fun parseCurriculumJson(
        rawJson: String,
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ): CurriculumPlanSpec? {
        try {
            val jsonText = extractJsonBlock(rawJson) ?: rawJson
            val root = JSONObject(jsonText)
            val lessonsArray = root.optJSONArray("lessons") ?: return null
            if (lessonsArray.length() == 0) return null

            val lessons = mutableListOf<CurriculumLessonSpec>()
            for (i in 0 until lessonsArray.length()) {
                val obj = lessonsArray.optJSONObject(i) ?: continue
                lessons.add(
                    CurriculumLessonSpec(
                        title = obj.optString("title", "Lesson ${i + 1}"),
                        overview = obj.optString("overview", "Overview of lesson ${i + 1}"),
                        contentMarkdown = obj.optString("contentMarkdown", obj.optString("content", "")),
                        keyTakeaways = obj.optString("keyTakeaways", ""),
                        codeSnippet = obj.optString("codeSnippet").takeIf { it.isNotBlank() },
                        codeLanguage = obj.optString("codeLanguage").takeIf { it.isNotBlank() },
                        codeExplanation = obj.optString("codeExplanation").takeIf { it.isNotBlank() },
                        expectedOutput = obj.optString("expectedOutput").takeIf { it.isNotBlank() },
                        debuggingGuidance = obj.optString("debuggingGuidance").takeIf { it.isNotBlank() },
                        practiceExercise = obj.optString("practiceExercise").takeIf { it.isNotBlank() }
                    )
                )
            }

            val quizArray = root.optJSONArray("quiz")
            val questions = mutableListOf<CurriculumQuestionSpec>()
            if (quizArray != null) {
                for (i in 0 until quizArray.length()) {
                    val qObj = quizArray.optJSONObject(i) ?: continue
                    val typeStr = qObj.optString("questionType", qObj.optString("type", "MULTIPLE_CHOICE"))
                    val qType = QuizQuestionType.fromString(typeStr)
                    val optsJson = qObj.optJSONArray("options")
                    val opts = mutableListOf<String>()
                    if (optsJson != null) {
                        for (j in 0 until optsJson.length()) {
                            opts.add(optsJson.optString(j))
                        }
                    }
                    if (opts.isEmpty() && qType == QuizQuestionType.TRUE_FALSE) {
                        opts.addAll(listOf("True", "False"))
                    }

                    questions.add(
                        CurriculumQuestionSpec(
                            lessonIndex = -1,
                            questionText = qObj.optString("questionText", qObj.optString("question", "Question ${i + 1}")),
                            questionType = qType,
                            options = opts,
                            correctAnswer = qObj.optString("correctAnswer", opts.firstOrNull() ?: "True"),
                            explanation = qObj.optString("explanation", "Correct answer is evaluated based on the lesson curriculum.")
                        )
                    )
                }
            }

            if (questions.isEmpty()) {
                questions.addAll(generateDefaultQuestions(topic, lessons))
            }

            return CurriculumPlanSpec(lessons = lessons, questions = questions)
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else {
            null
        }
    }

    private fun generateDefaultQuestions(topic: String, lessons: List<CurriculumLessonSpec>): List<CurriculumQuestionSpec> {
        val list = mutableListOf<CurriculumQuestionSpec>()
        lessons.take(3).forEachIndexed { index, lesson ->
            list.add(
                CurriculumQuestionSpec(
                    lessonIndex = index,
                    questionText = "What is the primary objective covered in \"${lesson.title}\"?",
                    questionType = QuizQuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        lesson.overview,
                        "Ignore foundational rules and jump directly to complex tasks without testing",
                        "Avoid modularizing code or verifying outputs",
                        "Replace analytical thinking with unverified assumptions"
                    ),
                    correctAnswer = lesson.overview,
                    explanation = "As covered in ${lesson.title}, the foundational focus is: ${lesson.overview}"
                )
            )
        }
        list.add(
            CurriculumQuestionSpec(
                lessonIndex = -1,
                questionText = "Understanding foundational concepts before applying advanced optimizations leads to better retention and fewer errors in $topic.",
                questionType = QuizQuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "A solid mental model allows learners to reason through edge cases and debug issues systematically."
            )
        )
        return list
    }

    private fun generateCuratedPlan(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        durationMinutes: Int
    ): CurriculumPlanSpec {
        val isBengali = language == LearningLanguage.BENGALI
        val isProgramming = category == LearningSubjectCategory.PROGRAMMING ||
            goal == LearningGoal.LEARN_PROGRAMMING ||
            topic.contains("python", ignoreCase = true) ||
            topic.contains("kotlin", ignoreCase = true) ||
            topic.contains("code", ignoreCase = true)

        val lessons = if (isBengali) {
            listOf(
                CurriculumLessonSpec(
                    title = "১. $topic এর মৌলিক ধারণা ও ভূমিকা",
                    overview = "$topic এর প্রাথমিক ধারণা এবং কেন এটি গুরুত্বপূর্ণ তা বোঝা।",
                    contentMarkdown = """
                        # $topic এর পরিচিতি

                        **$topic** হলো একটি অত্যন্ত গুরুত্বপূর্ণ বিষয় যা বাস্তব জীবনে এবং পেশাগত ক্ষেত্রে দারুণভাবে কাজে আসে।

                        ### মূল বিষয়বস্তু:
                        - **সংজ্ঞা**: $topic এর মূল অর্থ এবং এর পরিধি।
                        - **বাস্তব প্রয়োগ**: এটি কীভাবে দৈনন্দিন কাজ এবং আধুনিক প্রযুক্তিতে ব্যবহৃত হয়।
                        - **মানসিক মডেল**: বিষয়টিকে সহজে বোঝার জন্য বাস্তব জীবনের উপমা।

                        > মনে রাখবেন: যেকোনো কঠিন বিষয়কে ছোট ছোট অংশে ভাগ করে নিলে তা শেখা অনেক সহজ হয়ে যায়।
                    """.trimIndent(),
                    keyTakeaways = "• মৌলিক ধারণার স্পষ্টতা থাকলে পরবর্তী পাঠগুলো সহজে বোঝা যায়।\n• বাস্তব জীবনের উদাহরণের সাথে মিলিয়ে পড়লে স্মৃতিতে দীর্ঘস্থায়ী হয়।",
                    codeSnippet = if (isProgramming) "fun main() {\n    println(\"Welcome to learning $topic with MINOVA AI!\")\n}" else null,
                    codeLanguage = if (isProgramming) "kotlin" else null,
                    codeExplanation = if (isProgramming) "এই কোডটি স্ক্রিনে একটি স্বাগত বার্তা প্রিন্ট করে।" else null,
                    expectedOutput = if (isProgramming) "Welcome to learning $topic with MINOVA AI!" else null,
                    debuggingGuidance = if (isProgramming) "বানান বা ব্র্যাকেটের অমিল এড়িয়ে চলুন।" else null,
                    practiceExercise = "আজকের পাঠ থেকে আপনি কী শিখলেন তা নিজের ভাষায় ৩টি বাক্যে লিখে রাখুন।"
                ),
                CurriculumLessonSpec(
                    title = "২. গভীর বিশ্লেষণ ও বাস্তব উদাহরণ",
                    overview = "$topic এর অভ্যন্তরীণ কার্যপদ্ধতি ও বিশদ নিয়মাবলী।",
                    contentMarkdown = """
                        # মূল কার্যপদ্ধতি ও বিশ্লেষণ

                        এই ধাপে আমরা **$topic** এর ভেতরের মূল প্রক্রিয়াগুলো নিয়ে আলোচনা করব।

                        ### গুরুত্বপূর্ণ ধাপসমূহ:
                        1. **প্রথম ধাপ**: মূল ইনপুট বা সমস্যাটি চিহ্নিত করা।
                        2. **দ্বিতীয় ধাপ**: নিয়মমাফিক সমাধান বা বিশ্লেষণ প্রয়োগ করা।
                        3. **তৃতীয় ধাপ**: ফলাফল যাচাই ও মূল্যায়ন করা।
                    """.trimIndent(),
                    keyTakeaways = "• প্রতিটি প্রক্রিয়ার পেছনের যৌক্তিক কারণ বুঝতে হবে।\n• ভুলগুলো থেকে শেখাই দক্ষতার প্রথম ধাপ।",
                    practiceExercise = "একটি ছোট উদাহরণ নিজে তৈরি করুন এবং ধাপগুলো পর্যবেক্ষণ করুন।"
                ),
                CurriculumLessonSpec(
                    title = "৩. সমস্যা সমাধান ও সাধারণ ভুলত্রুটি",
                    overview = "সাধারণ ভুলগুলো কীভাবে এড়িয়ে চলা যায় এবং সমস্যার সমাধান করা যায়।",
                    contentMarkdown = """
                        # সাধারণ ভুল ও সমাধানের কৌশল

                        শিক্ষার্থীরা প্রায়শই যেসব ভুলের সম্মুখীন হন:
                        - তাড়াহুড়ো করে অনুমান করা।
                        - মূল নীতি উপেক্ষা করা।

                        ### সমাধানের সর্বোত্তম অনুশীলন:
                        - ধাপে ধাপে যাচাই করা।
                        - পরিষ্কার নোট রাখা ও নিয়মিত অনুশীলন করা।
                    """.trimIndent(),
                    keyTakeaways = "• ভুল এড়িয়ে চলতে সতর্ক পর্যবেক্ষণ প্রয়োজন।\n• নিয়মিত পুনরাবৃত্তিই সাফল্যের চাবিকাঠি।",
                    practiceExercise = "একটি সম্ভাব্য ভুল চিন্তা করুন এবং তা সংশোধন করার উপায় নির্ধারণ করুন।"
                ),
                CurriculumLessonSpec(
                    title = "৪. সমাপনী পর্যালোচনা ও ভবিষ্যৎ পরিকল্পনা",
                    overview = "পুরো কোর্সের সংক্ষিপ্ত পুনরাবৃত্তি এবং পরবর্তী ধাপ।",
                    contentMarkdown = """
                        # অর্জিত দক্ষতা ও ভবিষ্যৎ পথচলা

                        অভিনন্দন! আপনি **$topic** এর মূল পাঠগুলো সফলভাবে সম্পন্ন করেছেন।

                        ### আপনার ভবিষ্যৎ করণীয়:
                        - বাস্তব প্রোজেক্টে এই জ্ঞান প্রয়োগ করুন।
                        - কুইজে অংশ নিয়ে নিজের বোঝাপড়া মূল্যায়ন করুন।
                    """.trimIndent(),
                    keyTakeaways = "• নিয়মিত অনুশীলন অর্জিত জ্ঞানকে পোক্ত করে।\n• নতুন চ্যালেঞ্জ নেওয়ার জন্য প্রস্তুত থাকুন।",
                    practiceExercise = "কুইজে অংশ নিয়ে আপনার অর্জিত স্কোর যাচাই করুন।"
                )
            )
        } else {
            listOf(
                CurriculumLessonSpec(
                    title = "1. Foundations & Mental Models of $topic",
                    overview = "Mastering the core principles, terminology, and foundational intuition of $topic.",
                    contentMarkdown = """
                        # Understanding $topic

                        Welcome to your personalized **MINOVA AI Learn Mode** session on **$topic**.

                        ### Core Intuition:
                        - **Definition**: The fundamental conceptual framework that governs $topic.
                        - **Why it matters**: How this concept directly applies to real-world problem solving.
                        - **The Big Picture**: Breaking the concept down into modular, approachable components.

                        ### Key Principles:
                        1. **Clarity over Complexity**: Start with direct, verifiable facts before adding layers.
                        2. **Pattern Recognition**: Identify recurring motifs and structural mechanics.
                        3. **Immediate Application**: Test your comprehension after every single concept.
                    """.trimIndent(),
                    keyTakeaways = "• Strong fundamentals prevent confusion in advanced topics.\n• Relating new ideas to familiar concepts accelerates learning.\n• Active testing yields significantly higher retention than passive reading.",
                    codeSnippet = if (isProgramming) """
                        // Entry point demonstration for $topic
                        fun demonstrateFoundations() {
                            val topic = "$topic"
                            println("Exploring: " + topic)
                        }
                    """.trimIndent() else null,
                    codeLanguage = if (isProgramming) "kotlin" else null,
                    codeExplanation = if (isProgramming) "Demonstrates basic initialization, variable declaration, and output formatting." else null,
                    expectedOutput = if (isProgramming) "Exploring: $topic" else null,
                    debuggingGuidance = if (isProgramming) "Ensure correct variable scope and avoid uninitialized references." else null,
                    practiceExercise = "Summarize the primary purpose of $topic in your own words in 2-3 concise sentences."
                ),
                CurriculumLessonSpec(
                    title = "2. Mechanics, Structure & Deep Dive",
                    overview = "Examining how the components interact, execute, and deliver results.",
                    contentMarkdown = """
                        # Deep Dive into $topic

                        Now that the foundational concept is clear, let's explore how the inner mechanisms execute.

                        ### Step-by-Step Mechanism:
                        1. **Input / State**: Establishing initial conditions and constraints.
                        2. **Transformation / Logic**: The core algorithm or reasoning process applied to the data.
                        3. **Output / Verification**: Ensuring correctness and validating against expectations.

                        ### Practical Insight:
                        Always trace the flow of information systematically. When diagnosing issues, isolate variables one by one.
                    """.trimIndent(),
                    keyTakeaways = "• Systematic reasoning beats guesswork every time.\n• Trace inputs, transformations, and outputs to understand edge cases.\n• Build modular components that each handle a single responsibility.",
                    codeSnippet = if (isProgramming) """
                        fun processItem(input: String): String {
                            require(input.isNotBlank()) { "Input must not be empty" }
                            return input.trim().uppercase()
                        }
                    """.trimIndent() else null,
                    codeLanguage = if (isProgramming) "kotlin" else null,
                    codeExplanation = if (isProgramming) "Demonstrates defensive validation with require() and clean functional transformation." else null,
                    expectedOutput = if (isProgramming) "PROCESSITEM(\"test\") -> \"TEST\"" else null,
                    debuggingGuidance = if (isProgramming) "Always guard against null and blank values before processing." else null,
                    practiceExercise = "Formulate a concrete example where this step-by-step logic applies to your daily work or studies."
                ),
                CurriculumLessonSpec(
                    title = "3. Common Pitfalls, Debugging & Best Practices",
                    overview = "Learning how to identify edge cases, fix mistakes, and write production-grade solutions.",
                    contentMarkdown = """
                        # Avoiding Mistakes & Debugging

                        Most mistakes in **$topic** stem from a few predictable oversights:

                        ### The Top 3 Common Pitfalls:
                        - **Premature Assumptions**: Assuming inputs or conditions are always ideal.
                        - **Ignoring Edge Cases**: Forgetting empty states, boundary values, or timeout scenarios.
                        - **Overcomplicating the Solution**: Introducing unnecessary dependencies or abstractions.

                        ### Defensive Best Practices:
                        - Test edge cases first (empty, zero, maximum boundary).
                        - Keep error messages helpful, descriptive, and actionable.
                        - Verify state changes deterministically.
                    """.trimIndent(),
                    keyTakeaways = "• Anticipating failure modes makes systems robust and reliable.\n• Write clear diagnostic feedback instead of silent failures.\n• Simplicity is the prerequisite for reliability.",
                    practiceExercise = "Write down 2 potential edge cases for $topic and how you would protect against them."
                ),
                CurriculumLessonSpec(
                    title = "4. Synthesis, Practical Application & Next Steps",
                    overview = "Reviewing all concepts, executing the comprehensive quiz, and planning continuous learning.",
                    contentMarkdown = """
                        # Synthesis & Mastery

                        Congratulations! You have completed all foundational and practical lessons on **$topic**.

                        ### Your Learning Checklist:
                        - [x] Foundational definition and intuition
                        - [x] Structural mechanics and transformations
                        - [x] Pitfalls, debugging, and edge cases
                        - [ ] Test your knowledge with the final interactive quiz!

                        ### Next Steps:
                        Tap **Practice & Quiz** below to evaluate your understanding, review explanations, and cement your mastery.
                    """.trimIndent(),
                    keyTakeaways = "• True mastery comes from consistent retrieval practice.\n• Revisit bookmarked lessons whenever you need a quick refresher.\n• Keep exploring related topics with MINOVA AI Learn Mode!",
                    practiceExercise = "Take the final quiz to earn your 100% completion badge."
                )
            )
        }

        val questions = if (isBengali) {
            listOf(
                CurriculumQuestionSpec(
                    lessonIndex = 0,
                    questionText = "$topic শেখার ক্ষেত্রে কোন বিষয়টি সবচেয়ে গুরুত্বপূর্ণ?",
                    questionType = QuizQuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        "মৌলিক ধারণা স্পষ্টভাবে বোঝা এবং নিয়মমাফিক অনুশীলন করা",
                        "শুধু মুখস্থ করে পরীক্ষা দেওয়া",
                        "কোনো বাস্তব উদাহরণ না দেখে সরাসরি এগিয়ে যাওয়া",
                        "ভুল হলে তা এড়িয়ে চলা"
                    ),
                    correctAnswer = "মৌলিক ধারণা স্পষ্টভাবে বোঝা এবং নিয়মমাফিক অনুশীলন করা",
                    explanation = "মৌলিক বিষয়বস্তু স্পষ্টভাবে বুঝে ধাপে ধাপে অনুশীলন করলে দীর্ঘস্থায়ী দক্ষতা তৈরি হয়।"
                ),
                CurriculumQuestionSpec(
                    lessonIndex = 1,
                    questionText = "সমস্যা সমাধানের সময় ইনপুট এবং আউটপুট আগে থেকেই যাচাই করা প্রয়োজন।",
                    questionType = QuizQuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "সঠিক ইনপুট এবং প্রত্যাশিত ফলাফল জানা থাকলে সমাধান সঠিক হয়।"
                ),
                CurriculumQuestionSpec(
                    lessonIndex = 2,
                    questionText = "$topic এ সাধারণ ভুল এড়াতে কী করা উচিত?",
                    questionType = QuizQuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        "ধাপে ধাপে যাচাই করা ও পরিষ্কার নোট রাখা",
                        "তাড়াহুড়ো করে অনুমান করা",
                        "ভুলত্রুটি চিহ্নিত না করা",
                        "সবসময় জটিল পদ্ধতি ব্যবহার করা"
                    ),
                    correctAnswer = "ধাপে ধাপে যাচাই করা ও পরিষ্কার নোট রাখা",
                    explanation = "সতর্ক পর্যবেক্ষণ এবং ধাপে ধাপে যাচাই ভুল হওয়ার সম্ভাবনা কমিয়ে দেয়।"
                )
            )
        } else {
            listOf(
                CurriculumQuestionSpec(
                    lessonIndex = 0,
                    questionText = "What is the most effective approach when starting to learn $topic?",
                    questionType = QuizQuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        "Build clear mental models and verify foundational concepts before tackling advanced complexity",
                        "Memorize syntax or formulas without understanding the underlying intuition",
                        "Skip fundamentals directly to production optimization",
                        "Avoid testing or writing practical exercises"
                    ),
                    correctAnswer = "Build clear mental models and verify foundational concepts before tackling advanced complexity",
                    explanation = "A solid conceptual foundation allows learners to reason through edge cases and debug issues systematically."
                ),
                CurriculumQuestionSpec(
                    lessonIndex = 1,
                    questionText = "Defensive validation (checking boundaries and constraints before processing) prevents runtime errors in $topic.",
                    questionType = QuizQuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Guarding against invalid states early prevents silent failures and makes systems predictable."
                ),
                CurriculumQuestionSpec(
                    lessonIndex = 2,
                    questionText = "Which practice is recommended when debugging issues in $topic?",
                    questionType = QuizQuestionType.MULTIPLE_CHOICE,
                    options = listOf(
                        "Isolate variables systematically and test edge cases one by one",
                        "Change multiple unrelated parameters at once",
                        "Ignore error outputs and retry blindly",
                        "Add complex abstractions without diagnosing root causes"
                    ),
                    correctAnswer = "Isolate variables systematically and test edge cases one by one",
                    explanation = "Systematic isolation ensures you discover the exact root cause of the behavior."
                ),
                CurriculumQuestionSpec(
                    lessonIndex = -1,
                    questionText = "Active retrieval practice and self-testing produce higher retention than passive reading.",
                    questionType = QuizQuestionType.TRUE_FALSE,
                    options = listOf("True", "False"),
                    correctAnswer = "True",
                    explanation = "Cognitive science research demonstrates that testing strengthens neural memory retrieval pathways."
                )
            )
        }

        return CurriculumPlanSpec(lessons = lessons, questions = questions)
    }
}

data class CurriculumPlanSpec(
    val lessons: List<CurriculumLessonSpec>,
    val questions: List<CurriculumQuestionSpec>
)

data class CurriculumLessonSpec(
    val title: String,
    val overview: String,
    val contentMarkdown: String,
    val keyTakeaways: String,
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val codeExplanation: String? = null,
    val expectedOutput: String? = null,
    val debuggingGuidance: String? = null,
    val practiceExercise: String? = null
)

data class CurriculumQuestionSpec(
    val lessonIndex: Int,
    val questionText: String,
    val questionType: QuizQuestionType,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

/**
 * In-memory test implementation of [LearningRepository] for isolated tests and previews.
 */
class InMemoryLearningRepository : LearningRepository {
    private val sessions = MutableStateFlow<List<LearningSession>>(emptyList())
    private val lessons = MutableStateFlow<Map<String, List<LearningLesson>>>(emptyMap())
    private val questions = MutableStateFlow<Map<String, List<LearningQuizQuestion>>>(emptyMap())
    private val attempts = MutableStateFlow<Map<String, List<LearningQuizAttempt>>>(emptyMap())

    override fun observeAllSessions(): Flow<List<LearningSession>> = sessions.asStateFlow()

    override fun observeSession(sessionId: String): Flow<LearningSession?> =
        sessions.map { list -> list.firstOrNull { it.id == sessionId } }

    override fun observeLessonsForSession(sessionId: String): Flow<List<LearningLesson>> =
        lessons.map { it[sessionId] ?: emptyList() }

    override fun observeQuizQuestionsForSession(sessionId: String): Flow<List<LearningQuizQuestion>> =
        questions.map { it[sessionId] ?: emptyList() }

    override fun observeQuizAttempts(sessionId: String): Flow<List<LearningQuizAttempt>> =
        attempts.map { it[sessionId] ?: emptyList() }

    override suspend fun generateAndSaveLearningPlan(
        topic: String,
        category: LearningSubjectCategory,
        level: LearningLevel,
        goal: LearningGoal,
        language: LearningLanguage,
        dailyDurationMinutes: Int
    ): Result<String> {
        val sessionId = "test_session_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val session = LearningSession(
            id = sessionId,
            topic = topic,
            subjectCategory = category,
            level = level,
            goal = goal,
            language = language,
            dailyDurationMinutes = dailyDurationMinutes,
            totalLessons = 3,
            completedLessons = 0,
            lastOpenedLessonIndex = 0,
            isCompleted = false,
            createdAt = now,
            updatedAt = now
        )
        val lessonList = listOf(
            LearningLesson(
                id = "${sessionId}_l_0",
                sessionId = sessionId,
                lessonIndex = 0,
                title = "Lesson 1: Introduction to $topic",
                overview = "Core basics of $topic",
                contentMarkdown = "# $topic Basics\nFoundations.",
                keyTakeaways = "Takeaways",
                codeSnippet = "fun main() {\n    println(\"Learning $topic\")\n}",
                codeLanguage = "kotlin",
                codeExplanation = "Prints a greeting for $topic",
                expectedOutput = "Learning $topic",
                debuggingGuidance = "Ensure main is defined.",
                practiceExercise = "Modify the message."
            ),
            LearningLesson(
                id = "${sessionId}_l_1",
                sessionId = sessionId,
                lessonIndex = 1,
                title = "Lesson 2: Core Mechanics of $topic",
                overview = "Deep dive into $topic",
                contentMarkdown = "# Core Mechanics\nDetailed walkthrough.",
                keyTakeaways = "Core concepts mastered"
            ),
            LearningLesson(
                id = "${sessionId}_l_2",
                sessionId = sessionId,
                lessonIndex = 2,
                title = "Lesson 3: Practical Applications of $topic",
                overview = "Applying $topic in real-world scenarios",
                contentMarkdown = "# Real-World Usage\nBest practices.",
                keyTakeaways = "Ready for production"
            )
        )
        val questionList = listOf(
            LearningQuizQuestion(
                id = "${sessionId}_q_0",
                sessionId = sessionId,
                lessonIndex = 0,
                questionIndex = 0,
                questionType = QuizQuestionType.MULTIPLE_CHOICE,
                questionText = "Is $topic understood?",
                options = listOf("Yes", "No"),
                correctAnswer = "Yes",
                explanation = "Yes is correct"
            )
        )

        sessions.value = listOf(session) + sessions.value
        lessons.value = lessons.value + (sessionId to lessonList)
        questions.value = questions.value + (sessionId to questionList)
        return Result.success(sessionId)
    }

    override suspend fun markLessonCompleted(sessionId: String, lessonId: String, isCompleted: Boolean) {
        val currentLessons = lessons.value[sessionId] ?: return
        val updated = currentLessons.map { if (it.id == lessonId) it.copy(isCompleted = isCompleted) else it }
        lessons.value = lessons.value + (sessionId to updated)
        val completedCount = updated.count { it.isCompleted }
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(completedLessons = completedCount, isCompleted = completedCount == updated.size) else it
        }
    }

    override suspend fun toggleLessonBookmark(lessonId: String, currentBookmarked: Boolean) {
        lessons.value = lessons.value.mapValues { (_, list) ->
            list.map { if (it.id == lessonId) it.copy(isBookmarked = !currentBookmarked) else it }
        }
    }

    override suspend fun updateLastOpenedLesson(sessionId: String, lessonIndex: Int) {
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(lastOpenedLessonIndex = lessonIndex) else it
        }
    }

    override suspend fun submitQuizAnswer(questionId: String, selectedAnswer: String): Boolean {
        var isCorrect = false
        questions.value = questions.value.mapValues { (_, list) ->
            list.map { q ->
                if (q.id == questionId) {
                    isCorrect = q.correctAnswer.equals(selectedAnswer, ignoreCase = true)
                    q.copy(userAnswer = selectedAnswer, isAnswered = true, isCorrect = isCorrect, attemptCount = q.attemptCount + 1)
                } else q
            }
        }
        return isCorrect
    }

    override suspend fun resetQuizAnswers(sessionId: String) {
        questions.value = questions.value.mapValues { (sid, list) ->
            if (sid == sessionId) list.map { it.copy(userAnswer = null, isAnswered = false, isCorrect = false) } else list
        }
    }

    override suspend fun recordQuizAttempt(sessionId: String): LearningQuizAttempt? {
        val qs = questions.value[sessionId] ?: return null
        val correct = qs.count { it.isCorrect }
        val attempt = LearningQuizAttempt(
            id = "attempt_${UUID.randomUUID()}",
            sessionId = sessionId,
            score = correct,
            totalQuestions = qs.size,
            percentage = if (qs.isNotEmpty()) ((correct.toFloat() / qs.size.toFloat()) * 100).toInt() else 0,
            attemptedAt = System.currentTimeMillis()
        )
        val currentList = attempts.value[sessionId] ?: emptyList()
        attempts.value = attempts.value + (sessionId to (listOf(attempt) + currentList))
        return attempt
    }

    override suspend fun askLessonTutor(lesson: LearningLesson, question: String): NetworkResult<String> {
        return NetworkResult.Success("Tutor answer to: $question in context of ${lesson.title}")
    }

    override suspend fun deleteSession(sessionId: String) {
        sessions.value = sessions.value.filterNot { it.id == sessionId }
        lessons.value = lessons.value - sessionId
        questions.value = questions.value - sessionId
        attempts.value = attempts.value - sessionId
    }
}
