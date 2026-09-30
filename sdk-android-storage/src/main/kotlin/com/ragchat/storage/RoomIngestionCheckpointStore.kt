package com.ragchat.storage

import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage
import com.ragchat.ingestion.checkpoint.IngestionCheckpoint
import com.ragchat.ingestion.checkpoint.IngestionCheckpointStore
import com.ragchat.storage.db.RagChatDatabase
import com.ragchat.storage.db.entity.IngestionJobEntity

/**
 * Checkpoint store persisting pipeline progress and batch states into the encrypted Room database.
 *
 * Guarantees that upon process death or device restart, ingestion can resume exactly from
 * the last committed batch without data loss or duplicate work.
 */
public class RoomIngestionCheckpointStore(
    private val database: RagChatDatabase,
    private val scopeId: String = "default_scope",
) : IngestionCheckpointStore {
    override suspend fun saveCheckpoint(checkpoint: IngestionCheckpoint) {
        val now = System.currentTimeMillis()
        val fraction =
            if (checkpoint.totalUnits > 0) {
                checkpoint.completedUnits.toFloat() / checkpoint.totalUnits.toFloat()
            } else {
                0.0f
            }
        val entity =
            IngestionJobEntity(
                scopeId = scopeId,
                id = "job_${checkpoint.documentId}",
                documentId = checkpoint.documentId,
                status = "${checkpoint.stage.name}:${checkpoint.completedUnits}:${checkpoint.totalUnits}",
                progress = fraction,
                errorCategory = null,
                errorCode = null,
                retryCount = 0,
                createdAt = checkpoint.updatedAt,
                updatedAt = now,
            )
        database.ingestionJobDao().insert(entity)
    }

    override suspend fun getCheckpoint(documentId: String): IngestionCheckpoint? {
        val entity = database.ingestionJobDao().getById(scopeId, "job_$documentId") ?: return null
        val parts = entity.status.split(":")
        val stageName = parts.getOrNull(0) ?: IngestionStage.QUEUED.name
        val completedUnits = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalUnits = parts.getOrNull(2)?.toIntOrNull() ?: 0

        val stage =
            try {
                IngestionStage.valueOf(stageName)
            } catch (_: Exception) {
                IngestionStage.QUEUED
            }

        return IngestionCheckpoint(
            documentId = documentId,
            collectionId = "",
            stage = stage,
            completedUnits = completedUnits,
            totalUnits = totalUnits,
            updatedAt = entity.updatedAt,
        )
    }

    override suspend fun updateProgress(progress: IngestionProgress) {
        val now = System.currentTimeMillis()
        val entity =
            IngestionJobEntity(
                scopeId = scopeId,
                id = "job_${progress.documentId}",
                documentId = progress.documentId,
                status = "${progress.stage.name}:${progress.itemsProcessed}:${progress.totalItems}",
                progress = progress.progress,
                errorCategory = progress.errorCategory,
                errorCode = progress.errorCode,
                retryCount = 0,
                createdAt = now,
                updatedAt = now,
            )
        database.ingestionJobDao().insert(entity)
    }

    override suspend fun clearCheckpoint(documentId: String) {
        database.ingestionJobDao().deleteById(scopeId, "job_$documentId")
    }
}
