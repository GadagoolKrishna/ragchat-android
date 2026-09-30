package com.ragchat.testing

import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test fake implementation of [DocumentParser].
 */
public class FakeDocumentParser(
    private val supportedMimeTypes: Set<String> = setOf("text/plain", "text/markdown"),
    public val defaultElements: List<ParsedElement> =
        listOf(
            ParsedElement.Heading("Test Document Title", level = 1, pageNumber = 1),
            ParsedElement.Text("This is parsed body text from FakeDocumentParser.", pageNumber = 1),
        ),
) : DocumentParser {
    override fun supports(mimeType: String): Boolean = supportedMimeTypes.contains(mimeType)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            for (element in defaultElements) {
                emit(element)
            }
        }
}
