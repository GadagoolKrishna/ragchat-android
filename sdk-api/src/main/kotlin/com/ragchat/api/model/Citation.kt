package com.ragchat.api.model

/**
 * Source citation linking an LLM answer back to an ingested document chunk.
 *
 * @property documentId Identifier of the referenced document.
 * @property chunkId Identifier of the specific chunk referenced.
 * @property textSnippet Extracted snippet supporting the generated statement.
 * @property pageNumber 1-based page number if available.
 * @property score Retrieval or relevance score (0.0 to 1.0).
 * @property metadata Arbitrary metadata key-value pairs associated with the source.
 */
public data class Citation(
    val documentId: String,
    val chunkId: String,
    val textSnippet: String? = null,
    val pageNumber: Int? = null,
    val score: Float? = null,
    val metadata: Map<String, String> = emptyMap(),
)
