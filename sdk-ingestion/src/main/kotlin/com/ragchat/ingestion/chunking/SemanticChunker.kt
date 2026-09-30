package com.ragchat.ingestion.chunking

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement
import com.ragchat.ingestion.tokenizer.CharacterRatioTokenEstimator
import com.ragchat.ingestion.tokenizer.TokenEstimator
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/**
 * Semantic chunker that groups sentences by vector similarity computed via [EmbeddingProvider].
 *
 * When the cosine similarity between adjacent sentences drops below [similarityThreshold],
 * a semantic boundary is recognized to split chunks.
 *
 * @param embeddingProvider Provider generating sentence embeddings.
 * @param similarityThreshold Cosine similarity cutoff (0.0 to 1.0) under which a new chunk is started (default 0.7).
 * @param maxChunkTokens Upper bound on token count per chunk (default 512).
 * @param tokenEstimator Token estimator instance.
 */
public class SemanticChunker(
    private val embeddingProvider: EmbeddingProvider,
    public val similarityThreshold: Float = 0.7f,
    public val maxChunkTokens: Int = 512,
    public val tokenEstimator: TokenEstimator = CharacterRatioTokenEstimator(),
) : Chunker {
    override fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk> {
        val fullText =
            elements
                .mapNotNull { el ->
                    when (el) {
                        is ParsedElement.Text -> el.text
                        is ParsedElement.Heading -> el.title
                        else -> null
                    }
                }.joinToString("\n\n")

        val sentences = splitSentences(fullText)
        if (sentences.isEmpty()) return emptyList()

        return groupSentencesIntoChunks(document.id, sentences)
    }

    private fun groupSentencesIntoChunks(
        docId: String,
        sentences: List<String>,
    ): List<Chunk> {
        val embeddings =
            runBlocking {
                embeddingProvider.embed(sentences)
            }

        val chunks = mutableListOf<Chunk>()
        var currentSentenceGroup = mutableListOf<String>()
        var seq = 0

        for (i in sentences.indices) {
            val sentence = sentences[i]
            val currentGroupText = (currentSentenceGroup + listOf(sentence)).joinToString(" ")
            val currentGroupTokens = tokenEstimator.estimateTokens(currentGroupText)

            if (currentSentenceGroup.isNotEmpty()) {
                val sim = cosineSimilarity(embeddings[i - 1], embeddings[i])
                if (sim < similarityThreshold || currentGroupTokens > maxChunkTokens) {
                    val chunkText = currentSentenceGroup.joinToString(" ")
                    chunks.add(createChunk(docId, seq++, chunkText))
                    currentSentenceGroup = mutableListOf(sentence)
                    continue
                }
            }
            currentSentenceGroup.add(sentence)
        }

        if (currentSentenceGroup.isNotEmpty()) {
            val chunkText = currentSentenceGroup.joinToString(" ")
            chunks.add(createChunk(docId, seq, chunkText))
        }

        return chunks
    }

    private fun splitSentences(text: String): List<String> =
        if (text.isBlank()) {
            emptyList()
        } else {
            text
                .split(SENTENCE_DELIMITER_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }

    private fun createChunk(
        docId: String,
        seq: Int,
        content: String,
    ): Chunk =
        Chunk(
            id = "${docId}_sem_$seq",
            documentId = docId,
            content = content,
            sequenceNumber = seq,
            tokenCount = tokenEstimator.estimateTokens(content),
            metadata =
                mapOf(
                    "content_hash" to ChunkUtils.sha256Hex(content),
                    "is_semantic" to "true",
                ),
        )

    private companion object {
        private val SENTENCE_DELIMITER_REGEX = "(?<=[.!?])\\s+".toRegex()
    }
}
