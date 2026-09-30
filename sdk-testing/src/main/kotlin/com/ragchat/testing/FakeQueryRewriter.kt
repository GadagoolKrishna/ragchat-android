package com.ragchat.testing

import com.ragchat.api.model.ChatMessage
import com.ragchat.api.retrieval.QueryRewriter

/**
 * Test fake implementation of [QueryRewriter].
 */
public class FakeQueryRewriter(
    private val prefix: String = "",
) : QueryRewriter {
    override suspend fun rewrite(
        query: String,
        history: List<ChatMessage>,
    ): String = if (prefix.isEmpty()) query else "$prefix $query"
}
