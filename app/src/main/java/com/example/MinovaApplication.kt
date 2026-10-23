package com.example

import android.app.Application
import com.example.data.local.MinovaDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.CloudConversationRepository
import com.example.data.repository.ConversationRepository
import com.example.data.repository.ConversationRepositoryImpl
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirestoreCloudConversationRepository
import com.example.data.repository.InMemoryAuthRepository
import com.example.data.repository.InMemoryCloudConversationRepository
import com.example.data.repository.PromptLibraryRepository
import com.example.data.repository.PromptLibraryRepositoryImpl
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.CodeWorkspaceRepository
import com.example.data.repository.CodeWorkspaceRepositoryImpl
import com.example.data.repository.FileStudioRepository
import com.example.data.repository.FileStudioRepositoryImpl
import com.example.data.repository.LearningRepository
import com.example.data.repository.LearningRepositoryImpl
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SettingsRepositoryImpl
import com.google.firebase.FirebaseApp

class MinovaApplication : Application() {

    lateinit var database: MinovaDatabase
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var promptLibraryRepository: PromptLibraryRepository
        private set

    lateinit var learningRepository: LearningRepository
        private set

    lateinit var codeWorkspaceRepository: CodeWorkspaceRepository
        private set

    lateinit var fileStudioRepository: FileStudioRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var cloudConversationRepository: CloudConversationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MinovaDatabase.getInstance(this)
        conversationRepository = ConversationRepositoryImpl(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            attachmentDao = database.attachmentDao()
        )
        settingsRepository = SettingsRepositoryImpl(this)
        promptLibraryRepository = PromptLibraryRepositoryImpl(
            customPromptDao = database.customPromptDao()
        )
        val chatRepository = ChatRepositoryImpl()
        learningRepository = LearningRepositoryImpl(
            learningDao = database.learningDao(),
            chatRepository = chatRepository
        )
        codeWorkspaceRepository = CodeWorkspaceRepositoryImpl(
            codeSnippetDao = database.codeSnippetDao(),
            chatRepository = chatRepository
        )
        fileStudioRepository = FileStudioRepositoryImpl()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            authRepository = FirebaseAuthRepository(this)
            cloudConversationRepository = FirestoreCloudConversationRepository(this)
        } catch (_: Throwable) {
            val fallbackAuth = InMemoryAuthRepository()
            authRepository = fallbackAuth
            cloudConversationRepository = InMemoryCloudConversationRepository {
                fallbackAuth.currentUser?.uid
            }
        }
    }

    companion object {
        lateinit var instance: MinovaApplication
            private set
    }
}
