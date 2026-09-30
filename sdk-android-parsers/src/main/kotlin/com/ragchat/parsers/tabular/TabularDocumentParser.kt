package com.ragchat.parsers.tabular

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
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Streaming parser for CSV (`text/csv`) and Excel XLSX (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).
 *
 * Emits row-group chunks of tabular data while consistently preserving column schema headers.
 *
 * @param rowGroupBatchSize Number of rows bundled per [ParsedElement.Table] emission.
 */
public class TabularDocumentParser(
    private val limits: ParserLimits = ParserLimits(),
    private val rowGroupBatchSize: Int = 50,
) : DocumentParser {
    override fun supports(mimeType: String): Boolean =
        mimeType.equals("text/csv", ignoreCase = true) ||
            mimeType.equals("text/tab-separated-values", ignoreCase = true) ||
            mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val mime = source.mimeType.lowercase()
            if (mime.contains("spreadsheet") || mime.contains("xlsx")) {
                parseXlsx(source).collect { emit(it) }
            } else {
                val delimiter = if (mime.contains("tab-separated")) '\t' else ','
                parseCsv(source, delimiter).collect { emit(it) }
            }
        }.flowOn(Dispatchers.IO)

    private fun parseCsv(
        source: DocumentSource,
        delimiter: Char,
    ): Flow<ParsedElement> =
        flow {
            val reader = BufferedReader(InputStreamReader(source.openStream(), Charsets.UTF_8))
            reader.use { buf ->
                var headers: List<String>? = null
                val currentBatch = mutableListOf<List<String>>()

                var line = buf.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val cells = parseCsvLine(line, delimiter)
                        if (headers == null) {
                            headers = cells
                        } else {
                            currentBatch.add(cells)
                            if (currentBatch.size >= rowGroupBatchSize) {
                                emit(ParsedElement.Table(headers = headers, rows = ArrayList(currentBatch)))
                                currentBatch.clear()
                            }
                        }
                    }
                    line = buf.readLine()
                }

                if (headers != null && currentBatch.isNotEmpty()) {
                    emit(ParsedElement.Table(headers = headers, rows = ArrayList(currentBatch)))
                }
            }
        }

    private fun parseXlsx(source: DocumentSource): Flow<ParsedElement> =
        flow {
            val rawInput = source.openStream()
            val zipStream = ZipInputStream(rawInput)

            val sharedStrings = mutableListOf<String>()
            val sheetEntries = mutableListOf<ByteArray>()

            var entryCount = 0

            zipStream.use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > limits.maxEntries) {
                        limits.validateZipEntryPath("entry-limit-exceeded")
                    }
                    limits.validateZipEntryPath(entry.name)

                    if (entry.name == "xl/sharedStrings.xml") {
                        val protectedStream = ZipBombProtector(NonClosingInputStream(zip), limits)
                        sharedStrings.addAll(parseSharedStrings(protectedStream))
                    } else if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                        val protectedStream = ZipBombProtector(NonClosingInputStream(zip), limits)
                        sheetEntries.add(protectedStream.readBytes())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            for (sheetBytes in sheetEntries) {
                parseSheet(sheetBytes.inputStream(), sharedStrings).collect { emit(it) }
            }
        }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && localTagName(parser.name) == "t") {
                parser.next()
                strings.add(parser.text ?: "")
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheet(
        stream: InputStream,
        sharedStrings: List<String>,
    ): Flow<ParsedElement> =
        flow {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val state = SheetParserState(sharedStrings, rowGroupBatchSize)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> state.handleStartTag(parser)
                    XmlPullParser.END_TAG -> state.handleEndTag(parser) { emit(it) }
                }
                eventType = parser.next()
            }

            state.flushRemaining { emit(it) }
        }

    private class SheetParserState(
        private val sharedStrings: List<String>,
        private val batchSize: Int,
    ) {
        var headers: List<String>? = null
        val currentBatch = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var isSharedString = false

        fun handleStartTag(parser: XmlPullParser) {
            val tag = localTagName(parser.name)
            if (tag == "row") {
                currentRow = mutableListOf()
            } else if (tag == "c") {
                val cellType = parser.getAttributeValue(null, "t")
                isSharedString = cellType == "s"
            } else if (tag == "v") {
                parser.next()
                val rawValue = parser.text ?: ""
                val cellVal =
                    if (isSharedString) {
                        val idx = rawValue.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawValue
                    } else {
                        rawValue
                    }
                currentRow.add(cellVal)
            }
        }

        suspend fun handleEndTag(
            parser: XmlPullParser,
            emit: suspend (ParsedElement) -> Unit,
        ) {
            if (localTagName(parser.name) == "row" && currentRow.isNotEmpty()) {
                if (headers == null) {
                    headers = ArrayList(currentRow)
                } else {
                    currentBatch.add(ArrayList(currentRow))
                    if (currentBatch.size >= batchSize) {
                        emit(ParsedElement.Table(headers = headers!!, rows = ArrayList(currentBatch)))
                        currentBatch.clear()
                    }
                }
            }
        }

        suspend fun flushRemaining(emit: suspend (ParsedElement) -> Unit) {
            if (headers != null && currentBatch.isNotEmpty()) {
                emit(ParsedElement.Table(headers = headers!!, rows = ArrayList(currentBatch)))
            }
        }
    }

    private fun parseCsvLine(
        line: String,
        delimiter: Char,
    ): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                cells.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        cells.add(sb.toString().trim())
        return cells
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
