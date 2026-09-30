package com.ragchat.api.llm

import com.ragchat.api.model.ChatMessage

/**
 * Request payload passed to an [LlmProvider] for completion or chat generation.
 *
 * @property messages Ordered conversation messages.
 * @property temperature Sampling temperature (0.0 for deterministic, 1.0+ for creative).
 * @property maxTokens Maximum tokens to produce in the output.
 * @property stopSequences Optional stop token sequences triggering completion termination.
 */
public data class LlmRequest(
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
)
