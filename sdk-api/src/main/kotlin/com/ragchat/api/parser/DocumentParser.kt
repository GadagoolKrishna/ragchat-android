package com.ragchat.api.parser

import com.ragchat.api.model.ParsedElement
import kotlinx.coroutines.flow.Flow

/**
 * Service Provider Interface (SPI) for parsing document files into structured elements.
 */
public interface DocumentParser {
    /**
     * Evaluates whether this parser can process the given MIME type.
     *
     * @param mimeType Standard MIME string (e.g. "application/pdf").
     * @return `true` if supported, `false` otherwise.
     */
    public fun supports(mimeType: String): Boolean

    /**
     * Parses the binary stream provided by [source] into a stream of structured elements.
     *
     * @param source Binary stream container.
     * @return Cold [Flow] emitting [ParsedElement] items.
     */
    public fun parse(source: DocumentSource): Flow<ParsedElement>
}
