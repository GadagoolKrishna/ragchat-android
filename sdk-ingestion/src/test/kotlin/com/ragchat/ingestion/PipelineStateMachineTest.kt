package com.ragchat.ingestion

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Collection
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.CollectionStats
import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionStage
import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.api.storage.SearchResult
import com.ragchat.api.storage.VectorStore
import com.ragchat.ingestion.checkpoint.InMemoryIngestionCheckpointStore
import com.ragchat.ingestion.checkpoint.IngestionCheckpoint
import com.ragchat.ingestion.chunking.FixedSizeChunker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class PipelineStateMachineTest {
    private class StringDocumentSource(
        val content: String,
        override val mimeType: String = "text/plain",
    ) : DocumentSource {
        override val sizeBytes: Long = content.toByteArray().size.toLong()

        override fun openStream(): InputStream = ByteArrayInputStream(content.toByteArray())

        override fun close() {
            // Test no-op
        }
    }

    private class TestParser : DocumentParser {
        override fun supports(mimeType: String): Boolean = true

        override fun parse(source: DocumentSource): Flow<ParsedElement> {
            val text = source.openStream().bufferedReader().use { it.readText() }
            return flowOf(ParsedElement.Text(text = text, pageNumber = 1))
        }
    }

    private class TestEmbeddingProvider(
        private val callCount: AtomicInteger = AtomicInteger(0),
    ) : EmbeddingProvider {
        override val modelId: String = "test-embedder"
        override val version: String = "1.0"
        override val dimensions: Int = 4
        override val maxInputTokens: Int = 512
        override val normalize: Boolean = true

        override fun close() {
            // Test no-op
        }

        override suspend fun embed(
            texts: List<String>,
            taskType: EmbeddingTaskType,
        ): List<FloatArray> {
            callCount.addAndGet(texts.size)
            return texts.map { floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f) }
        }
    }

    private class TestVectorStore : VectorStore {
        val upsertedChunks = mutableListOf<Pair<Chunk, FloatArray>>()

        override suspend fun createCollection(spec: CollectionSpec): Collection = Collection(spec.id, spec, System.currentTimeMillis())

        override suspend fun upsert(
            collectionId: String,
            chunks: List<Pair<Chunk, FloatArray>>,
        ) {
            upsertedChunks.addAll(chunks)
        }

        override suspend fun deleteByDocument(
            collectionId: String,
            documentId: String,
        ) {
            // Test no-op
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

        override suspend fun stats(collectionId: String): CollectionStats = CollectionStats(collectionId, 0, 0, 0)

        override suspend fun export(collectionId: String): Flow<Pair<Chunk, FloatArray>> = emptyFlow()

        override suspend fun cryptoShred(collectionId: String) {
            // Test no-op
        }

        override fun close() {
            // Test no-op
        }
    }

    @Test
    fun testCompletePipelineProgression() =
        runTest {
            val pipeline = IngestionPipeline()
            val checkpointStore = InMemoryIngestionCheckpointStore()
            val vectorStore = TestVectorStore()
            val embeddingProvider = TestEmbeddingProvider()
            val chunker = FixedSizeChunker(chunkSize = 10, chunkOverlap = 2)

            val params =
                IngestionJobParams(
                    document = Document(id = "doc-1", mimeType = "text/plain"),
                    source = StringDocumentSource("This is a simple document text for ingestion testing."),
                    collectionId = "test-col",
                    parsers = listOf(TestParser()),
                    chunker = chunker,
                    embeddingProvider = embeddingProvider,
                    vectorStore = vectorStore,
                    checkpointStore = checkpointStore,
                )

            val stages = mutableListOf<IngestionStage>()
            pipeline.ingest(params).toList().forEach { stages.add(it.stage) }

            assertTrue(stages.contains(IngestionStage.QUEUED))
            assertTrue(stages.contains(IngestionStage.PARSING))
            assertTrue(stages.contains(IngestionStage.CHUNKING))
            assertTrue(stages.contains(IngestionStage.EMBEDDING))
            assertTrue(stages.contains(IngestionStage.INDEXING))
            assertEquals(IngestionStage.DONE, stages.last())
            assertTrue(vectorStore.upsertedChunks.isNotEmpty())
        }

    @Test
    fun testContentHashDeduplication() =
        runTest {
            val pipeline = IngestionPipeline()
            val checkpointStore = InMemoryIngestionCheckpointStore()
            val vectorStore = TestVectorStore()
            val embeddingCount = AtomicInteger(0)
            val embeddingProvider = TestEmbeddingProvider(embeddingCount)
            val chunker = FixedSizeChunker(chunkSize = 10, chunkOverlap = 2)

            val source = StringDocumentSource("Exact same duplicate content")
            // Pre-calculate SHA-256 for the content
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash =
                digest.digest(source.content.toByteArray()).joinToString("") { "%02x".format(it) }

            val params =
                IngestionJobParams(
                    document = Document(id = "doc-dup", mimeType = "text/plain"),
                    source = source,
                    collectionId = "test-col",
                    parsers = listOf(TestParser()),
                    chunker = chunker,
                    embeddingProvider = embeddingProvider,
                    vectorStore = vectorStore,
                    checkpointStore = checkpointStore,
                    existingChecksum = hash, // Matching hash simulates existing document
                )

            val progressList = pipeline.ingest(params).toList()
            assertEquals(2, progressList.size)
            assertEquals(IngestionStage.QUEUED, progressList[0].stage)
            assertEquals(IngestionStage.DONE, progressList[1].stage)
            assertEquals(0, embeddingCount.get()) // No embeddings created
            assertEquals(0, vectorStore.upsertedChunks.size)
        }

    @Test
    fun testProcessDeathResumptionFromCheckpoint() =
        runTest {
            val pipeline = IngestionPipeline()
            val checkpointStore = InMemoryIngestionCheckpointStore()
            val vectorStore = TestVectorStore()
            val embeddingCount = AtomicInteger(0)
            val embeddingProvider = TestEmbeddingProvider(embeddingCount)
            val chunker = FixedSizeChunker(chunkSize = 5, chunkOverlap = 0)

            val longText = (1..50).joinToString(" ") { "word$it" }
            val params =
                IngestionJobParams(
                    document = Document(id = "doc-resume", mimeType = "text/plain"),
                    source = StringDocumentSource(longText),
                    collectionId = "test-col",
                    parsers = listOf(TestParser()),
                    chunker = chunker,
                    embeddingProvider = embeddingProvider,
                    vectorStore = vectorStore,
                    checkpointStore = checkpointStore,
                )

            // Simulate crash after processing first 4 chunks
            checkpointStore.saveCheckpoint(
                IngestionCheckpoint(
                    documentId = "doc-resume",
                    collectionId = "test-col",
                    stage = IngestionStage.INDEXING,
                    completedUnits = 4,
                    totalUnits = 10,
                ),
            )

            pipeline.ingest(params).toList()

            // Verify checkpoint was cleared on DONE
            assertEquals(null, checkpointStore.getCheckpoint("doc-resume"))
        }

    @Test
    fun testJobCancellation() =
        runTest {
            val pipeline = IngestionPipeline()
            val checkpointStore = InMemoryIngestionCheckpointStore()
            val vectorStore = TestVectorStore()
            val embeddingProvider = TestEmbeddingProvider()
            val chunker = FixedSizeChunker(chunkSize = 5, chunkOverlap = 0)

            val params =
                IngestionJobParams(
                    document = Document(id = "doc-cancel", mimeType = "text/plain"),
                    source = StringDocumentSource((1..100).joinToString(" ") { "text$it" }),
                    collectionId = "test-col",
                    parsers = listOf(TestParser()),
                    chunker = chunker,
                    embeddingProvider = embeddingProvider,
                    vectorStore = vectorStore,
                    checkpointStore = checkpointStore,
                )

            // Cancel immediately before or during start
            pipeline.cancel("doc-cancel")

            val stages = pipeline.ingest(params).toList().map { it.stage }
            assertTrue(stages.contains(IngestionStage.CANCELLED))
        }

    @Test
    fun testBulk500FileStressTest() =
        runTest {
            val pipeline = IngestionPipeline()
            val checkpointStore = InMemoryIngestionCheckpointStore()
            val vectorStore = TestVectorStore()
            val embeddingProvider = TestEmbeddingProvider()
            val chunker = FixedSizeChunker(chunkSize = 10, chunkOverlap = 0)

            for (i in 1..500) {
                val params =
                    IngestionJobParams(
                        document = Document(id = "doc-$i", mimeType = "text/plain"),
                        source = StringDocumentSource("Bulk stress test document $i content."),
                        collectionId = "test-col",
                        parsers = listOf(TestParser()),
                        chunker = chunker,
                        embeddingProvider = embeddingProvider,
                        vectorStore = vectorStore,
                        checkpointStore = checkpointStore,
                    )
                val results = pipeline.ingest(params).toList()
                assertEquals(IngestionStage.DONE, results.last().stage)
            }
            assertEquals(500, vectorStore.upsertedChunks.size)
        }
}
