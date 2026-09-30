package com.ragchat.retrieval

import com.ragchat.api.error.SdkError
import com.ragchat.api.retrieval.Reranker
import com.ragchat.api.storage.SearchResult
import com.ragchat.api.storage.VectorStore

/**
 * Configuration options for the [RetrievalPipeline].
 *
 * @property topK Number of candidate chunks to retrieve.
 * @property confidenceThreshold Minimum top similarity score required. If the best score falls below,
 *                                the pipeline yields [SdkError.InsufficientEvidenceError].
 * @property enableMmr Whether to apply Maximal Marginal Relevance diversification.
 * @property mmrLambda Balance factor between relevance and diversity in MMR.
 * @property rrfK Smoothing constant for Reciprocal Rank Fusion.
 */
public data class RetrievalConfig(
    public val topK: Int = 5,
    public val confidenceThreshold: Float = 0.35f,
    public val enableMmr: Boolean = true,
    public val mmrLambda: Float = 0.7f,
    public val rrfK: Int = 60,
)

/**
 * Orchestrator for the retrieval engine.
 *
 * Combines:
 * 1. Hybrid dense vector + FTS5 BM25 search via Reciprocal Rank Fusion (RRF).
 * 2. In-query ACL tag & metadata filtering (never post-filtered).
 * 3. Confidence score gating yielding "insufficient evidence" error.
 * 4. Optional SPI [Reranker] hook.
 * 5. Maximal Marginal Relevance (MMR) diversification.
 * 6. Context assembly packing chunks into token budgets.
 *
 * Pure Kotlin/JVM module with zero Android framework imports.
 */
public class RetrievalPipeline(
    private val vectorStore: VectorStore,
    private val reranker: Reranker? = null,
    private val config: RetrievalConfig = RetrievalConfig(),
) {
    /**
     * Executes hybrid retrieval against a collection.
     *
     * @param collectionId Target collection ID.
     * @param query Raw user text query.
     * @param queryVector Dense query embedding vector.
     * @param aclFilter Security access control list filter and metadata criteria.
     * @return Ranked and diversified list of [SearchResult] items.
     * @throws SdkError.InsufficientEvidenceError if the highest score is below confidenceThreshold.
     */
    public suspend fun retrieve(
        collectionId: String,
        query: String,
        queryVector: FloatArray,
        aclFilter: AclFilter? = null,
    ): List<SearchResult> {
        val filterMap = aclFilter?.toQueryFilter()

        // 1. Execute hybrid retrieval in vector store (vector + FTS5 combined with RRF)
        val rawCandidates =
            vectorStore.hybridFtsQuery(
                collectionId = collectionId,
                queryText = query,
                vector = queryVector,
                topK = config.topK * 2,
                filter = filterMap,
            )

        val initialCandidates =
            if (aclFilter != null) {
                rawCandidates.filter { aclFilter.matches(it.chunk.metadata) }
            } else {
                rawCandidates
            }

        if (initialCandidates.isEmpty()) {
            throw SdkError.InsufficientEvidenceError(
                "No documents matched the query or access control criteria",
            )
        }

        // 2. Confidence threshold check: yield "insufficient evidence" if top score is too low
        val topScore = initialCandidates.first().score
        if (topScore < config.confidenceThreshold) {
            throw SdkError.InsufficientEvidenceError(
                "Top retrieval score ($topScore) falls below confidence threshold (${config.confidenceThreshold})",
            )
        }

        // 3. Optional Reranker SPI hook
        val reranked =
            if (reranker != null) {
                reranker.rerank(query, initialCandidates)
            } else {
                initialCandidates
            }

        // 4. MMR Diversification
        val diversified =
            if (config.enableMmr) {
                MmrDiversifier.diversify(
                    candidates = reranked,
                    topK = config.topK,
                    lambda = config.mmrLambda,
                )
            } else {
                reranked.take(config.topK)
            }

        return diversified
    }
}
