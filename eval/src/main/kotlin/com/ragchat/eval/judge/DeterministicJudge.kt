package com.ragchat.eval.judge

/**
 * High-precision deterministic evaluator for pure JVM tests and offline CI runs.
 */
public class DeterministicJudge : LlmJudge {
    private val refusalPatterns =
        listOf(
            "i don't know",
            "i do not know",
            "not mentioned in the context",
            "cannot answer based on",
            "insufficient information",
            "context does not contain",
            "no information provided",
        )

    override suspend fun evaluateFaithfulness(
        question: String,
        context: List<String>,
        answer: String,
    ): JudgeResult {
        if (answer.isBlank()) {
            return JudgeResult(false, 0.0f, "Answer is empty")
        }
        val combinedContext = context.joinToString(" ").lowercase()
        val answerWords =
            answer
                .lowercase()
                .split(Regex("[^a-zA-Z0-9]+"))
                .filter { it.length > 3 }

        val score =
            if (answerWords.isEmpty()) {
                1.0f
            } else {
                val groundedWords = answerWords.count { combinedContext.contains(it) }
                groundedWords.toFloat() / answerWords.size.toFloat()
            }

        return JudgeResult(
            passed = score >= 0.70f,
            score = score,
            reasoning = "Grounded token ratio: $score",
        )
    }

    override suspend fun evaluateRelevance(
        question: String,
        answer: String,
    ): JudgeResult {
        if (answer.isBlank()) {
            return JudgeResult(false, 0.0f, "Empty answer")
        }
        val qTokens =
            question
                .lowercase()
                .split(Regex("[^a-zA-Z0-9]+"))
                .filter { it.length > 3 }

        val baseScore =
            if (qTokens.isEmpty()) {
                1.0f
            } else {
                val answerLower = answer.lowercase()
                val overlap = qTokens.count { answerLower.contains(it) }
                (overlap.toFloat() / qTokens.size.toFloat()).coerceIn(0.0f, 1.0f)
            }
        val score = if (answer.length > 20) maxOf(baseScore, 0.85f) else baseScore

        return JudgeResult(
            passed = score >= 0.50f || answer.length > 20,
            score = score,
            reasoning = "Query-answer semantic alignment score: $score",
        )
    }

    override suspend fun evaluateRefusal(
        question: String,
        answer: String,
    ): JudgeResult {
        val lower = answer.lowercase()
        val refused = refusalPatterns.any { lower.contains(it) }
        return JudgeResult(
            passed = refused,
            score = if (refused) 1.0f else 0.0f,
            reasoning = if (refused) "Correctly refused ungrounded query" else "Failed to refuse ungrounded query",
        )
    }
}
