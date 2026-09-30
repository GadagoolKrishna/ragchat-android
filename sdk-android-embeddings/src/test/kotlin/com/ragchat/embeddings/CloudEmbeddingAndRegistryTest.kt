package com.ragchat.embeddings

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Collection
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.CollectionStats
import com.ragchat.api.model.DistanceMetric
import com.ragchat.api.storage.SearchResult
import com.ragchat.api.storage.VectorStore
import com.ragchat.embeddings.cloud.CloudEmbeddingProvider
import com.ragchat.embeddings.registry.EmbeddingRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudEmbeddingAndRegistryTest {
    private class FakeVectorStore : VectorStore {
        val storage = mutableMapOf<String, MutableList<Pair<Chunk, FloatArray>>>()

        override suspend fun createCollection(spec: CollectionSpec): Collection {
            storage.putIfAbsent(spec.id, mutableListOf())
            return Collection(spec.id, spec, System.currentTimeMillis(), 0)
        }

        override suspend fun upsert(
            collectionId: String,
            chunks: List<Pair<Chunk, FloatArray>>,
        ) {
            val list = storage.getOrPut(collectionId) { mutableListOf() }
            list.removeAll { existing -> chunks.any { it.first.id == existing.first.id } }
            list.addAll(chunks)
        }

        override suspend fun deleteByDocument(
            collectionId: String,
            documentId: String,
        ) {
            storage[collectionId]?.removeAll { it.first.documentId == documentId }
        }

        override suspend fun query(
            collectionId: String,
            vector: FloatArray,
            topK: Int,
            filter: Map<String, String>?,
        ): List<SearchResult> = emptyList()

        override suspend fun hybridFtsQuery(
            collectionId: String,
            queryText: String,
            vector: FloatArray,
            topK: Int,
            filter: Map<String, String>?,
        ): List<SearchResult> = emptyList()

        override suspend fun stats(collectionId: String): CollectionStats =
            CollectionStats(
                collectionId = collectionId,
                totalChunks = (storage[collectionId]?.size ?: 0).toLong(),
                totalDocuments = 1L,
                storageSizeBytes = 1024L,
            )

        override suspend fun export(collectionId: String): Flow<Pair<Chunk, FloatArray>> = (storage[collectionId] ?: emptyList()).asFlow()

        override suspend fun cryptoShred(collectionId: String) {
            storage.remove(collectionId)
        }

        override fun close() {
            storage.clear()
        }
    }

    private class FakeAuthProvider(
        private val token: String? = "Bearer test-token-123",
    ) : AuthProvider {
        override suspend fun getAuthorizationHeader(): String? = token
    }

    private class FakePolicyProvider(
        private val allow: Boolean,
    ) : PolicyProvider {
        override suspend fun evaluate(
            intent: String,
            context: Map<String, String>,
        ): PolicyDecision =
            if (allow) {
                PolicyDecision.Allowed
            } else {
                PolicyDecision.Denied("RULE_LOCAL_ONLY", "DATA_EXFILTRATION_PROHIBITED")
            }
    }

    private class FakeEmbeddingProvider(
        override val modelId: String,
        override val version: String = "1.0.0",
        override val dimensions: Int = 256,
        override val maxInputTokens: Int = 512,
        override val normalize: Boolean = true,
    ) : EmbeddingProvider {
        override suspend fun embed(
            texts: List<String>,
            taskType: EmbeddingTaskType,
        ): List<FloatArray> = texts.map { FloatArray(dimensions) { 0.1f } }

        override fun close() {
            // No resources to close
        }
    }

    @Test
    fun testCloudEmbeddingBlockedByPolicy() {
        val provider =
            CloudEmbeddingProvider(
                endpoint = "https://example.com/embed",
                authProvider = FakeAuthProvider(),
                policyProvider = FakePolicyProvider(allow = false),
            )

        assertThrows(SdkError.PolicyViolationError::class.java) {
            runBlocking {
                provider.embed(listOf("Sensitive company document text"), EmbeddingTaskType.RETRIEVAL_DOCUMENT)
            }
        }
    }

    @Test
    fun testRegistryCollectionValidation() {
        val registry = EmbeddingRegistry(FakeVectorStore())
        val provider = FakeEmbeddingProvider(modelId = "embeddinggemma-300m", dimensions = 256)

        val matchingSpec =
            CollectionSpec(
                id = "col1",
                embeddingModelId = "embeddinggemma-300m",
                embeddingModelVersion = "1.0.0",
                dimensions = 256,
                distanceMetric = DistanceMetric.COSINE,
            )

        val mismatchedSpec =
            CollectionSpec(
                id = "col2",
                embeddingModelId = "other-model",
                embeddingModelVersion = "1.0.0",
                dimensions = 256,
                distanceMetric = DistanceMetric.COSINE,
            )

        registry.validateCollectionIntegrity(matchingSpec, provider)

        assertThrows(SdkError.ValidationError::class.java) {
            registry.validateCollectionIntegrity(mismatchedSpec, provider)
        }
    }

    @Test
    fun testReembeddingUpgradeJob() =
        runBlocking {
            val vectorStore = FakeVectorStore()
            val registry = EmbeddingRegistry(vectorStore)

            val initialChunk =
                Chunk(
                    id = "c1",
                    documentId = "doc1",
                    content = "Document content to be re-embedded with newer model.",
                    sequenceNumber = 0,
                    tokenCount = 10,
                )
            vectorStore.upsert("test_col", listOf(Pair(initialChunk, FloatArray(128))))

            val targetProvider = FakeEmbeddingProvider(modelId = "embeddinggemma-v2", dimensions = 512)
            val result = registry.reembedCollection("test_col", targetProvider)

            assertEquals("test_col", result.collectionId)
            assertEquals("embeddinggemma-v2", result.newModelId)
            assertEquals(1, result.chunksReembedded)

            val storedPairs = mutableListOf<Pair<Chunk, FloatArray>>()
            vectorStore.export("test_col").collect { storedPairs.add(it) }

            assertEquals(1, storedPairs.size)
            assertEquals(512, storedPairs[0].second.size)
        }
}
