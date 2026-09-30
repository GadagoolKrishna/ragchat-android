package com.ragchat.api.retrieval

import com.ragchat.api.model.ChatMessage

/**
 * Service Provider Interface (SPI) for reformulating conversational queries into standalone search queries.
 */
public interface QueryRewriter {
    /**
     * Resolves anaphoras and contextual references in [query] using recent [history].
     *
     * @param query Raw user input.
     * @param history Recent conversation messages for context.
     * @return Self-contained, retrieval-optimized query string.
     */
    public suspend fun rewrite(
        query: String,
        history: List<ChatMessage>,
    ): String
}
