package com.example.data.model

/**
 * Categories for organizing built-in and custom prompts in the MINOVA AI Prompt Library.
 */
enum class PromptCategory(val displayName: String) {
    CODING("Coding"),
    STUDY("Study"),
    ENGLISH("English"),
    WRITING("Writing");

    companion object {
        fun fromString(value: String?): PromptCategory {
            if (value == null) return CODING
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                        it.displayName.equals(value, ignoreCase = true)
            } ?: CODING
        }
    }
}

/**
 * Domain model representing a reusable prompt template in the Prompt Library.
 */
data class PromptItem(
    val id: String,
    val title: String,
    val description: String,
    val promptText: String,
    val category: PromptCategory,
    val tags: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Built-in curated prompts organized across Coding, Study, English, and Writing categories.
 */
object BuiltInPrompts {
    val all: List<PromptItem> = listOf(
        // ============================================================
        // 1. CODING PROMPTS
        // ============================================================
        PromptItem(
            id = "builtin_coding_generation",
            title = "Code Generation",
            description = "Generate clean, production-ready code with best practices and edge-case handling.",
            promptText = "Write clean, well-structured, production-ready code for the following requirement.\n\nLanguage/Framework: [Specify language or framework]\nRequirement: [Describe what you want to build]\n\nPlease include:\n1. Idiomatic implementation with clear naming\n2. Error handling and edge-case safety\n3. Brief explanation of the architecture and key choices",
            category = PromptCategory.CODING,
            tags = listOf("code", "generate", "implementation", "architecture", "clean code"),
            isCustom = false,
            createdAt = 1_700_000_001_000L,
            updatedAt = 1_700_000_001_000L
        ),
        PromptItem(
            id = "builtin_coding_debugging",
            title = "Debugging Assistant",
            description = "Diagnose bugs, stack traces, or unexpected behavior and provide a verified fix.",
            promptText = "Help me debug the following issue in my code.\n\nExpected behavior: [What should happen]\nActual behavior / Error message: [Paste error or stack trace]\n\nCode snippet:\n```\n[Paste your code here]\n```\n\nPlease identify the root cause, provide the corrected code, and explain how to prevent similar bugs.",
            category = PromptCategory.CODING,
            tags = listOf("debug", "bug", "error", "stack trace", "fix", "troubleshooting"),
            isCustom = false,
            createdAt = 1_700_000_002_000L,
            updatedAt = 1_700_000_002_000L
        ),
        PromptItem(
            id = "builtin_coding_explanation",
            title = "Code Explanation & Review",
            description = "Break down complex code step-by-step and suggest performance or readability improvements.",
            promptText = "Explain how the following code works step by step.\n\n```\n[Paste code here]\n```\n\nPlease cover:\n1. High-level summary of its purpose\n2. Step-by-step walkthrough of the logic\n3. Time and space complexity\n4. Suggestions for refactoring, readability, or performance improvements",
            category = PromptCategory.CODING,
            tags = listOf("explain", "review", "refactor", "complexity", "walkthrough"),
            isCustom = false,
            createdAt = 1_700_000_003_000L,
            updatedAt = 1_700_000_003_000L
        ),

        // ============================================================
        // 2. STUDY PROMPTS
        // ============================================================
        PromptItem(
            id = "builtin_study_topic_explanation",
            title = "Topic Explanation",
            description = "Master any complex subject with intuitive explanations, analogies, and examples.",
            promptText = "Explain the topic \"[Insert Topic]\" in a clear, structured way.\n\nPlease include:\n1. A simple intuitive overview (Feynman technique)\n2. Core concepts and key terminology\n3. A real-world analogy or practical example\n4. Common misconceptions to avoid",
            category = PromptCategory.STUDY,
            tags = listOf("study", "explain", "concept", "feynman", "learn", "topic"),
            isCustom = false,
            createdAt = 1_700_000_004_000L,
            updatedAt = 1_700_000_004_000L
        ),
        PromptItem(
            id = "builtin_study_notes",
            title = "Notes Generation",
            description = "Transform raw material or syllabus topics into structured, high-retention study notes.",
            promptText = "Create comprehensive, structured study notes on the following topic or material:\n\nTopic / Source Text:\n[Insert topic or paste lecture content]\n\nFormat the notes with:\n- Executive summary\n- Key definitions & formulas\n- Bullet-point takeaways grouped by subtopic\n- Quick-review checklist for exam preparation",
            category = PromptCategory.STUDY,
            tags = listOf("notes", "revision", "summary", "exam", "outline", "study"),
            isCustom = false,
            createdAt = 1_700_000_005_000L,
            updatedAt = 1_700_000_005_000L
        ),
        PromptItem(
            id = "builtin_study_quiz",
            title = "Quiz & Active Recall",
            description = "Generate practice questions with detailed answer explanations to test your understanding.",
            promptText = "Create an active-recall practice quiz on \"[Insert Topic]\".\n\nDifficulty level: [Beginner / Intermediate / Advanced]\n\nPlease generate:\n- 5 multiple-choice questions\n- 3 short-answer conceptual questions\n- An answer key at the end with clear explanations for why each answer is correct",
            category = PromptCategory.STUDY,
            tags = listOf("quiz", "test", "practice", "questions", "active recall", "flashcards"),
            isCustom = false,
            createdAt = 1_700_000_006_000L,
            updatedAt = 1_700_000_006_000L
        ),

        // ============================================================
        // 3. ENGLISH PROMPTS
        // ============================================================
        PromptItem(
            id = "builtin_english_grammar",
            title = "Grammar Correction",
            description = "Fix grammatical errors, punctuation, and awkward phrasing while explaining the rules.",
            promptText = "Check and correct the grammar, spelling, and punctuation in the following text:\n\n\"[Paste your text here]\"\n\nPlease provide:\n1. The corrected, natural-sounding version\n2. A polished alternative for formal/professional contexts\n3. A bulleted breakdown of the key grammar or phrasing improvements made",
            category = PromptCategory.ENGLISH,
            tags = listOf("grammar", "proofread", "correction", "english", "fluency"),
            isCustom = false,
            createdAt = 1_700_000_007_000L,
            updatedAt = 1_700_000_007_000L
        ),
        PromptItem(
            id = "builtin_english_translation",
            title = "Translation & Nuance",
            description = "Translate text accurately into natural English while preserving tone and cultural nuance.",
            promptText = "Translate the following text into natural, idiomatic English (or specify target language: [Target Language]):\n\n\"[Paste text to translate]\"\n\nPlease provide:\n1. Natural translation that preserves the original tone\n2. Alternative phrasings (formal vs. conversational)\n3. Notes on any idioms or cultural nuances",
            category = PromptCategory.ENGLISH,
            tags = listOf("translate", "translation", "language", "idioms", "nuance"),
            isCustom = false,
            createdAt = 1_700_000_008_000L,
            updatedAt = 1_700_000_008_000L
        ),
        PromptItem(
            id = "builtin_english_vocabulary",
            title = "Vocabulary Practice",
            description = "Learn high-impact vocabulary, synonyms, collocations, and contextual usage examples.",
            promptText = "Help me expand my English vocabulary around the theme or word: \"[Insert Word or Theme]\".\n\nPlease provide:\n1. Clear definition, pronunciation guide, and part of speech\n2. 5 advanced synonyms or related expressions with nuance differences\n3. Common collocations and example sentences in professional and daily contexts\n4. A quick fill-in-the-blank exercise to practice",
            category = PromptCategory.ENGLISH,
            tags = listOf("vocabulary", "words", "synonyms", "english", "collocations", "speaking"),
            isCustom = false,
            createdAt = 1_700_000_009_000L,
            updatedAt = 1_700_000_009_000L
        ),

        // ============================================================
        // 4. WRITING PROMPTS
        // ============================================================
        PromptItem(
            id = "builtin_writing_email",
            title = "Email Writing",
            description = "Draft clear, persuasive, and well-toned professional emails for any situation.",
            promptText = "Draft a professional email with the following details:\n\nRecipient: [Who you are emailing]\nPurpose / Key message: [What you want to achieve]\nTone: [Formal / Friendly / Persuasive / Concise]\nKey points to include: [List details or context]\n\nPlease include 3 compelling subject line options and a clear call to action.",
            category = PromptCategory.WRITING,
            tags = listOf("email", "professional", "communication", "outreach", "business"),
            isCustom = false,
            createdAt = 1_700_000_010_000L,
            updatedAt = 1_700_000_010_000L
        ),
        PromptItem(
            id = "builtin_writing_report",
            title = "Report Writing",
            description = "Structure executive reports, project updates, or technical documentation clearly.",
            promptText = "Write a well-structured professional report based on the following information:\n\nReport Title / Topic: [Insert Topic]\nAudience: [Executives / Engineering Team / Clients]\nKey Findings / Data Points:\n[Paste raw notes or metrics]\n\nPlease organize the report with:\n1. Executive Summary\n2. Background & Objectives\n3. Key Findings & Analysis\n4. Actionable Recommendations & Next Steps",
            category = PromptCategory.WRITING,
            tags = listOf("report", "documentation", "executive", "writing", "analysis"),
            isCustom = false,
            createdAt = 1_700_000_011_000L,
            updatedAt = 1_700_000_011_000L
        ),
        PromptItem(
            id = "builtin_writing_summarization",
            title = "Summarization & Key Insights",
            description = "Distill long articles, meetings, or documents into concise takeaways and action items.",
            promptText = "Summarize the following text clearly and concisely:\n\n\"[Paste article, transcript, or document here]\"\n\nPlease provide:\n1. A one-sentence TL;DR summary\n2. Top 5 key takeaways in bullet points\n3. Important decisions, metrics, or action items mentioned",
            category = PromptCategory.WRITING,
            tags = listOf("summarize", "summary", "tldr", "insights", "takeaways", "writing"),
            isCustom = false,
            createdAt = 1_700_000_012_000L,
            updatedAt = 1_700_000_012_000L
        )
    )
}
