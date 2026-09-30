package com.ragchat.core.citation

import com.ragchat.api.model.Citation
import com.ragchat.api.storage.SearchResult

/**
 * Extracts and maps citation references (e.g. `[chunk_id]` or `[doc1_chunk0]`) within model generated text
 * back to source document chunks with page numbers, scores, and text snippets.
 */
public object CitationBuilder {
    private val CITATION_REGEX = Regex("\\[([a-zA-Z0-9_-]+)\\]")

    /**
     * Extracts all unique chunk citations appearing in [answerText] and correlates them with [retrievedResults].
     *
     * @param answerText Text produced by the LLM.
     * @param retrievedResults Candidate chunks retrieved for the prompt.
     * @return List of resolved [Citation] records.
     */
    public fun extractCitations(
        answerText: String,
        retrievedResults: List<SearchResult>,
    ): List<Citation> {
        val matches = CITATION_REGEX.findAll(answerText)
        val referencedChunkIds = matches.map { it.groupValues[1] }.toSet()

        val resultsByChunkId = retrievedResults.associateBy { it.chunk.id }
        val citations = mutableListOf<Citation>()

        for (chunkId in referencedChunkIds) {
            val result = resultsByChunkId[chunkId]
            if (result != null) {
                val pageNumber =
                    result.chunk.metadata["page_number"]?.toIntOrNull()
                        ?: result.chunk.metadata["page"]?.toIntOrNull()
                val snippet = result.chunk.content.take(200)

                citations.add(
                    Citation(
                        documentId = result.chunk.documentId,
                        chunkId = result.chunk.id,
                        textSnippet = snippet,
                        pageNumber = pageNumber,
                        score = result.score,
                        metadata = result.chunk.metadata,
                    ),
                )
            }
        }
        return citations
    }
}
