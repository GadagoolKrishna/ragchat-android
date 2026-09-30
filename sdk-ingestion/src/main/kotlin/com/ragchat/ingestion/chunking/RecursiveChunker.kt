package com.ragchat.ingestion.chunking

import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement
import com.ragchat.ingestion.tokenizer.CharacterRatioTokenEstimator
import com.ragchat.ingestion.tokenizer.TokenEstimator

/**
 * Recursive text chunker that attempts to split on natural linguistic boundaries
 * in descending order: `\n\n` (paragraphs), `\n` (lines), `. ` (sentences), ` ` (words).
 *
 * @param maxChunkTokens Maximum allowed tokens per chunk (default 512).
 * @param tokenEstimator Token estimator instance.
 */
public class RecursiveChunker(
    public val maxChunkTokens: Int = 512,
    public val tokenEstimator: TokenEstimator = CharacterRatioTokenEstimator(),
) : Chunker {
    private val separators = listOf("\n\n", "\n", ". ", " ", "")

    override fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk> {
        val combinedText =
            elements
                .mapNotNull { el ->
                    when (el) {
                        is ParsedElement.Text -> el.text
                        is ParsedElement.Heading -> el.title
                        is ParsedElement.Table -> el.rows.joinToString("\n") { it.joinToString(", ") }
                        else -> null
                    }
                }.joinToString("\n\n")

        if (combinedText.isBlank()) return emptyList()

        val textChunks = splitRecursively(combinedText, separators)
        var seq = 0
        var charOffset = 0

        return textChunks.map { text ->
            val startOffset = combinedText.indexOf(text, charOffset).coerceAtLeast(charOffset)
            val endOffset = startOffset + text.length
            charOffset = endOffset

            val tokens = tokenEstimator.estimateTokens(text)
            val meta =
                mapOf(
                    "char_offset_start" to startOffset.toString(),
                    "char_offset_end" to endOffset.toString(),
                    "content_hash" to ChunkUtils.sha256Hex(text),
                )

            Chunk(
                id = "${document.id}_rec_$seq",
                documentId = document.id,
                content = text,
                sequenceNumber = seq++,
                tokenCount = tokens,
                metadata = meta,
            )
        }
    }

    private fun splitRecursively(
        text: String,
        remainingSeparators: List<String>,
    ): List<String> =
        when {
            text.isBlank() -> emptyList()
            tokenEstimator.estimateTokens(text) <= maxChunkTokens || remainingSeparators.isEmpty() -> listOf(text.trim())
            else -> partitionAndSplit(text, remainingSeparators.first(), remainingSeparators.drop(1))
        }

    private fun partitionAndSplit(
        text: String,
        sep: String,
        nextSeps: List<String>,
    ): List<String> {
        val splits =
            if (sep.isEmpty()) {
                listOf(text.take(text.length / 2), text.substring(text.length / 2))
            } else {
                text.split(sep)
            }

        val chunks = mutableListOf<String>()
        var currentAcc = StringBuilder()

        for (part in splits) {
            val candidate = if (currentAcc.isEmpty()) part else currentAcc.toString() + sep + part
            val candidateTokens = tokenEstimator.estimateTokens(candidate)

            if (candidateTokens <= maxChunkTokens) {
                currentAcc = StringBuilder(candidate)
            } else {
                if (currentAcc.isNotEmpty()) {
                    chunks.add(currentAcc.toString().trim())
                    currentAcc.clear()
                }

                val partTokens = tokenEstimator.estimateTokens(part)
                if (partTokens > maxChunkTokens) {
                    chunks.addAll(splitRecursively(part, nextSeps))
                } else {
                    currentAcc.append(part)
                }
            }
        }

        if (currentAcc.isNotBlank()) {
            chunks.add(currentAcc.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }
}
