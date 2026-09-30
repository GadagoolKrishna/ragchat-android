package com.ragchat.api.storage

import com.ragchat.api.model.Chunk

/**
 * Result returned from a vector or hybrid search query.
 *
 * @property chunk Retrieved text chunk.
 * @property score Similarity or relevance score (higher is more relevant).
 * @property distance Optional raw distance metric value.
 */
public data class SearchResult(
    val chunk: Chunk,
    val score: Float,
    val distance: Float? = null,
)
