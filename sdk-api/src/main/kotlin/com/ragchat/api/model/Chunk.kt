package com.ragchat.api.model

/**
 * Represents a segmented chunk of a [Document] ready for embedding and indexing.
 *
 * @property id Unique identifier of this chunk.
 * @property documentId The identifier of the parent document.
 * @property content Raw textual content of the chunk.
 * @property sequenceNumber Index order of this chunk within the parent document.
 * @property tokenCount Approximate or exact token count of this chunk's content.
 * @property metadata Key-value metadata specific to this chunk (e.g., page numbers, section headers).
 */
public data class Chunk(
    val id: String,
    val documentId: String,
    val content: String,
    val sequenceNumber: Int,
    val tokenCount: Int,
    val metadata: Map<String, String> = emptyMap(),
)
