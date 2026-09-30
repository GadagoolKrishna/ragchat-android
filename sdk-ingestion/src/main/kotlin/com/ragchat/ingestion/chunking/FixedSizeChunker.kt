package com.ragchat.ingestion.chunking

import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement
import com.ragchat.ingestion.tokenizer.CharacterRatioTokenEstimator
import com.ragchat.ingestion.tokenizer.TokenEstimator

/**
 * Fixed-size token chunker with sliding window overlap.
 *
 * Each generated [Chunk] carries:
 * - document ID
 * - sequence number
 * - token count
 * - metadata: `page_start`, `page_end`, `char_offset_start`, `char_offset_end`, `content_hash`, `section_path`
 *
 * @param chunkSize Target maximum token capacity per chunk (default 512 tokens).
 * @param chunkOverlap Overlap between consecutive chunks in tokens (default 64 tokens).
 * @param tokenEstimator Strategy for calculating token lengths (defaults to [CharacterRatioTokenEstimator]).
 */
public class FixedSizeChunker(
    public val chunkSize: Int = 512,
    public val chunkOverlap: Int = 64,
    public val tokenEstimator: TokenEstimator = CharacterRatioTokenEstimator(),
) : Chunker {
    init {
        require(chunkSize > 0) { "chunkSize must be > 0" }
        require(chunkOverlap >= 0 && chunkOverlap < chunkSize) {
            "chunkOverlap must be >= 0 and < chunkSize"
        }
    }

    override fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk> {
        val fullTextBuilder = StringBuilder()
        val pageMap = mutableListOf<Pair<IntRange, Int>>() // charRange to pageNumber
        var currentOffset = 0

        for (el in elements) {
            val text =
                when (el) {
                    is ParsedElement.Text -> el.text
                    is ParsedElement.Heading -> "${el.title}\n"
                    is ParsedElement.Table -> formatTable(el)
                    is ParsedElement.PageBreak -> ""
                    is ParsedElement.Image -> el.altText ?: ""
                }
            if (text.isNotEmpty()) {
                val start = currentOffset
                fullTextBuilder.append(text).append("\n\n")
                currentOffset = fullTextBuilder.length
                el.pageNumber?.let { page ->
                    pageMap.add(Pair(start until currentOffset, page))
                }
            }
        }

        val fullText = fullTextBuilder.toString().trim()
        if (fullText.isEmpty()) return emptyList()

        return sliceTextIntoChunks(document.id, fullText, pageMap)
    }

    private fun sliceTextIntoChunks(
        docId: String,
        text: String,
        pageMap: List<Pair<IntRange, Int>>,
    ): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var seq = 0
        var charIdx = 0
        val textLen = text.length

        while (charIdx < textLen) {
            val (endIdx, tokenCount) = findWindowEnd(text, charIdx)
            val chunkContent = text.substring(charIdx, endIdx).trim()

            if (chunkContent.isNotEmpty()) {
                val (pageStart, pageEnd) = resolvePageRange(charIdx, endIdx, pageMap)
                val meta =
                    mapOf(
                        "page_start" to pageStart.toString(),
                        "page_end" to pageEnd.toString(),
                        "char_offset_start" to charIdx.toString(),
                        "char_offset_end" to endIdx.toString(),
                        "content_hash" to ChunkUtils.sha256Hex(chunkContent),
                    )

                chunks.add(
                    Chunk(
                        id = "${docId}_chk_$seq",
                        documentId = docId,
                        content = chunkContent,
                        sequenceNumber = seq++,
                        tokenCount = tokenCount,
                        metadata = meta,
                    ),
                )
            }

            if (endIdx >= textLen) break

            val stepBackChars = estimateCharsForTokens(text, charIdx, endIdx, chunkOverlap)
            val nextStart = (endIdx - stepBackChars).coerceAtLeast(charIdx + 1)
            charIdx = nextStart
        }

        return chunks
    }

    private fun findWindowEnd(
        text: String,
        startIdx: Int,
    ): Pair<Int, Int> {
        var low = startIdx + 1
        var high = text.length
        var bestEnd = text.length
        var bestTokens = tokenEstimator.estimateTokens(text.substring(startIdx, bestEnd))

        if (bestTokens <= chunkSize) {
            return Pair(bestEnd, bestTokens)
        }

        // Binary search character offset fitting chunkSize tokens
        while (low <= high) {
            val mid = (low + high) ushr 1
            val sub = text.substring(startIdx, mid)
            val tokens = tokenEstimator.estimateTokens(sub)
            if (tokens <= chunkSize) {
                bestEnd = mid
                bestTokens = tokens
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return Pair(bestEnd, bestTokens)
    }

    private fun estimateCharsForTokens(
        text: String,
        start: Int,
        end: Int,
        tokensNeeded: Int,
    ): Int {
        if (tokensNeeded <= 0) return 0
        val slice = text.substring(start, end)
        val totalTokens = tokenEstimator.estimateTokens(slice).coerceAtLeast(1)
        val ratio = tokensNeeded.toFloat() / totalTokens.toFloat()
        return (slice.length * ratio).toInt().coerceIn(1, slice.length - 1)
    }

    private fun resolvePageRange(
        start: Int,
        end: Int,
        pageMap: List<Pair<IntRange, Int>>,
    ): Pair<Int, Int> {
        var pStart = 1
        var pEnd = 1
        val matchingPages =
            pageMap
                .filter { (range, _) ->
                    range.first < end && range.last >= start
                }.map { it.second }

        if (matchingPages.isNotEmpty()) {
            pStart = matchingPages.minOrNull() ?: 1
            pEnd = matchingPages.maxOrNull() ?: 1
        }
        return Pair(pStart, pEnd)
    }

    private fun formatTable(table: ParsedElement.Table): String {
        val sb = StringBuilder()
        if (table.headers.isNotEmpty()) {
            sb.append(table.headers.joinToString(" | ")).append("\n")
            sb.append(table.headers.joinToString(" | ") { "---" }).append("\n")
        }
        for (row in table.rows) {
            sb.append(row.joinToString(" | ")).append("\n")
        }
        return sb.toString()
    }
}
