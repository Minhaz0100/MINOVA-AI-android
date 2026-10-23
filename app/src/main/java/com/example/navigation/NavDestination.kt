package com.example.navigation

sealed class MinovaScreen {
    data object Splash : MinovaScreen()
    data class Chat(val conversationId: String? = null) : MinovaScreen()
    data object Settings : MinovaScreen()
    data object Profile : MinovaScreen()
    data object Search : MinovaScreen()
    data object Pinned : MinovaScreen()
    data object Archived : MinovaScreen()
    data object PromptLibrary : MinovaScreen()
    data object LearnHome : MinovaScreen()
    data class LearnSession(val sessionId: String, val lessonIndex: Int = 0) : MinovaScreen()
    data class LearnQuiz(val sessionId: String) : MinovaScreen()
    data object CodeWorkspace : MinovaScreen()
    data object FileStudio : MinovaScreen()
}
