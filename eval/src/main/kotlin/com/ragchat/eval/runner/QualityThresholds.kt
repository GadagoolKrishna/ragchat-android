package com.ragchat.eval.runner

import com.ragchat.eval.metrics.RagEvaluationReport

/**
 * Enterprise quality gate thresholds for RAG regression detection.
 */
public data class QualityThresholds(
    public val minRecallAtK: Float = 0.85f,
    public val minContextPrecision: Float = 0.80f,
    public val minFaithfulness: Float = 0.90f,
    public val minRelevance: Float = 0.85f,
    public val minRefusalAccuracy: Float = 0.95f,
    public val maxLatencyMs: Long = 1500L,
) {
    /**
     * Verifies an evaluation report against the threshold targets.
     *
     * @return List of regression violations (empty if all passed).
     */
    public fun checkViolations(report: RagEvaluationReport): List<String> {
        val violations = mutableListOf<String>()

        if (report.meanRecallAtK < minRecallAtK) {
            violations.add("Recall@K regression: ${report.meanRecallAtK} < threshold $minRecallAtK")
        }
        if (report.meanContextPrecision < minContextPrecision) {
            violations.add("Context Precision regression: ${report.meanContextPrecision} < threshold $minContextPrecision")
        }
        if (report.meanFaithfulness < minFaithfulness) {
            violations.add("Faithfulness regression: ${report.meanFaithfulness} < threshold $minFaithfulness")
        }
        if (report.meanRelevance < minRelevance) {
            violations.add("Relevance regression: ${report.meanRelevance} < threshold $minRelevance")
        }
        if (report.meanRefusalAccuracy < minRefusalAccuracy) {
            violations.add("Refusal Accuracy regression: ${report.meanRefusalAccuracy} < threshold $minRefusalAccuracy")
        }
        if (report.meanLatencyMs > maxLatencyMs) {
            violations.add("Latency regression: ${report.meanLatencyMs}ms > max allowed ${maxLatencyMs}ms")
        }

        return violations
    }
}
