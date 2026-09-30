package com.ragchat.api.embedding

/**
 * Task objective for asymmetric or specialized text embedding models.
 */
public enum class EmbeddingTaskType {
    /**
     * Embedding a document passage or chunk for index storage.
     */
    RETRIEVAL_DOCUMENT,

    /**
     * Embedding a short search query for retrieval matching.
     */
    RETRIEVAL_QUERY,

    /**
     * Embedding for direct semantic similarity or sentence clustering.
     */
    SEMANTIC_SIMILARITY,

    /**
     * Embedding for downstream text classification.
     */
    CLASSIFICATION,
}
