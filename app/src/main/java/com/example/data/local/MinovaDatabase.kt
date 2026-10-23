package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AttachmentDao
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.entity.AttachmentEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity

import com.example.data.local.dao.CodeSnippetDao
import com.example.data.local.dao.CustomPromptDao
import com.example.data.local.dao.LearningDao
import com.example.data.local.entity.CodeSnippetEntity
import com.example.data.local.entity.CustomPromptEntity
import com.example.data.local.entity.LearningLessonEntity
import com.example.data.local.entity.LearningQuizAttemptEntity
import com.example.data.local.entity.LearningQuizQuestionEntity
import com.example.data.local.entity.LearningSessionEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        CustomPromptEntity::class,
        LearningSessionEntity::class,
        LearningLessonEntity::class,
        LearningQuizQuestionEntity::class,
        LearningQuizAttemptEntity::class,
        CodeSnippetEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class MinovaDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun customPromptDao(): CustomPromptDao
    abstract fun learningDao(): LearningDao
    abstract fun codeSnippetDao(): CodeSnippetDao

    companion object {
        private const val DATABASE_NAME = "minova_ai_database"

        @Volatile
        private var INSTANCE: MinovaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attachments` (
                        `attachmentId` TEXT NOT NULL,
                        `messageId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `localUri` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`attachmentId`),
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_conversationId` ON `attachments` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_messageId` ON `attachments` (`messageId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `responseStyle` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `responseLanguage` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `responseFormat` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_prompts` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `promptText` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_prompts_category` ON `custom_prompts` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_prompts_updatedAt` ON `custom_prompts` (`updatedAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_sessions` (
                        `id` TEXT NOT NULL,
                        `topic` TEXT NOT NULL,
                        `subjectCategory` TEXT NOT NULL,
                        `level` TEXT NOT NULL,
                        `goal` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `dailyDurationMinutes` INTEGER NOT NULL,
                        `totalLessons` INTEGER NOT NULL,
                        `completedLessons` INTEGER NOT NULL,
                        `lastOpenedLessonIndex` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_sessions_updatedAt` ON `learning_sessions` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_sessions_subjectCategory` ON `learning_sessions` (`subjectCategory`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_lessons` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `lessonIndex` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `contentMarkdown` TEXT NOT NULL,
                        `keyTakeaways` TEXT NOT NULL,
                        `codeSnippet` TEXT,
                        `codeLanguage` TEXT,
                        `codeExplanation` TEXT,
                        `expectedOutput` TEXT,
                        `debuggingGuidance` TEXT,
                        `practiceExercise` TEXT,
                        `isCompleted` INTEGER NOT NULL,
                        `isBookmarked` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `learning_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_lessons_sessionId` ON `learning_lessons` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_lessons_sessionId_lessonIndex` ON `learning_lessons` (`sessionId`, `lessonIndex`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_quiz_questions` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `lessonIndex` INTEGER NOT NULL,
                        `questionIndex` INTEGER NOT NULL,
                        `questionType` TEXT NOT NULL,
                        `questionText` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `userAnswer` TEXT,
                        `isAnswered` INTEGER NOT NULL,
                        `isCorrect` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `learning_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_quiz_questions_sessionId` ON `learning_quiz_questions` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_quiz_questions_sessionId_lessonIndex_questionIndex` ON `learning_quiz_questions` (`sessionId`, `lessonIndex`, `questionIndex`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_quiz_attempts` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `percentage` INTEGER NOT NULL,
                        `attemptedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `learning_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_quiz_attempts_sessionId` ON `learning_quiz_attempts` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_quiz_attempts_attemptedAt` ON `learning_quiz_attempts` (`attemptedAt`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `code_snippets` (
                        `id` TEXT NOT NULL,
                        `ownerUid` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_code_snippets_ownerUid` ON `code_snippets` (`ownerUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_code_snippets_updatedAt` ON `code_snippets` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_code_snippets_language` ON `code_snippets` (`language`)")
            }
        }

        fun getInstance(context: Context): MinovaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MinovaDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .build()
                .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): MinovaDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                MinovaDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
