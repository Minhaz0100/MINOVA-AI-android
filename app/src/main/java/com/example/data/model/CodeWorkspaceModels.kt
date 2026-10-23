package com.example.data.model

/**
 * Supported programming languages in MINOVA AI Code Workspace.
 */
enum class CodeLanguage(
    val id: String,
    val displayName: String,
    val fileExtension: String,
    val markdownTag: String,
    val sampleSnippet: String
) {
    C(
        id = "c",
        displayName = "C",
        fileExtension = ".c",
        markdownTag = "c",
        sampleSnippet = """
            #include <stdio.h>

            int main(void) {
                int numbers[] = {2, 4, 6, 8, 10};
                int sum = 0;
                int count = sizeof(numbers) / sizeof(numbers[0]);
                for (int i = 0; i < count; i++) {
                    sum += numbers[i];
                }
                printf("Sum: %d\n", sum);
                return 0;
            }
        """.trimIndent()
    ),
    CPP(
        id = "cpp",
        displayName = "C++",
        fileExtension = ".cpp",
        markdownTag = "cpp",
        sampleSnippet = """
            #include <iostream>
            #include <vector>
            #include <numeric>

            int main() {
                std::vector<int> values = {10, 20, 30, 40};
                int total = std::accumulate(values.begin(), values.end(), 0);
                std::cout << "Total: " << total << std::endl;
                return 0;
            }
        """.trimIndent()
    ),
    JAVA(
        id = "java",
        displayName = "Java",
        fileExtension = ".java",
        markdownTag = "java",
        sampleSnippet = """
            import java.util.List;

            public class Main {
                public static void main(String[] args) {
                    List<String> items = List.of("Alpha", "Beta", "Gamma");
                    items.stream()
                        .map(String::toUpperCase)
                        .forEach(System.out::println);
                }
            }
        """.trimIndent()
    ),
    PYTHON(
        id = "python",
        displayName = "Python",
        fileExtension = ".py",
        markdownTag = "python",
        sampleSnippet = """
            def fibonacci(n: int) -> list[int]:
                if n <= 0:
                    return []
                seq = [0, 1]
                while len(seq) < n:
                    seq.append(seq[-1] + seq[-2])
                return seq[:n]

            print(fibonacci(8))
        """.trimIndent()
    ),
    JAVASCRIPT(
        id = "javascript",
        displayName = "JavaScript",
        fileExtension = ".js",
        markdownTag = "javascript",
        sampleSnippet = """
            function groupByCategory(items) {
                return items.reduce((acc, item) => {
                    const key = item.category || 'uncategorized';
                    acc[key] = acc[key] || [];
                    acc[key].push(item);
                    return acc;
                }, {});
            }
        """.trimIndent()
    ),
    KOTLIN(
        id = "kotlin",
        displayName = "Kotlin",
        fileExtension = ".kt",
        markdownTag = "kotlin",
        sampleSnippet = """
            fun findFirstDuplicate(numbers: List<Int>): Int? {
                val seen = mutableSetOf<Int>()
                for (num in numbers) {
                    if (!seen.add(num)) return num
                }
                return null
            }

            fun main() {
                println(findFirstDuplicate(listOf(3, 5, 2, 5, 8)))
            }
        """.trimIndent()
    );

    companion object {
        fun fromId(id: String): CodeLanguage {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) ||
                    it.displayName.equals(id, ignoreCase = true) ||
                    it.markdownTag.equals(id, ignoreCase = true)
            } ?: KOTLIN
        }
    }
}

/**
 * Available AI actions inside Code Workspace.
 */
enum class CodeActionType(
    val id: String,
    val title: String,
    val subtitle: String,
    val requiresCodeInEditor: Boolean
) {
    EXPLAIN(
        id = "explain",
        title = "Explain Code",
        subtitle = "Step-by-step walkthrough of how the code works",
        requiresCodeInEditor = true
    ),
    DEBUG(
        id = "debug",
        title = "Debug Code",
        subtitle = "Analyze compiler/runtime errors and propose fixes",
        requiresCodeInEditor = true
    ),
    IMPROVE(
        id = "improve",
        title = "Improve Code",
        subtitle = "Optimize complexity, idioms, and structure",
        requiresCodeInEditor = true
    ),
    REVIEW(
        id = "review",
        title = "Review Code",
        subtitle = "Check readability, edge cases, and potential bugs",
        requiresCodeInEditor = true
    ),
    GENERATE(
        id = "generate",
        title = "Generate Code",
        subtitle = "Write clean code from a natural-language prompt",
        requiresCodeInEditor = false
    )
}

/**
 * Structured result from AI Code Assistance separating explanation, change summary, and code.
 */
data class CodeAssistanceResult(
    val actionType: CodeActionType,
    val language: CodeLanguage,
    val explanationMarkdown: String,
    val changesSummary: String? = null,
    val extractedCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Locally persisted code snippet scoped to a specific user or guest session.
 */
data class CodeSnippet(
    val id: String,
    val ownerUid: String,
    val title: String,
    val language: CodeLanguage,
    val code: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val GUEST_OWNER_UID = "guest_local"
    }
}
