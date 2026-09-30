package com.ragchat.ingestion

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.api.storage.VectorStore
import com.ragchat.ingestion.backpressure.BackpressureController
import com.ragchat.ingestion.checkpoint.InMemoryIngestionCheckpointStore
import com.ragchat.ingestion.checkpoint.IngestionCheckpoint
import com.ragchat.ingestion.checkpoint.IngestionCheckpointStore
import com.ragchat.ingestion.retry.RetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Parameters configuring a single document ingestion job.
 */
public data class IngestionJobParams(
    val document: Document,
    val source: DocumentSource,
    val collectionId: String,
    val parsers: List<DocumentParser>,
    val chunker: Chunker,
    val embeddingProvider: EmbeddingProvider,
    val vectorStore: VectorStore,
    val checkpointStore: IngestionCheckpointStore = InMemoryIngestionCheckpointStore(),
    val backpressureController: BackpressureController = BackpressureController(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val existingChecksum: String? = null,
    val logger: RagChatLogger? = null,
)

/**
 * Enterprise Ingestion Pipeline State Machine.
 *
 * Manages stage progression:
 * `QUEUED` -> `PARSING` -> `CHUNKING` -> `EMBEDDING` -> `INDEXING` -> `DONE`
 * (or `CANCELLED`, `PAUSED`, `FAILED`).
 */
public class IngestionPipeline {
    private val activeJobs = ConcurrentHashMap<String, JobControlState>()

    private data class JobControlState(
        val isCancelled: AtomicBoolean = AtomicBoolean(false),
        val isPaused: AtomicBoolean = AtomicBoolean(false),
    )

    private fun getOrCreateControl(documentId: String): JobControlState = activeJobs.computeIfAbsent(documentId) { JobControlState() }

    public fun pause(documentId: String) {
        getOrCreateControl(documentId).isPaused.set(true)
    }

    public fun resume(documentId: String) {
        getOrCreateControl(documentId).isPaused.set(false)
    }

    public fun cancel(documentId: String) {
        getOrCreateControl(documentId).isCancelled.set(true)
    }

    /**
     * Executes the ingestion pipeline, emitting an [IngestionProgress] Flow.
     */
    @Suppress("TooGenericExceptionCaught")
    public fun ingest(params: IngestionJobParams): Flow<IngestionProgress> =
        flow {
            val docId = params.document.id
            val control = getOrCreateControl(docId)

            try {
                if (executePreProcessing(params, control) { emit(it) }) {
                    return@flow
                }

                val chunks = parseAndChunk(params, control) { emit(it) }
                if (chunks.isEmpty()) {
                    val emptyProgress = IngestionProgress(docId, IngestionStage.DONE, 0, 0, 1.0f)
                    params.checkpointStore.updateProgress(emptyProgress)
                    emit(emptyProgress)
                    return@flow
                }

                processBatches(params, chunks, control) { emit(it) }

                val finalProgress = IngestionProgress(docId, IngestionStage.DONE, chunks.size, chunks.size, 1.0f)
                params.checkpointStore.updateProgress(finalProgress)
                params.checkpointStore.clearCheckpoint(docId)
                emit(finalProgress)
            } catch (e: JobCancelledException) {
                params.logger?.log(LogLevel.WARN, "IngestionPipeline", "Job $docId cancelled: ${e.message}")
                val cancelledProgress =
                    IngestionProgress(docId, IngestionStage.CANCELLED, errorCategory = "CANCELLED", errorCode = "JOB_CANCELLED")
                params.checkpointStore.updateProgress(cancelledProgress)
                emit(cancelledProgress)
            } catch (e: JobPausedException) {
                params.logger?.log(LogLevel.INFO, "IngestionPipeline", "Job $docId paused: ${e.message}")
                val pausedProgress = IngestionProgress(docId, IngestionStage.PAUSED, errorCategory = "PAUSED", errorCode = "JOB_PAUSED")
                params.checkpointStore.updateProgress(pausedProgress)
                emit(pausedProgress)
            } catch (e: Exception) {
                params.logger?.log(LogLevel.ERROR, "IngestionPipeline", "Ingestion failed: ${e.javaClass.simpleName}")
                val failedProgress =
                    IngestionProgress(
                        docId,
                        IngestionStage.FAILED,
                        errorCategory = "INGESTION_ERROR",
                        errorCode =
                            e.message ?: "UNKNOWN",
                    )
                params.checkpointStore.updateProgress(failedProgress)
                emit(failedProgress)
            } finally {
                activeJobs.remove(docId)
            }
        }

    private suspend fun executePreProcessing(
        params: IngestionJobParams,
        control: JobControlState,
        emitProgress: suspend (IngestionProgress) -> Unit,
    ): Boolean {
        val docId = params.document.id
        val queuedProgress = IngestionProgress(docId, IngestionStage.QUEUED, 0, 1, 0.05f)
        params.checkpointStore.updateProgress(queuedProgress)
        emitProgress(queuedProgress)
        checkJobState(control, docId, params)

        val computedChecksum = calculateSha256(params.source)
        if (params.existingChecksum != null && params.existingChecksum == computedChecksum) {
            params.logger?.log(LogLevel.INFO, "IngestionPipeline", "Deduplicated document with matching checksum.")
            val doneProgress = IngestionProgress(docId, IngestionStage.DONE, 1, 1, 1.0f)
            params.checkpointStore.updateProgress(doneProgress)
            emitProgress(doneProgress)
            return true
        }
        return false
    }

    private suspend fun parseAndChunk(
        params: IngestionJobParams,
        control: JobControlState,
        emitProgress: suspend (IngestionProgress) -> Unit,
    ): List<Chunk> {
        val docId = params.document.id
        val parsingProgress = IngestionProgress(docId, IngestionStage.PARSING, 0, 1, 0.15f)
        params.checkpointStore.updateProgress(parsingProgress)
        emitProgress(parsingProgress)
        checkJobState(control, docId, params)

        val parser =
            params.parsers.firstOrNull { it.supports(params.source.mimeType) }
                ?: throw SdkError.DocumentParsingError(params.source.mimeType, "NO_SUPPORTED_PARSER")

        val elements =
            params.retryPolicy.executeWithRetry {
                parser.parse(params.source).toList()
            }

        val chunkingProgress = IngestionProgress(docId, IngestionStage.CHUNKING, 0, elements.size, 0.30f)
        params.checkpointStore.updateProgress(chunkingProgress)
        emitProgress(chunkingProgress)
        checkJobState(control, docId, params)

        return params.chunker.chunk(params.document, elements)
    }

    private suspend fun processBatches(
        params: IngestionJobParams,
        chunks: List<Chunk>,
        control: JobControlState,
        emitProgress: suspend (IngestionProgress) -> Unit,
    ) {
        val docId = params.document.id
        val lastCheckpoint = params.checkpointStore.getCheckpoint(docId)
        var startIndex =
            if (lastCheckpoint != null && lastCheckpoint.completedUnits > 0) {
                lastCheckpoint.completedUnits
            } else {
                0
            }

        while (startIndex < chunks.size) {
            checkJobState(control, docId, params)

            params.backpressureController.applyThrottleDelay()
            val batchSize = params.backpressureController.resolveBatchSize()
            val endIndex = minOf(startIndex + batchSize, chunks.size)
            val batch = chunks.subList(startIndex, endIndex)

            // Stage: EMBEDDING
            val embeddingProgress =
                IngestionProgress(
                    docId,
                    IngestionStage.EMBEDDING,
                    startIndex,
                    chunks.size,
                    0.30f + 0.40f * (startIndex.toFloat() / chunks.size),
                )
            params.checkpointStore.updateProgress(embeddingProgress)
            emitProgress(embeddingProgress)

            val texts = batch.map { it.content }
            val embeddings =
                params.retryPolicy.executeWithRetry {
                    params.embeddingProvider.embed(texts, EmbeddingTaskType.RETRIEVAL_DOCUMENT)
                }

            // Stage: INDEXING
            val indexingProgress =
                IngestionProgress(
                    docId,
                    IngestionStage.INDEXING,
                    startIndex,
                    chunks.size,
                    0.70f + 0.30f * (startIndex.toFloat() / chunks.size),
                )
            params.checkpointStore.updateProgress(indexingProgress)
            emitProgress(indexingProgress)

            val pairs = batch.zip(embeddings)
            params.retryPolicy.executeWithRetry {
                params.vectorStore.upsert(params.collectionId, pairs)
            }

            params.checkpointStore.saveCheckpoint(
                IngestionCheckpoint(
                    documentId = docId,
                    collectionId = params.collectionId,
                    stage = IngestionStage.INDEXING,
                    completedUnits = endIndex,
                    totalUnits = chunks.size,
                ),
            )
            startIndex = endIndex
        }
    }

    private fun checkJobState(
        control: JobControlState,
        docId: String,
        params: IngestionJobParams,
    ) {
        if (control.isCancelled.get()) {
            params.logger?.log(LogLevel.WARN, "IngestionPipeline", "Job $docId was cancelled.")
            throw JobCancelledException()
        }
        if (control.isPaused.get()) {
            params.logger?.log(LogLevel.INFO, "IngestionPipeline", "Job $docId was paused.")
            throw JobPausedException()
        }
    }

    private fun calculateSha256(source: DocumentSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        source.openStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b))
        }
        return sb.toString()
    }

    private class JobCancelledException : Exception("Job explicitly cancelled")

    private class JobPausedException : Exception("Job paused")
}
