package com.ragchat.parsers.docx

import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.parsers.security.ParserLimits
import com.ragchat.parsers.security.ZipBombProtector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Streaming OOXML document parser for DOCX files (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`).
 *
 * Implements streaming XML parsing via [XmlPullParser] directly over [ZipInputStream]
 * to extract paragraphs, heading levels, and tables without full DOM memory allocation.
 */
public class DocxDocumentParser(
    private val limits: ParserLimits = ParserLimits(),
) : DocumentParser {
    override fun supports(mimeType: String): Boolean =
        mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ignoreCase = true) ||
            mimeType.equals("application/msword", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val rawInput = source.openStream()
            val zipStream = ZipInputStream(rawInput)

            var entryCount = 0

            zipStream.use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > limits.maxEntries) {
                        limits.validateZipEntryPath("entry-limit-exceeded")
                    }
                    limits.validateZipEntryPath(entry.name)

                    if (entry.name == "word/document.xml") {
                        val entryProtected = ZipBombProtector(NonClosingInputStream(zip), limits)
                        parseDocumentXml(entryProtected).collect { element ->
                            emit(element)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.flowOn(Dispatchers.IO)

    private fun parseDocumentXml(stream: InputStream): Flow<ParsedElement> =
        flow {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val state = DocxXmlState()
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> state.handleStartTag(parser) { emit(it) }
                    XmlPullParser.END_TAG -> state.handleEndTag(parser) { emit(it) }
                }
                eventType = parser.next()
            }
        }

    private class DocxXmlState {
        var currentSection: String? = null
        var inTable = false
        var currentTableRow = mutableListOf<String>()
        val tableHeaders = mutableListOf<String>()
        val tableRows = mutableListOf<List<String>>()

        suspend fun handleStartTag(
            parser: XmlPullParser,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            when (localTagName(parser.name)) {
                "tbl" -> {
                    inTable = true
                    tableHeaders.clear()
                    tableRows.clear()
                }
                "tr" -> currentTableRow = mutableListOf()
                "p" -> {
                    val (text, headingLevel) = parseParagraph(parser)
                    if (text.isNotBlank()) {
                        if (headingLevel != null) {
                            currentSection = text
                            emit(ParsedElement.Heading(title = text, level = headingLevel, section = currentSection))
                        } else if (inTable) {
                            currentTableRow.add(text)
                        } else {
                            emit(ParsedElement.Text(text = text, section = currentSection))
                        }
                    }
                }
            }
        }

        suspend fun handleEndTag(
            parser: XmlPullParser,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            when (localTagName(parser.name)) {
                "tr" -> {
                    if (inTable && currentTableRow.isNotEmpty()) {
                        if (tableHeaders.isEmpty()) {
                            tableHeaders.addAll(currentTableRow)
                        } else {
                            tableRows.add(ArrayList(currentTableRow))
                        }
                    }
                }
                "tbl" -> {
                    if (tableHeaders.isNotEmpty() || tableRows.isNotEmpty()) {
                        emit(
                            ParsedElement.Table(
                                headers = ArrayList(tableHeaders),
                                rows = ArrayList(tableRows),
                                section = currentSection,
                            ),
                        )
                    }
                    inTable = false
                }
            }
        }

        private fun parseParagraph(parser: XmlPullParser): Pair<String, Int?> {
            val textBuilder = StringBuilder()
            var headingLevel: Int? = null
            var depth = 1

            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        headingLevel = extractHeadingLevel(parser) ?: headingLevel
                        extractText(parser)?.let { textBuilder.append(it) }
                    }
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
            return Pair(textBuilder.toString().trim(), headingLevel)
        }

        private fun extractHeadingLevel(parser: XmlPullParser): Int? {
            if (localTagName(parser.name) != "pStyle") return null
            val styleVal =
                parser.getAttributeValue(null, "val")
                    ?: parser.getAttributeValue(null, "w:val")
                    ?: ""
            return if (styleVal.startsWith("Heading", ignoreCase = true)) {
                styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
            } else {
                null
            }
        }

        private fun extractText(parser: XmlPullParser): String? {
            if (localTagName(parser.name) != "t") return null
            parser.next()
            return parser.text
        }
    }

    private companion object {
        fun localTagName(name: String?): String {
            if (name == null) return ""
            val idx = name.indexOf(':')
            return if (idx >= 0) name.substring(idx + 1) else name
        }
    }

    private class NonClosingInputStream(
        inputStream: InputStream,
    ) : FilterInputStream(inputStream) {
        override fun close() {
            // Do not close parent zipStream
        }
    }
}
