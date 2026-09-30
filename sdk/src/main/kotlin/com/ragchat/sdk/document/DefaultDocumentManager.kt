package com.ragchat.sdk.document

import android.content.Context
import com.ragchat.api.config.RagChatConfig
import com.ragchat.api.document.DocumentManager
import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.IngestionStage
import com.ragchat.api.model.OverallIngestionProgress
import com.ragchat.api.parser.DocumentSource
import com.ragchat.ingestion.IngestionJobParams
import com.ragchat.ingestion.IngestionPipeline
import com.ragchat.ingestion.checkpoint.InMemoryIngestionCheckpointStore
import com.ragchat.ingestion.checkpoint.IngestionCheckpointStore
import com.ragchat.parsers.DefaultParserRegistry
import com.ragchat.storage.StorageManager
import com.ragchat.storage.StoreDocumentParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Default implementation of [DocumentManager] coordinating document persistence,
 * state machine pipeline execution, and background worker scheduling.
 */
public class DefaultDocumentManager(
    private val context: Context,
    private val config: RagChatConfig,
    private val storageManager: StorageManager,
    private val checkpointStore: IngestionCheckpointStore = InMemoryIngestionCheckpointStore(),
) : DocumentManager {
    private val pipeline = IngestionPipeline()

    override suspend fun add(
        source: DocumentSource,
        collectionId: String,
        metadata: Map<String, String>,
    ): Flow<IngestionProgress> =
        flow {
            val docId = UUID.randomUUID().toString()
            val document =
                Document(
                    id = docId,
                    mimeType = source.mimeType,
                    sizeBytes = source.sizeBytes,
                    metadata = metadata,
                )

            // Store raw encrypted document in storage
            source.openStream().use { input ->
                storageManager.storeDocument(
                    params =
                        StoreDocumentParams(
                            scopeId = "default_scope",
                            documentId = docId,
                            collectionId = collectionId,
                            name = metadata["title"] ?: "document_$docId",
                            mimeType = source.mimeType,
                            metadata = metadata,
                        ),
                    inputStream = input,
                )
            }

            val parsers = if (config.parsers.isNotEmpty()) config.parsers else DefaultParserRegistry.createDefaultParsers()
            val chunker = config.chunker ?: error("Chunker not configured in RagChatConfig")
            val embedding = config.embeddingProvider ?: error("EmbeddingProvider not configured")
            val vectorStore = config.vectorStore ?: error("VectorStore not configured")

            val params =
                IngestionJobParams(
                    document = document,
                    source = source,
                    collectionId = collectionId,
                    parsers = parsers,
                    chunker = chunker,
                    embeddingProvider = embedding,
                    vectorStore = vectorStore,
                    checkpointStore = checkpointStore,
                    logger = config.logger,
                )

            pipeline.ingest(params).collect { progress ->
                emit(progress)
            }
        }

    override suspend fun add(
        file: File,
        mimeType: String,
        collectionId: String,
        metadata: Map<String, String>,
    ): Flow<IngestionProgress> {
        val source = LocalFileDocumentSource(file, mimeType)
        return add(source, collectionId, metadata)
    }

    override suspend fun add(
        stream: InputStream,
        name: String,
        mimeType: String,
        collectionId: String,
        metadata: Map<String, String>,
    ): Flow<IngestionProgress> {
        val tempFile = File.createTempFile("ragchat_doc_", ".tmp", context.cacheDir)
        FileOutputStream(tempFile).use { out ->
            stream.copyTo(out)
        }
        val source = LocalFileDocumentSource(tempFile, mimeType)
        return add(source, collectionId, metadata + mapOf("title" to name))
    }

    override suspend fun list(collectionId: String?): List<Document> {
        // Query database via storageManager
        return emptyList()
    }

    override suspend fun status(documentId: String): IngestionProgress? {
        val checkpoint = checkpointStore.getCheckpoint(documentId) ?: return null
        return IngestionProgress(
            documentId = documentId,
            stage = checkpoint.stage,
            itemsProcessed = checkpoint.completedUnits,
            totalItems = checkpoint.totalUnits,
            progress =
                if (checkpoint.totalUnits > 0) {
                    checkpoint.completedUnits.toFloat() / checkpoint.totalUnits.toFloat()
                } else {
                    0.0f
                },
        )
    }

    override suspend fun remove(documentId: String) {
        pipeline.cancel(documentId)
        checkpointStore.clearCheckpoint(documentId)
        storageManager.deleteDocument("default_scope", documentId)
    }

    override suspend fun reindexAll(collectionId: String): Flow<OverallIngestionProgress> =
        flow {
            val documents = list(collectionId)
            val total = documents.size
            var completed = 0

            for (doc in documents) {
                val progress =
                    OverallIngestionProgress(
                        totalDocuments = total,
                        completedDocuments = completed,
                        failedDocuments = 0,
                        activeDocumentProgress = IngestionProgress(doc.id, IngestionStage.INDEXING, completed, total, 0.5f),
                        overallProgress = if (total > 0) completed.toFloat() / total.toFloat() else 1.0f,
                    )
                emit(progress)
                completed++
            }

            emit(
                OverallIngestionProgress(
                    totalDocuments = total,
                    completedDocuments = completed,
                    failedDocuments = 0,
                    activeDocumentProgress = null,
                    overallProgress = 1.0f,
                ),
            )
        }

    private class LocalFileDocumentSource(
        private val file: File,
        override val mimeType: String,
    ) : DocumentSource {
        override val sizeBytes: Long get() = file.length()

        override fun openStream(): InputStream = FileInputStream(file)

        override fun close() {
            // No-op
        }
    }
}
