package com.ragchat.api.ingestion

import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement

/**
 * Service Provider Interface (SPI) for partitioning parsed document elements into indexable chunks.
 */
public interface Chunker {
    /**
     * Splits structured elements of a document into a sequence of [Chunk] items.
     *
     * @param document Parent document context.
     * @param elements Ordered parsed elements extracted from the document.
     * @return Ordered list of [Chunk] instances.
     */
    public fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk>
}
