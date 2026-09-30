package com.ragchat.api.model

/**
 * Distance metrics supported for vector similarity queries.
 */
public enum class DistanceMetric {
    /**
     * Cosine similarity metric.
     */
    COSINE,

    /**
     * Euclidean (L2) distance metric.
     */
    EUCLIDEAN,

    /**
     * Dot product metric.
     */
    DOT_PRODUCT,
}

/**
 * Specification for creating and validating a vector collection index.
 *
 * @property id Unique name or identifier of the collection.
 * @property embeddingModelId Model identifier of the embedding model used for indexing.
 * @property embeddingModelVersion Version string of the embedding model.
 * @property dimensions Dimensionality of the vector embeddings stored in this collection.
 * @property distanceMetric Distance metric used for vector distance ranking.
 */
public data class CollectionSpec(
    val id: String,
    val embeddingModelId: String,
    val embeddingModelVersion: String,
    val dimensions: Int,
    val distanceMetric: DistanceMetric = DistanceMetric.COSINE,
)

/**
 * Represents an indexed collection in the vector store.
 *
 * @property id Unique identifier of the collection.
 * @property spec Specification describing the embedding model and dimensions.
 * @property createdAtEpochMs Creation timestamp in milliseconds.
 * @property chunkCount Current number of chunks indexed in the collection.
 */
public data class Collection(
    val id: String,
    val spec: CollectionSpec,
    val createdAtEpochMs: Long,
    val chunkCount: Long = 0,
)

/**
 * Statistical metadata for an indexed collection.
 *
 * @property collectionId Identifier of the collection.
 * @property totalChunks Total number of chunks indexed.
 * @property totalDocuments Total number of distinct parent documents represented.
 * @property storageSizeBytes Approximate storage footprint in bytes.
 */
public data class CollectionStats(
    val collectionId: String,
    val totalChunks: Long,
    val totalDocuments: Long,
    val storageSizeBytes: Long,
)
