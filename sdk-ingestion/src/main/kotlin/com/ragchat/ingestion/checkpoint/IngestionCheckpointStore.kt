package com.ragchat.ingestion.checkpoint

import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage

/**
 * Checkpoint record tracking a document's stage and batch progress.
 *
 * @property documentId Document identifier.
 * @property collectionId Target collection identifier.
 * @property stage Current pipeline stage.
 * @property completedUnits Completed units (e.g. parsed pages, embedded chunks).
 * @property totalUnits Total items in current stage.
 * @property payloadJson Optional JSON payload containing serialized state for resume.
 * @property updatedAt Timestamp of last checkpoint.
 */
public data class IngestionCheckpoint(
    val documentId: String,
    val collectionId: String,
    val stage: IngestionStage,
    val completedUnits: Int,
    val totalUnits: Int,
    val payloadJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Storage SPI for persisting and recovering document ingestion checkpoints across process restarts.
 */
public interface IngestionCheckpointStore {
    /**
     * Saves or updates a document checkpoint.
     */
    public suspend fun saveCheckpoint(checkpoint: IngestionCheckpoint)

    /**
     * Retrieves the latest checkpoint for a document.
     */
    public suspend fun getCheckpoint(documentId: String): IngestionCheckpoint?

    /**
     * Updates document progress state.
     */
    public suspend fun updateProgress(progress: IngestionProgress)

    /**
     * Clears or marks a checkpoint completed.
     */
    public suspend fun clearCheckpoint(documentId: String)
}

/**
 * In-memory checkpoint store used for testing and transient pipeline execution.
 */
public class InMemoryIngestionCheckpointStore : IngestionCheckpointStore {
    private val checkpoints = mutableMapOf<String, IngestionCheckpoint>()
    private val progresses = mutableMapOf<String, IngestionProgress>()

    override suspend fun saveCheckpoint(checkpoint: IngestionCheckpoint) {
        checkpoints[checkpoint.documentId] = checkpoint
    }

    override suspend fun getCheckpoint(documentId: String): IngestionCheckpoint? = checkpoints[documentId]

    override suspend fun updateProgress(progress: IngestionProgress) {
        progresses[progress.documentId] = progress
    }

    override suspend fun clearCheckpoint(documentId: String) {
        checkpoints.remove(documentId)
    }

    /**
     * Retrieves recorded progress for a document.
     */
    public fun getProgress(documentId: String): IngestionProgress? = progresses[documentId]
}
