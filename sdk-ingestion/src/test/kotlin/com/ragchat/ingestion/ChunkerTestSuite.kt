package com.ragchat.ingestion

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement
import com.ragchat.ingestion.chunking.FixedSizeChunker
import com.ragchat.ingestion.chunking.RecursiveChunker
import com.ragchat.ingestion.chunking.SemanticChunker
import com.ragchat.ingestion.chunking.StructureAwareChunker
import com.ragchat.ingestion.tokenizer.CharacterRatioTokenEstimator
import com.ragchat.ingestion.tokenizer.WordBoundaryTokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChunkerTestSuite {
    @Test
    fun testCharacterRatioTokenEstimator() {
        val estimator = CharacterRatioTokenEstimator()
        val english = "Hello world from RagChat"
        val hindi = "नमस्ते दुनिया"
        val kannada = "ನಮಸ್ಕಾರ ವಿಶ್ವ"

        val engTokens = estimator.estimateTokens(english)
        val hindiTokens = estimator.estimateTokens(hindi)
        val kannadaTokens = estimator.estimateTokens(kannada)

        assertTrue(engTokens > 0)
        assertTrue(hindiTokens > 0)
        assertTrue(kannadaTokens > 0)
    }

    @Test
    fun testWordBoundaryTokenEstimator() {
        val estimator = WordBoundaryTokenEstimator()
        val text = "The quick brown fox jumps over the lazy dog"
        val tokens = estimator.estimateTokens(text)
        assertEquals(11, tokens) // 9 words * 1.33 = 11.97 -> 11
    }

    @Test
    fun testFixedSizeChunker() {
        val chunker = FixedSizeChunker(chunkSize = 20, chunkOverlap = 5)
        val doc = Document(id = "doc1", mimeType = "text/plain")
        val elements =
            listOf(
                ParsedElement.Text("This is page one text that is slightly longer to cause multiple chunks.", pageNumber = 1),
                ParsedElement.PageBreak(pageNumber = 1),
                ParsedElement.Text("This is page two text with more content for evaluation.", pageNumber = 2),
            )

        val chunks = chunker.chunk(doc, elements)
        assertTrue(chunks.isNotEmpty())
        for (c in chunks) {
            assertEquals("doc1", c.documentId)
            assertNotNull(c.metadata["char_offset_start"])
            assertNotNull(c.metadata["char_offset_end"])
            assertNotNull(c.metadata["content_hash"])
            assertNotNull(c.metadata["page_start"])
            assertNotNull(c.metadata["page_end"])
        }
    }

    @Test
    fun testRecursiveChunker() {
        val chunker = RecursiveChunker(maxChunkTokens = 15)
        val doc = Document(id = "doc2", mimeType = "text/plain")
        val text = "Paragraph 1 is here.\n\nParagraph 2 is here. It has another sentence.\n\nParagraph 3 is here."
        val elements = listOf(ParsedElement.Text(text))

        val chunks = chunker.chunk(doc, elements)
        assertTrue(chunks.size >= 2)
        for (c in chunks) {
            assertTrue(c.tokenCount <= 15)
            assertNotNull(c.metadata["content_hash"])
        }
    }

    @Test
    fun testStructureAwareChunker() {
        val chunker = StructureAwareChunker(maxChunkTokens = 40)
        val doc = Document(id = "doc3", mimeType = "text/markdown")
        val elements =
            listOf(
                ParsedElement.Heading("Architecture", level = 1),
                ParsedElement.Text("Architecture intro text here."),
                ParsedElement.Heading("Storage Engine", level = 2),
                ParsedElement.Text("Details regarding SQLCipher and HNSW index."),
                ParsedElement.Table(
                    headers = listOf("ColumnA", "ColumnB"),
                    rows =
                        listOf(
                            listOf("Val1", "Val2"),
                            listOf("Val3", "Val4"),
                        ),
                ),
            )

        val chunks = chunker.chunk(doc, elements)
        assertTrue(chunks.isNotEmpty())

        val tableChunk = chunks.find { it.metadata["is_table"] == "true" }
        assertNotNull(tableChunk)
        assertTrue(tableChunk.content.contains("ColumnA | ColumnB"))
        assertTrue(tableChunk.content.contains("Val1 | Val2"))

        val secChunk = chunks.find { it.metadata["section_path"]?.contains("Storage Engine") == true }
        assertNotNull(secChunk)
    }

    @Test
    fun testSemanticChunker() {
        val mockProvider =
            object : EmbeddingProvider {
                override val modelId = "mock"
                override val version = "1.0"
                override val dimensions = 2
                override val maxInputTokens = 128
                override val normalize = true

                override suspend fun embed(
                    texts: List<String>,
                    taskType: EmbeddingTaskType,
                ): List<FloatArray> =
                    texts.mapIndexed { idx, _ ->
                        // Alternate vector directions to simulate semantic shift
                        if (idx % 2 == 0) floatArrayOf(1.0f, 0.0f) else floatArrayOf(0.0f, 1.0f)
                    }

                override fun close() {
                    // No-op for mock provider
                }
            }

        val chunker =
            SemanticChunker(
                embeddingProvider = mockProvider,
                similarityThreshold = 0.5f,
            )
        val doc = Document(id = "doc4", mimeType = "text/plain")
        val elements =
            listOf(
                ParsedElement.Text("First topic sentence. Second topic sentence about dogs. Third topic about quantum physics."),
            )

        val chunks = chunker.chunk(doc, elements)
        assertTrue(chunks.size >= 2)
    }
}
