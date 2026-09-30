package com.ragchat.api.chat

import com.ragchat.api.config.ModelRoutingMode

/**
 * Execution parameters and constraints for a chat interaction.
 *
 * @property workspaceId Logical workspace scope for isolation. Defaults to "default".
 * @property sessionId Conversational session identifier. Defaults to "default".
 * @property collectionId Vector store collection ID for document retrieval. Defaults to "default".
 * @property topK Override for candidate chunk retrieval count. If null, default SDK topK is used.
 * @property confidenceThreshold Minimum similarity threshold for retrieved context chunks.
 * @property temperature Sampling temperature override for the LLM.
 * @property maxTokens Maximum tokens to generate in the completion.
 * @property jsonSchema Optional JSON schema definition enforcing structured output format.
 * @property maxStructuredRetries Maximum retry attempts if output fails JSON schema validation.
 * @property routingMode Override for model routing locality (local vs cloud).
 * @property aclFilter Custom metadata key-value pairs for access-control filtering.
 */
public data class ChatOptions(
    val workspaceId: String = "default",
    val sessionId: String = "default",
    val collectionId: String = "default",
    val topK: Int? = null,
    val confidenceThreshold: Float? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val jsonSchema: String? = null,
    val maxStructuredRetries: Int = 2,
    val routingMode: ModelRoutingMode? = null,
    val aclFilter: Map<String, String>? = null,
)
