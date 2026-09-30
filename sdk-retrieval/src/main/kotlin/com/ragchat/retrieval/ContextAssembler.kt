package com.ragchat.retrieval

import com.ragchat.api.model.Chunk
import com.ragchat.api.storage.SearchResult

/**
 * Packs retrieved chunks into a bounded LLM context window with neighbor-chunk expansion and deduplication.
 *
 * @property maxTokenBudget Maximum token budget allocated for context chunks.
 * @property neighborExpansionWindow Number of adjacent sequence chunks (N +/- window) to pull from document.
 * @property tokenEstimatorCharRatio Ratio for fallback token estimation if tokenCount is not populated (default ~4 chars/token).
 */
public class ContextAssembler(
    public val maxTokenBudget: Int = 3000,
    public val neighborExpansionWindow: Int = 1,
    public val tokenEstimatorCharRatio: Float = 4.0f,
) {
    /**
     * Assembles a list of search result chunks into a cohesive context string.
     *
     * @param candidates Ordered list of candidate [SearchResult] items.
     * @param neighborLookup Suspend lambda for fetching adjacent chunks by documentId and sequenceNumber.
     * @return [AssembledContext] containing deduplicated, expanded, and budgeted context.
     */
    public suspend fun assemble(
        candidates: List<SearchResult>,
        neighborLookup: (suspend (documentId: String, sequenceNumber: Int) -> Chunk?)? = null,
    ): AssembledContext {
        if (candidates.isEmpty()) {
            return AssembledContext("", emptyList(), 0, false)
        }

        val chunkPool = gatherChunkPool(candidates, neighborLookup)
        chunkPool.sortWith(compareBy({ it.documentId }, { it.sequenceNumber }))

        val (included, currentTokens, wasTruncated) = packChunksIntoBudget(chunkPool)
        val text = buildContextText(included)

        return AssembledContext(
            assembledText = text,
            includedChunks = included,
            totalEstimatedTokens = currentTokens,
            truncated = wasTruncated,
        )
    }

    private suspend fun gatherChunkPool(
        candidates: List<SearchResult>,
        neighborLookup: (suspend (documentId: String, sequenceNumber: Int) -> Chunk?)?,
    ): MutableList<Chunk> {
        val seenChunkIds = mutableSetOf<String>()
        val chunkPool = mutableListOf<Chunk>()

        for (result in candidates) {
            val chunk = result.chunk
            if (seenChunkIds.add(chunk.id)) {
                chunkPool.add(chunk)
            }
            if (neighborLookup != null && neighborExpansionWindow > 0) {
                expandNeighbors(chunk, neighborLookup, seenChunkIds, chunkPool)
            }
        }
        return chunkPool
    }

    private suspend fun expandNeighbors(
        chunk: Chunk,
        neighborLookup: suspend (documentId: String, sequenceNumber: Int) -> Chunk?,
        seenChunkIds: MutableSet<String>,
        chunkPool: MutableList<Chunk>,
    ) {
        for (offset in -neighborExpansionWindow..neighborExpansionWindow) {
            if (offset == 0) continue
            val targetSeq = chunk.sequenceNumber + offset
            if (targetSeq >= 0) {
                val neighbor = neighborLookup(chunk.documentId, targetSeq)
                if (neighbor != null && seenChunkIds.add(neighbor.id)) {
                    chunkPool.add(neighbor)
                }
            }
        }
    }

    private fun packChunksIntoBudget(chunkPool: List<Chunk>): Triple<List<Chunk>, Int, Boolean> {
        val included = mutableListOf<Chunk>()
        val seenContents = mutableSetOf<String>()
        var currentTokens = 0
        var wasTruncated = false

        for (c in chunkPool) {
            val normalized = c.content.trim()
            if (!seenContents.contains(normalized)) {
                val estTokens = if (c.tokenCount > 0) c.tokenCount else estimateTokens(c.content)
                if (currentTokens + estTokens > maxTokenBudget) {
                    wasTruncated = true
                } else {
                    seenContents.add(normalized)
                    included.add(c)
                    currentTokens += estTokens
                }
            }
        }
        return Triple(included, currentTokens, wasTruncated)
    }

    private fun buildContextText(included: List<Chunk>): String {
        val textBuilder = StringBuilder()
        for (c in included) {
            if (textBuilder.isNotEmpty()) {
                textBuilder.append("\n\n---\n\n")
            }
            textBuilder.append(c.content)
        }
        return textBuilder.toString()
    }

    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / tokenEstimatorCharRatio).toInt().coerceAtLeast(1)
    }
}

/**
 * Result of [ContextAssembler.assemble].
 */
public data class AssembledContext(
    public val assembledText: String,
    public val includedChunks: List<Chunk>,
    public val totalEstimatedTokens: Int,
    public val truncated: Boolean,
)
