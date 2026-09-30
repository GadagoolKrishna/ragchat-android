package com.ragchat.testing

import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement

/**
 * Test fake implementation of [Chunker].
 */
public class FakeChunker(
    public val chunkSize: Int = 100,
) : Chunker {
    override fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var seq = 0
        for (el in elements) {
            val text =
                when (el) {
                    is ParsedElement.Text -> el.text
                    is ParsedElement.Heading -> el.title
                    is ParsedElement.Table -> el.headers.joinToString(" | ")
                    is ParsedElement.Image -> el.altText ?: "[Image]"
                    is ParsedElement.PageBreak -> continue
                }
            chunks.add(
                Chunk(
                    id = "${document.id}_chunk_$seq",
                    documentId = document.id,
                    content = text,
                    sequenceNumber = seq++,
                    tokenCount = text.length / 4,
                    metadata = el.pageNumber?.let { mapOf("page" to it.toString()) } ?: emptyMap(),
                ),
            )
        }
        return chunks
    }
}
