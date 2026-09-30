package com.ragchat.retrieval

/**
 * Pure Kotlin search algorithm implementation (RRF, MMR).
 */
public class HybridSearchEngine {
    /**
     * Computes reciprocal rank fusion score for a collection of ranks.
     * Formula: sum(1.0 / (k + rank)) with default k = 60.
     */
    public fun computeRrf(
        rankings: List<Int>,
        k: Int = 60,
    ): Double = rankings.sumOf { rank -> 1.0 / (k + rank) }
}
