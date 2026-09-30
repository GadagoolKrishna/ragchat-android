package com.ragchat.testing

import com.ragchat.api.retrieval.Reranker
import com.ragchat.api.storage.SearchResult

/**
 * Test fake implementation of [Reranker].
 */
public class FakeReranker : Reranker {
    override suspend fun rerank(
        query: String,
        candidates: List<SearchResult>,
    ): List<SearchResult> = candidates.sortedByDescending { it.score }
}
