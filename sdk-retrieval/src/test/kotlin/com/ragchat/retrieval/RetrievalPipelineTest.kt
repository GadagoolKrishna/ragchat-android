package com.ragchat.retrieval

import com.ragchat.api.error.SdkError
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Collection
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.CollectionStats
import com.ragchat.api.storage.SearchResult
import com.ragchat.api.storage.VectorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalPipelineTest {
    private class FakeVectorStore(
        private val mockResults: List<SearchResult>,
    ) : VectorStore {
        override suspend fun createCollection(spec: CollectionSpec): Collection = TODO()

        override suspend fun upsert(
            collectionId: String,
            chunks: List<Pair<Chunk, FloatArray>>,
        ) {
            // No-op for retrieval unit tests
        }

        override suspend fun deleteByDocument(
            collectionId: String,
            documentId: String,
        ) {
            // No-op for retrieval unit tests
        }

        override suspend fun query(
            collectionId: String,
            vector: FloatArray,
            topK: Int,
            filter: Map<String, String>?,
        ): List<SearchResult> = mockResults

        override suspend fun hybridFtsQuery(
            collectionId: String,
            queryText: String,
            vector: FloatArray,
            topK: Int,
            filter: Map<String, String>?,
        ): List<SearchResult> {
            if (filter == null) return mockResults
            return mockResults.filter { result ->
                filter.all { (k, v) -> result.chunk.metadata[k] == v }
            }
        }

        override suspend fun stats(collectionId: String): CollectionStats =
            CollectionStats(collectionId, mockResults.size.toLong(), 1L, 1024L)

        override suspend fun export(collectionId: String): Flow<Pair<Chunk, FloatArray>> = emptyFlow()

        override suspend fun cryptoShred(collectionId: String) {
            // No-op for retrieval unit tests
        }

        override fun close() {
            // No-op for retrieval unit tests
        }
    }

    private fun sampleChunk(
        id: String,
        content: String,
        docId: String = "doc-1",
        seq: Int = 0,
        metadata: Map<String, String> = emptyMap(),
    ): Chunk =
        Chunk(
            id = id,
            documentId = docId,
            content = content,
            sequenceNumber = seq,
            tokenCount = content.split(" ").size,
            metadata = metadata,
        )

    @Test
    fun `hybrid retrieval combines dense and FTS ranks with RRF`() =
        runBlocking {
            val c1 = sampleChunk("c1", "Android memory architecture and garbage collection")
            val c2 = sampleChunk("c2", "Vector embeddings and HNSW indexing for search")
            val c3 = sampleChunk("c3", "Coroutines Flow reactive pipelines")

            val results =
                listOf(
                    SearchResult(chunk = c1, score = 0.85f),
                    SearchResult(chunk = c2, score = 0.80f),
                    SearchResult(chunk = c3, score = 0.75f),
                )

            val pipeline =
                RetrievalPipeline(
                    vectorStore = FakeVectorStore(results),
                    config = RetrievalConfig(topK = 2, confidenceThreshold = 0.005f),
                )

            val retrieved =
                pipeline.retrieve(
                    collectionId = "test-col",
                    query = "Android memory",
                    queryVector = FloatArray(384) { 0.1f },
                )

            assertEquals(2, retrieved.size)
            assertEquals("c1", retrieved[0].chunk.id)
        }

    @Test(expected = SdkError.InsufficientEvidenceError::class)
    fun `pipeline throws InsufficientEvidenceError when scores below threshold`(): Unit =
        runBlocking {
            val c1 = sampleChunk("c1", "Low relevance content")
            val results = listOf(SearchResult(chunk = c1, score = 0.001f))

            val pipeline =
                RetrievalPipeline(
                    vectorStore = FakeVectorStore(results),
                    config = RetrievalConfig(confidenceThreshold = 0.5f),
                )

            pipeline.retrieve(
                collectionId = "test-col",
                query = "Unrelated query",
                queryVector = FloatArray(384),
            )
        }

    @Test
    fun `acl filter enforces tenant and role constraints`() =
        runBlocking {
            val matchingChunk =
                sampleChunk(
                    "m1",
                    "Confidential salary info",
                    metadata = mapOf("tenant_id" to "tenant-1", "acl_roles" to "hr,admin"),
                )
            val nonMatchingRoleChunk =
                sampleChunk(
                    "m2",
                    "General info",
                    metadata = mapOf("tenant_id" to "tenant-1", "acl_roles" to "guest"),
                )

            val results =
                listOf(
                    SearchResult(matchingChunk, 0.9f),
                    SearchResult(nonMatchingRoleChunk, 0.85f),
                )

            val acl =
                AclFilter(
                    tenantId = "tenant-1",
                    allowedRoles = setOf("hr"),
                )

            val pipeline =
                RetrievalPipeline(
                    vectorStore = FakeVectorStore(results),
                    config = RetrievalConfig(confidenceThreshold = 0.01f),
                )

            val retrieved =
                pipeline.retrieve(
                    collectionId = "test-col",
                    query = "Salary",
                    queryVector = FloatArray(384),
                    aclFilter = acl,
                )

            assertEquals(1, retrieved.size)
            assertEquals("m1", retrieved[0].chunk.id)
        }

    @Test
    fun `context assembler packs chunks and performs neighbor expansion`() =
        runBlocking {
            val c0 = sampleChunk("c0", "Section 1 Overview", seq = 0)
            val c1 = sampleChunk("c1", "Section 2 Core Architecture", seq = 1)
            val c2 = sampleChunk("c2", "Section 3 Conclusion", seq = 2)

            val assembler =
                ContextAssembler(
                    maxTokenBudget = 100,
                    neighborExpansionWindow = 1,
                )

            val primaryResult = SearchResult(chunk = c1, score = 0.95f)
            val lookup: suspend (String, Int) -> Chunk? = { _, seq ->
                when (seq) {
                    0 -> c0
                    2 -> c2
                    else -> null
                }
            }

            val assembled = assembler.assemble(listOf(primaryResult), lookup)
            assertEquals(3, assembled.includedChunks.size)
            assertTrue(assembled.assembledText.contains("Section 1 Overview"))
            assertTrue(assembled.assembledText.contains("Section 2 Core Architecture"))
            assertTrue(assembled.assembledText.contains("Section 3 Conclusion"))
        }

    @Test
    fun `mmr diversifier penalizes redundancy`() {
        val c1 = sampleChunk("c1", "Kotlin coroutines flow dispatchers")
        val c2 = sampleChunk("c2", "Kotlin coroutines flow dispatchers and scope")
        val c3 = sampleChunk("c3", "SQLite database encryption using SQLCipher")

        val results =
            listOf(
                SearchResult(c1, 0.95f),
                SearchResult(c2, 0.90f),
                SearchResult(c3, 0.85f),
            )

        val diversified = MmrDiversifier.diversify(results, topK = 2, lambda = 0.5f)

        assertEquals(2, diversified.size)
        assertEquals("c1", diversified[0].chunk.id)
        assertEquals("c3", diversified[1].chunk.id)
    }
}
