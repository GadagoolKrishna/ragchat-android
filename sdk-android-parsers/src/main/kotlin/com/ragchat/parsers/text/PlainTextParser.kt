package com.ragchat.parsers.text

import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.parsers.security.ParserLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Streaming parser for plaintext and Markdown documents (`text/plain`, `text/markdown`).
 *
 * Extracts paragraphs, headings (`# Title`), and basic Markdown tables.
 */
public class PlainTextParser(
    private val limits: ParserLimits = ParserLimits(),
) : DocumentParser {
    override fun supports(mimeType: String): Boolean =
        mimeType.equals("text/plain", ignoreCase = true) ||
            mimeType.equals("text/markdown", ignoreCase = true) ||
            mimeType.equals("text/x-markdown", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val reader = BufferedReader(InputStreamReader(source.openStream(), Charsets.UTF_8))
            reader.use { buf ->
                val state = TextParserState()
                var line = buf.readLine()
                while (line != null) {
                    state.consumeLine(line.trim()) { element ->
                        emit(element)
                    }
                    line = buf.readLine()
                }
                state.flushRemaining { element ->
                    emit(element)
                }
            }
        }.flowOn(Dispatchers.IO)

    private class TextParserState {
        var currentSection: String? = null
        var paragraphBuilder = StringBuilder()
        var tableHeaders: List<String>? = null
        val tableRows = mutableListOf<List<String>>()

        suspend fun consumeLine(
            trimmed: String,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            if (trimmed.startsWith("|") && trimmed.contains("---")) {
                return
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                handleTableLine(trimmed)
                return
            }

            flushTable(emit)

            val headingMatch = MARKDOWN_HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                flushParagraph(emit)
                val hashes = headingMatch.groupValues[1]
                val title = headingMatch.groupValues[2].trim()
                currentSection = title
                emit(ParsedElement.Heading(title = title, level = hashes.length, section = currentSection))
            } else if (trimmed.isEmpty()) {
                flushParagraph(emit)
            } else {
                if (paragraphBuilder.isNotEmpty()) {
                    paragraphBuilder.append(" ")
                }
                paragraphBuilder.append(trimmed)
            }
        }

        private fun handleTableLine(trimmed: String) {
            val cells =
                trimmed
                    .split("|")
                    .map { it.trim() }
                    .filterIndexed { index, _ -> index > 0 && index < trimmed.split("|").size - 1 }

            if (tableHeaders == null) {
                tableHeaders = cells
            } else {
                tableRows.add(cells)
            }
        }

        suspend fun flushTable(emit: suspend (ParsedElement) -> Unit) {
            tableHeaders?.let { headers ->
                emit(
                    ParsedElement.Table(
                        headers = headers,
                        rows = ArrayList(tableRows),
                        section = currentSection,
                    ),
                )
                tableHeaders = null
                tableRows.clear()
            }
        }

        suspend fun flushParagraph(emit: suspend (ParsedElement) -> Unit) {
            if (paragraphBuilder.isNotBlank()) {
                emit(ParsedElement.Text(text = paragraphBuilder.toString().trim(), section = currentSection))
                paragraphBuilder = StringBuilder()
            }
        }

        suspend fun flushRemaining(emit: suspend (ParsedElement) -> Unit) {
            flushTable(emit)
            flushParagraph(emit)
        }
    }

    private companion object {
        private val MARKDOWN_HEADING_REGEX = "^(#{1,6})\\s+(.+)$".toRegex()
    }
}
