package com.ragchat.core.routing

/**
 * Heuristic analyzer evaluating query complexity to inform local vs cloud model routing.
 */
public object QueryComplexityHeuristic {
    private val REASONING_KEYWORDS =
        setOf(
            "compare",
            "contrast",
            "analyze",
            "synthesize",
            "explain why",
            "evaluate",
            "difference between",
            "pros and cons",
            "step by step",
            "elaborate",
            "derive",
            "conclude",
            "implications",
            "relationship between",
            "how does",
            "summarize in depth",
        )

    /**
     * Determines whether the given query and conversation context exhibits high complexity.
     *
     * @param query The active user query string.
     * @param historyTurnCount Number of previous conversation turns.
     * @param requestedMaxTokens Output length requested by user/options.
     * @return `true` if query complexity indicates a cloud-grade model is preferred.
     */
    public fun isHighComplexity(
        query: String,
        historyTurnCount: Int = 0,
        requestedMaxTokens: Int = 256,
    ): Boolean {
        val lower = query.lowercase()
        val keywordMatch = REASONING_KEYWORDS.any { lower.contains(it) }
        val isLongQuery = query.length > 250
        val isLongContext = historyTurnCount >= 6
        val isLongOutput = requestedMaxTokens >= 800

        var score = 0
        if (keywordMatch) score += 2
        if (isLongQuery) score += 1
        if (isLongContext) score += 1
        if (isLongOutput) score += 1

        return score >= 2
    }
}
