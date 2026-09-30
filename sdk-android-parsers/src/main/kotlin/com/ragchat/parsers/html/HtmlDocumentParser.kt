package com.ragchat.parsers.html

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
 * Sanitized streaming parser for HTML documents (`text/html`).
 *
 * Strips `<script>`, `<style>`, and comment blocks. Extracts headings (`<h1>`..`<h6>`),
 * paragraphs (`<p>`), list items (`<li>`), and tables (`<table>`).
 */
public class HtmlDocumentParser(
    private val limits: ParserLimits = ParserLimits(),
) : DocumentParser {
    override fun supports(mimeType: String): Boolean =
        mimeType.equals("text/html", ignoreCase = true) ||
            mimeType.equals("application/xhtml+xml", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val reader = BufferedReader(InputStreamReader(source.openStream(), Charsets.UTF_8))
            reader.use { buf ->
                val state = HtmlParserState()
                var line = buf.readLine()
                while (line != null) {
                    state.consumeLine(line) { element ->
                        emit(element)
                    }
                    line = buf.readLine()
                }
            }
        }.flowOn(Dispatchers.IO)

    private class HtmlParserState {
        var currentSection: String? = null
        var insideScriptOrStyle = false
        var insideTable = false
        var currentTableRow = mutableListOf<String>()
        val tableHeaders = mutableListOf<String>()
        val tableRows = mutableListOf<List<String>>()

        suspend fun consumeLine(
            line: String,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            val lower = line.lowercase()
            if (lower.contains("<script") || lower.contains("<style")) {
                insideScriptOrStyle = true
            }
            if (lower.contains("</script>") || lower.contains("</style>")) {
                insideScriptOrStyle = false
                return
            }
            if (insideScriptOrStyle) return

            if (lower.contains("<table")) {
                insideTable = true
                tableHeaders.clear()
                tableRows.clear()
            }

            if (insideTable) {
                processTableLine(line, lower, emit)
            } else {
                processNonTableLine(line, emit)
            }
        }

        private suspend fun processTableLine(
            line: String,
            lower: String,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            if (lower.contains("<tr")) {
                currentTableRow = mutableListOf()
            }
            for (match in CELL_REGEX.findAll(line)) {
                val cellContent = cleanHtmlText(match.groupValues[1])
                if (cellContent.isNotBlank()) currentTableRow.add(cellContent)
            }
            if (lower.contains("</tr>") && currentTableRow.isNotEmpty()) {
                if (tableHeaders.isEmpty()) {
                    tableHeaders.addAll(currentTableRow)
                } else {
                    tableRows.add(ArrayList(currentTableRow))
                }
            }
            if (lower.contains("</table>")) {
                if (tableHeaders.isNotEmpty() || tableRows.isNotEmpty()) {
                    emit(
                        ParsedElement.Table(
                            headers = ArrayList(tableHeaders),
                            rows = ArrayList(tableRows),
                            section = currentSection,
                        ),
                    )
                }
                insideTable = false
            }
        }

        private suspend fun processNonTableLine(
            line: String,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            val headingMatch = HEADING_REGEX.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].toIntOrNull() ?: 1
                val title = cleanHtmlText(headingMatch.groupValues[2])
                if (title.isNotBlank()) {
                    currentSection = title
                    emit(ParsedElement.Heading(title = title, level = level, section = currentSection))
                }
            } else {
                val text = cleanHtmlText(line)
                if (text.isNotBlank()) {
                    emit(ParsedElement.Text(text = text, section = currentSection))
                }
            }
        }

        private fun cleanHtmlText(raw: String): String =
            raw
                .replace(TAG_REGEX, " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
    }

    private companion object {
        private val HEADING_REGEX = "<h([1-6])[^>]*>(.*?)</h[1-6]>".toRegex(RegexOption.IGNORE_CASE)
        private val CELL_REGEX = "<t[dh][^>]*>(.*?)</t[dh]>".toRegex(RegexOption.IGNORE_CASE)
        private val TAG_REGEX = "<[^>]+>".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
