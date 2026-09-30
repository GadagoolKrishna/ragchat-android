package com.ragchat.api.model

/**
 * Complete answer produced by the RagChat pipeline.
 *
 * @property text The finalized textual response.
 * @property citations Source references grounding the answer.
 * @property confidence Confidence score between 0.0 and 1.0, or null if unsupported.
 * @property modelUsed The model identifier that generated the response.
 * @property locality Locality of the model execution (local or cloud).
 */
public data class Answer(
    val text: String,
    val citations: List<Citation>,
    val confidence: Float?,
    val modelUsed: String,
    val locality: Locality,
)
