package com.ragchat.api.model

/**
 * Sequential execution stages of the document ingestion pipeline.
 */
public enum class IngestionStage {
    /**
     * Job is registered in the queue waiting for execution resources.
     */
    QUEUED,

    /**
     * Parsing raw document stream and extracting structured elements.
     */
    PARSING,

    /**
     * Partitioning structured elements into text passages/chunks.
     */
    CHUNKING,

    /**
     * Generating dense vector embeddings for chunk passages.
     */
    EMBEDDING,

    /**
     * Persisting chunk texts, metadata, full-text FTS5 index, and dense vectors.
     */
    INDEXING,

    /**
     * Ingestion finished successfully.
     */
    DONE,

    /**
     * Ingestion halted due to an unrecoverable error or exceeded retry attempts.
     */
    FAILED,

    /**
     * Ingestion was explicitly cancelled by the user or application.
     */
    CANCELLED,

    /**
     * Ingestion is paused waiting for device conditions or user resumption.
     */
    PAUSED,
}

/**
 * Progress status event for a single document being ingested.
 *
 * @property documentId Unique identifier of the document.
 * @property stage Current pipeline lifecycle stage.
 * @property itemsProcessed Number of units (e.g. pages, chunks, vectors) processed in the current stage.
 * @property totalItems Total number of units in the current stage, or -1 if indeterminate.
 * @property progress Normalized progress between 0.0f and 1.0f.
 * @property errorCategory Sanitized error classification if [stage] is [IngestionStage.FAILED].
 * @property errorCode Sanitized machine-readable error code if [stage] is [IngestionStage.FAILED].
 */
public data class IngestionProgress(
    val documentId: String,
    val stage: IngestionStage,
    val itemsProcessed: Int = 0,
    val totalItems: Int = 0,
    val progress: Float = 0.0f,
    val errorCategory: String? = null,
    val errorCode: String? = null,
)

/**
 * Aggregated progress across a multi-document bulk ingestion batch.
 *
 * @property totalDocuments Total number of documents in the batch.
 * @property completedDocuments Number of documents successfully ingested.
 * @property failedDocuments Number of documents that failed ingestion.
 * @property activeDocumentProgress Progress of the document currently being processed, or null.
 * @property overallProgress Aggregated completion fraction between 0.0f and 1.0f.
 */
public data class OverallIngestionProgress(
    val totalDocuments: Int,
    val completedDocuments: Int,
    val failedDocuments: Int,
    val activeDocumentProgress: IngestionProgress?,
    val overallProgress: Float,
)
