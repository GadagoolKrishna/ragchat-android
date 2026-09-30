package com.ragchat.eval.judge

/**
 * Result produced by an LLM-as-judge or deterministic evaluator.
 *
 * @property passed Whether the response satisfies the metric requirements.
 * @property score Numeric score between 0.0 and 1.0.
 * @property reasoning Rationale or critique justifying the score.
 */
public data class JudgeResult(
    public val passed: Boolean,
    public val score: Float,
    public val reasoning: String,
)

/**
 * Pluggable Service Provider Interface (SPI) for LLM-as-a-Judge evaluators.
 */
public interface LlmJudge {
    /**
     * Evaluates answer faithfulness against retrieved context chunks.
     */
    public suspend fun evaluateFaithfulness(
        question: String,
        context: List<String>,
        answer: String,
    ): JudgeResult

    /**
     * Evaluates answer relevance against the original user question.
     */
    public suspend fun evaluateRelevance(
        question: String,
        answer: String,
    ): JudgeResult

    /**
     * Evaluates whether an unanswerable question was correctly refused.
     */
    public suspend fun evaluateRefusal(
        question: String,
        answer: String,
    ): JudgeResult
}
