package com.ragchat.api.retrieval

import com.ragchat.api.storage.SearchResult

/**
 * Service Provider Interface (SPI) for scoring and re-ranking candidate retrieval results.
 */
public interface Reranker {
    /**
     * Re-orders and scores retrieved search candidates against the original user query.
     *
     * @param query User prompt or search query.
     * @param candidates Initial candidates retrieved from vector or hybrid search.
     * @return Re-scored and sorted list of [SearchResult] items.
     */
    public suspend fun rerank(
        query: String,
        candidates: List<SearchResult>,
    ): List<SearchResult>
}
