package com.ragchat.eval.metrics

import com.ragchat.eval.judge.LlmJudge
import com.ragchat.eval.model.GoldenQueryCase

/**
 * Execution context bundling inputs for a single case evaluation.
 */
public data class CaseEvaluationContext(
    public val case: GoldenQueryCase,
    public val retrievedDocIds: List<String>,
    public val retrievedChunks: List<String>,
    public val generatedAnswer: String,
    public val latencyMs: Long,
    public val topK: Int = 3,
)

/**
 * Metric outcomes for an individual query evaluation case.
 */
public data class CaseEvaluationResult(
    public val caseId: String,
    public val recallAtK: Float,
    public val contextPrecision: Float,
    public val faithfulness: Float,
    public val relevance: Float,
    public val refusalAccuracy: Float,
    public val latencyMs: Long,
    public val passed: Boolean,
    public val failureReasons: List<String> = emptyList(),
)

/**
 * Aggregated metric scores across an entire evaluation run.
 */
public data class RagEvaluationReport(
    public val totalCases: Int,
    public val meanRecallAtK: Float,
    public val meanContextPrecision: Float,
    public val meanFaithfulness: Float,
    public val meanRelevance: Float,
    public val meanRefusalAccuracy: Float,
    public val meanLatencyMs: Long,
    public val allCasesPassed: Boolean,
    public val caseResults: List<CaseEvaluationResult>,
)

/**
 * Calculator computing standard RAG metrics.
 */
public class RagMetricsCalculator(
    private val judge: LlmJudge,
) {
    /**
     * Computes metrics for a single case given retrieved document IDs and generated answer.
     */
    public suspend fun evaluateCase(context: CaseEvaluationContext): CaseEvaluationResult {
        if (context.case.expectedRefusal) {
            return evaluateRefusalCase(context)
        }
        return evaluateAnswerableCase(context)
    }

    private suspend fun evaluateRefusalCase(ctx: CaseEvaluationContext): CaseEvaluationResult {
        val refusalJudge = judge.evaluateRefusal(ctx.case.question, ctx.generatedAnswer)
        val failureReasons =
            if (!refusalJudge.passed) {
                listOf("Failed to refuse unanswerable question: ${refusalJudge.reasoning}")
            } else {
                emptyList()
            }
        return CaseEvaluationResult(
            caseId = ctx.case.id,
            recallAtK = 1.0f,
            contextPrecision = 1.0f,
            faithfulness = 1.0f,
            relevance = 1.0f,
            refusalAccuracy = refusalJudge.score,
            latencyMs = ctx.latencyMs,
            passed = refusalJudge.passed,
            failureReasons = failureReasons,
        )
    }

    private suspend fun evaluateAnswerableCase(ctx: CaseEvaluationContext): CaseEvaluationResult {
        val failureReasons = mutableListOf<String>()

        val expectedDocIds =
            ctx.case.expectedCitations
                .map { it.documentId }
                .toSet()
        val topKDocs = ctx.retrievedDocIds.take(ctx.topK).toSet()
        val recallAtK =
            if (expectedDocIds.isEmpty()) {
                1.0f
            } else {
                val hits = expectedDocIds.intersect(topKDocs).size
                hits.toFloat() / expectedDocIds.size.toFloat()
            }
        if (recallAtK < 0.5f) {
            failureReasons.add("Low Recall@${ctx.topK}: $recallAtK")
        }

        val contextPrecision =
            if (topKDocs.isEmpty()) {
                0.0f
            } else {
                topKDocs.intersect(expectedDocIds).size.toFloat() / topKDocs.size.toFloat()
            }

        val faithfulnessResult = judge.evaluateFaithfulness(ctx.case.question, ctx.retrievedChunks, ctx.generatedAnswer)
        if (!faithfulnessResult.passed) {
            failureReasons.add("Faithfulness violation: ${faithfulnessResult.reasoning}")
        }

        val relevanceResult = judge.evaluateRelevance(ctx.case.question, ctx.generatedAnswer)
        if (!relevanceResult.passed) {
            failureReasons.add("Relevance failure: ${relevanceResult.reasoning}")
        }

        return CaseEvaluationResult(
            caseId = ctx.case.id,
            recallAtK = recallAtK,
            contextPrecision = contextPrecision,
            faithfulness = faithfulnessResult.score,
            relevance = relevanceResult.score,
            refusalAccuracy = 1.0f,
            latencyMs = ctx.latencyMs,
            passed = failureReasons.isEmpty(),
            failureReasons = failureReasons,
        )
    }

    /**
     * Aggregates multiple case results into a summary report.
     */
    public fun aggregate(results: List<CaseEvaluationResult>): RagEvaluationReport {
        if (results.isEmpty()) {
            return RagEvaluationReport(0, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0L, true, emptyList())
        }

        val n = results.size.toFloat()
        return RagEvaluationReport(
            totalCases = results.size,
            meanRecallAtK = results.map { it.recallAtK }.sum() / n,
            meanContextPrecision = results.map { it.contextPrecision }.sum() / n,
            meanFaithfulness = results.map { it.faithfulness }.sum() / n,
            meanRelevance = results.map { it.relevance }.sum() / n,
            meanRefusalAccuracy = results.map { it.refusalAccuracy }.sum() / n,
            meanLatencyMs = (results.map { it.latencyMs }.sum().toDouble() / results.size.toDouble()).toLong(),
            allCasesPassed = results.all { it.passed },
            caseResults = results,
        )
    }
}
