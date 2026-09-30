package com.ragchat.embeddings.registry

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.storage.VectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata record for a validated embedding model specification.
 *
 * @property modelId Unique model identifier.
 * @property version Model version string.
 * @property dimensions Dimensionality produced by the model.
 * @property maxInputTokens Maximum tokens per chunk.
 * @property isLocal Whether the model executes on-device.
 */
public data class ModelMetadata(
    val modelId: String,
    val version: String,
    val dimensions: Int,
    val maxInputTokens: Int,
    val isLocal: Boolean,
)

/**
 * Result of a re-embedding collection upgrade job.
 *
 * @property collectionId Migrated collection ID.
 * @property oldModelId Source model ID.
 * @property newModelId Target upgraded model ID.
 * @property chunksReembedded Number of chunks re-embedded and re-indexed.
 * @property durationMs Time taken in milliseconds.
 */
public data class ReembeddingResult(
    val collectionId: String,
    val oldModelId: String,
    val newModelId: String,
    val chunksReembedded: Int,
    val durationMs: Long,
)

/**
 * Registry managing registered embedding models, collection integrity, and re-embedding jobs.
 *
 * Enforces that:
 * 1. Collections cannot mix different embedding models or dimensions.
 * 2. Provides [reembedCollection] to migrate existing chunks to upgraded model versions.
 */
public class EmbeddingRegistry(
    private val vectorStore: VectorStore,
    private val logger: RagChatLogger? = null,
) {
    private val registeredModels = ConcurrentHashMap<String, ModelMetadata>()

    /**
     * Registers a known embedding provider metadata.
     */
    public fun register(
        provider: EmbeddingProvider,
        isLocal: Boolean = true,
    ) {
        val key = "${provider.modelId}:${provider.version}"
        registeredModels[key] =
            ModelMetadata(
                modelId = provider.modelId,
                version = provider.version,
                dimensions = provider.dimensions,
                maxInputTokens = provider.maxInputTokens,
                isLocal = isLocal,
            )
        logger?.log(LogLevel.INFO, "EmbeddingRegistry", "Registered model $key with ${provider.dimensions} dimensions.")
    }

    /**
     * Validates that an incoming vector or provider matches a collection specification.
     * Throws [SdkError.ValidationError] on mismatch.
     */
    public fun validateCollectionIntegrity(
        spec: CollectionSpec,
        provider: EmbeddingProvider,
    ) {
        if (spec.embeddingModelId != provider.modelId) {
            throw SdkError.ValidationError(
                field = "embeddingModelId",
                details = "Model mismatch: collection requires '${spec.embeddingModelId}', but provider is '${provider.modelId}'",
            )
        }
        if (spec.dimensions != provider.dimensions) {
            throw SdkError.ValidationError(
                field = "dimensions",
                details = "Dimension mismatch: collection requires ${spec.dimensions}, but provider has ${provider.dimensions}",
            )
        }
    }

    /**
     * Executes an end-to-end re-embedding migration job for an existing collection.
     *
     * Exports all existing chunks, computes new dense vectors using [targetProvider],
     * and re-indexes them into the vector store.
     *
     * @param collectionId Target collection to re-embed.
     * @param targetProvider Upgraded embedding provider.
     * @param batchSize Number of chunks to embed and upsert per batch.
     * @return [ReembeddingResult] detailing chunks migrated.
     */
    public suspend fun reembedCollection(
        collectionId: String,
        targetProvider: EmbeddingProvider,
        batchSize: Int = 32,
    ): ReembeddingResult =
        withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            logger?.log(LogLevel.INFO, "EmbeddingRegistry", "Starting re-embedding job for collection '$collectionId'")

            // 1. Export all existing chunks
            val exportedPairs = vectorStore.export(collectionId).toList()
            if (exportedPairs.isEmpty()) {
                return@withContext ReembeddingResult(
                    collectionId = collectionId,
                    oldModelId = "unknown",
                    newModelId = targetProvider.modelId,
                    chunksReembedded = 0,
                    durationMs = System.currentTimeMillis() - startTime,
                )
            }

            val chunks = exportedPairs.map { it.first }
            var processedCount = 0

            // 2. Batch embed and upsert with target provider
            for (batch in chunks.chunked(batchSize)) {
                val texts = batch.map { it.content }
                val newVectors = targetProvider.embed(texts, EmbeddingTaskType.RETRIEVAL_DOCUMENT)

                val updatedPairs = batch.zip(newVectors)
                vectorStore.upsert(collectionId, updatedPairs)
                processedCount += batch.size
            }

            val elapsed = System.currentTimeMillis() - startTime
            logger?.log(LogLevel.INFO, "EmbeddingRegistry", "Re-embedded $processedCount chunks for '$collectionId' in ${elapsed}ms")

            ReembeddingResult(
                collectionId = collectionId,
                oldModelId = exportedPairs.firstOrNull()?.first?.let { "migrated" } ?: "unknown",
                newModelId = targetProvider.modelId,
                chunksReembedded = processedCount,
                durationMs = elapsed,
            )
        }
}
