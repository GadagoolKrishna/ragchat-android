package com.ragchat.api.model

/**
 * Represents a discrete document ingested into RagChat.
 *
 * @property id Unique identifier of the document.
 * @property uri Optional URI or relative path denoting the source location.
 * @property mimeType Standard MIME type of the document (e.g., application/pdf, text/markdown).
 * @property metadata Key-value metadata associated with the document.
 * @property createdAtEpochMs Timestamp in milliseconds when the document was registered.
 */
public data class Document(
    val id: String,
    val uri: String? = null,
    val mimeType: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
