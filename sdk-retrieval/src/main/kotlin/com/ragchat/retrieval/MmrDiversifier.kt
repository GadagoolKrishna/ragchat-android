package com.ragchat.retrieval

import com.ragchat.api.model.Chunk
import com.ragchat.api.storage.SearchResult
import kotlin.math.sqrt

/**
 * Maximal Marginal Relevance (MMR) diversification engine.
 *
 * Balances retrieval relevance against semantic redundancy across candidate chunks:
 * MMR = argmax_{d_i in R \ S} [ lambda * Sim(d_i, Q) - (1 - lambda) * max_{d_j in S} Sim(d_i, d_j) ]
 */
public object MmrDiversifier {
    /**
     * Diversifies search results using MMR.
     *
     * @param candidates Initial ranked search results from hybrid retrieval.
     * @param embeddings Optional mapping of chunk ID to its vector embedding for pairwise similarity.
     *                   If absent, token Jaccard similarity is used as textual proxy.
     * @param topK Number of diversified chunks to select.
     * @param lambda Tradeoff parameter between 0.0 (maximum diversity) and 1.0 (pure relevance). Default is 0.7.
     * @return Ordered list of [SearchResult] items selected via MMR.
     */
    public fun diversify(
        candidates: List<SearchResult>,
        embeddings: Map<String, FloatArray> = emptyMap(),
        topK: Int = 10,
        lambda: Float = 0.7f,
    ): List<SearchResult> {
        if (candidates.isEmpty() || topK <= 0) return emptyList()

        val selected = mutableListOf<SearchResult>()
        if (candidates.size <= topK) {
            selected.addAll(candidates)
        } else {
            val remaining = candidates.toMutableList()
            val first = remaining.maxByOrNull { it.score }
            if (first != null) {
                selected.add(first)
                remaining.remove(first)
                runMmrLoop(selected, remaining, embeddings, topK, lambda)
            }
        }
        return selected
    }

    private fun runMmrLoop(
        selected: MutableList<SearchResult>,
        remaining: MutableList<SearchResult>,
        embeddings: Map<String, FloatArray>,
        topK: Int,
        lambda: Float,
    ) {
        while (selected.size < topK && remaining.isNotEmpty()) {
            val nextBest = findNextMmrCandidate(selected, remaining, embeddings, lambda) ?: break
            selected.add(nextBest)
            remaining.remove(nextBest)
        }
    }

    private fun findNextMmrCandidate(
        selected: List<SearchResult>,
        remaining: List<SearchResult>,
        embeddings: Map<String, FloatArray>,
        lambda: Float,
    ): SearchResult? {
        var bestCandidate: SearchResult? = null
        var bestMmrScore = -Float.MAX_VALUE

        for (candidate in remaining) {
            val maxSimToSelected =
                selected.maxOfOrNull { sel ->
                    computePairwiseSimilarity(candidate.chunk, sel.chunk, embeddings)
                } ?: 0f

            val mmrScore = (lambda * candidate.score) - ((1f - lambda) * maxSimToSelected)
            if (mmrScore > bestMmrScore) {
                bestMmrScore = mmrScore
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    private fun computePairwiseSimilarity(
        c1: Chunk,
        c2: Chunk,
        embeddings: Map<String, FloatArray>,
    ): Float {
        val e1 = embeddings[c1.id]
        val e2 = embeddings[c2.id]
        return if (e1 != null && e2 != null) {
            cosineSimilarity(e1, e2)
        } else {
            jaccardSimilarity(c1.content, c2.content)
        }
    }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }

    private fun jaccardSimilarity(
        textA: String,
        textB: String,
    ): Float {
        val tokensA = textA.lowercase().split("\\s+".toRegex()).toSet()
        val tokensB = textB.lowercase().split("\\s+".toRegex()).toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }
}
