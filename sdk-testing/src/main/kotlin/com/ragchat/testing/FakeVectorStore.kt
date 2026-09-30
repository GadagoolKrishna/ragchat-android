package com.ragchat.testing

import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Collection
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.CollectionStats
import com.ragchat.api.storage.SearchResult
import com.ragchat.api.storage.VectorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Thread-safe in-memory test fake implementation of [VectorStore].
 */
public class FakeVectorStore : VectorStore {
    private val isClosed = AtomicBoolean(false)
    private val collections = ConcurrentHashMap<String, Collection>()
    private val storage = ConcurrentHashMap<String, MutableList<Pair<Chunk, FloatArray>>>()

    override suspend fun createCollection(spec: CollectionSpec): Collection {
        check(!isClosed.get()) { "FakeVectorStore is closed" }
        return collections.computeIfAbsent(spec.id) {
            Collection(
                id = spec.id,
                spec = spec,
                createdAtEpochMs = System.currentTimeMillis(),
                chunkCount = 0,
            )
        }
    }

    override suspend fun upsert(
        collectionId: String,
        chunks: List<Pair<Chunk, FloatArray>>,
    ) {
        check(!isClosed.get()) { "FakeVectorStore is closed" }
        val list = storage.computeIfAbsent(collectionId) { mutableListOf() }
        synchronized(list) {
            for (newChunk in chunks) {
                list.removeIf { it.first.id == newChunk.first.id }
                list.add(newChunk)
            }
        }
    }

    override suspend fun deleteByDocument(
        collectionId: String,
        documentId: String,
    ) {
        check(!isClosed.get()) { "FakeVectorStore is closed" }
        storage[collectionId]?.let { list ->
            synchronized(list) {
                list.removeIf { it.first.documentId == documentId }
            }
        }
    }

    override suspend fun query(
        collectionId: String,
        vector: FloatArray,
        topK: Int,
        filter: Map<String, String>?,
    ): List<SearchResult> {
        check(!isClosed.get()) { "FakeVectorStore is closed" }
        val list = storage[collectionId] ?: return emptyList()
        val items = synchronized(list) { list.toList() }

        return items
            .filter { (chunk, _) ->
                if (filter == null) {
                    true
                } else {
                    filter.all { (k, v) -> chunk.metadata[k] == v }
                }
            }.map { (chunk, chunkVec) ->
                val similarity = cosineSimilarity(vector, chunkVec)
                SearchResult(chunk = chunk, score = similarity, distance = 1f - similarity)
            }.sortedByDescending { it.score }
            .take(topK)
    }

    override suspend fun hybridFtsQuery(
        collectionId: String,
        queryText: String,
        vector: FloatArray,
        topK: Int,
        filter: Map<String, String>?,
    ): List<SearchResult> = query(collectionId, vector, topK, filter)

    override suspend fun stats(collectionId: String): CollectionStats {
        val list = storage[collectionId] ?: emptyList()
        val docs = list.map { it.first.documentId }.toSet()
        return CollectionStats(
            collectionId = collectionId,
            totalChunks = list.size.toLong(),
            totalDocuments = docs.size.toLong(),
            storageSizeBytes = list.size * 1024L,
        )
    }

    override suspend fun export(collectionId: String): Flow<Pair<Chunk, FloatArray>> {
        val list = storage[collectionId] ?: emptyList()
        return list.asFlow()
    }

    override suspend fun cryptoShred(collectionId: String) {
        collections.remove(collectionId)
        storage.remove(collectionId)
    }

    override fun close() {
        isClosed.set(true)
    }

    private fun cosineSimilarity(
        v1: FloatArray,
        v2: FloatArray,
    ): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        val size = minOf(v1.size, v2.size)
        for (i in 0 until size) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
        return if (denom > 0f) dot / denom else 0f
    }
}
