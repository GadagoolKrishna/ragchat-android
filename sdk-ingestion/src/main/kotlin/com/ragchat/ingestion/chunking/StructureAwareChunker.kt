package com.ragchat.ingestion.chunking

import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Document
import com.ragchat.api.model.ParsedElement
import com.ragchat.ingestion.tokenizer.CharacterRatioTokenEstimator
import com.ragchat.ingestion.tokenizer.TokenEstimator

/**
 * Structure-aware chunker that preserves document hierarchy:
 * 1. Tracks hierarchical section paths from [ParsedElement.Heading] elements (`"Overview > Architecture"`).
 * 2. Bundles tables with their column schema headers intact so tabular data is never orphaned.
 * 3. Records starting/ending page numbers and character offsets.
 *
 * @param maxChunkTokens Maximum tokens per chunk (default 512).
 * @param tokenEstimator Token estimator instance.
 */
public class StructureAwareChunker(
    public val maxChunkTokens: Int = 512,
    public val tokenEstimator: TokenEstimator = CharacterRatioTokenEstimator(),
) : Chunker {
    override fun chunk(
        document: Document,
        elements: List<ParsedElement>,
    ): List<Chunk> {
        val state = ChunkerState(document.id, maxChunkTokens, tokenEstimator)
        for (el in elements) {
            processElement(el, state)
        }
        state.flushCurrentText()
        return state.chunks
    }

    private fun processElement(
        el: ParsedElement,
        state: ChunkerState,
    ) {
        when (el) {
            is ParsedElement.Heading -> {
                state.flushCurrentText()
                state.updateHeading(el.level, el.title)
            }
            is ParsedElement.Table -> {
                state.flushCurrentText()
                state.appendTable(el)
            }
            is ParsedElement.Text -> {
                state.appendText(el)
            }
            is ParsedElement.PageBreak -> {
                state.pageEnd = el.pageNumber
            }
            is ParsedElement.Image -> {
                state.appendImage(el)
            }
        }
    }

    private class ChunkerState(
        val docId: String,
        val maxTokens: Int,
        val tokenEstimator: TokenEstimator,
    ) {
        val chunks = mutableListOf<Chunk>()
        var seq = 0
        var currentSectionPath = "Root"
        val headingStack = mutableListOf<Pair<Int, String>>()
        var currentTextAcc = StringBuilder()
        var pageStart: Int? = null
        var pageEnd: Int? = null
        var charOffsetStart = 0
        var totalOffset = 0

        fun flushCurrentText() {
            val text = currentTextAcc.toString().trim()
            if (text.isNotEmpty()) {
                val tokens = tokenEstimator.estimateTokens(text)
                val meta =
                    mutableMapOf(
                        "section_path" to currentSectionPath,
                        "char_offset_start" to charOffsetStart.toString(),
                        "char_offset_end" to totalOffset.toString(),
                        "content_hash" to ChunkUtils.sha256Hex(text),
                    )
                pageStart?.let { meta["page_start"] = it.toString() }
                pageEnd?.let { meta["page_end"] = it.toString() }

                chunks.add(
                    Chunk(
                        id = "${docId}_struct_$seq",
                        documentId = docId,
                        content = text,
                        sequenceNumber = seq++,
                        tokenCount = tokens,
                        metadata = meta,
                    ),
                )
            }
            currentTextAcc.clear()
            charOffsetStart = totalOffset
            pageStart = null
            pageEnd = null
        }

        fun updateHeading(
            level: Int,
            title: String,
        ) {
            while (headingStack.isNotEmpty() && headingStack.last().first >= level) {
                headingStack.removeAt(headingStack.size - 1)
            }
            headingStack.add(Pair(level, title))
            currentSectionPath = headingStack.joinToString(" > ") { it.second }
            totalOffset += title.length
        }

        fun appendText(el: ParsedElement.Text) {
            if (pageStart == null && el.pageNumber != null) {
                pageStart = el.pageNumber
            }
            if (el.pageNumber != null) {
                pageEnd = el.pageNumber
            }
            val candidate = if (currentTextAcc.isEmpty()) el.text else currentTextAcc.toString() + "\n\n" + el.text
            val estTokens = tokenEstimator.estimateTokens(candidate)

            if (estTokens > maxTokens && currentTextAcc.isNotEmpty()) {
                flushCurrentText()
                currentTextAcc.append(el.text)
            } else {
                if (currentTextAcc.isNotEmpty()) currentTextAcc.append("\n\n")
                currentTextAcc.append(el.text)
            }
            totalOffset += el.text.length
        }

        fun appendImage(el: ParsedElement.Image) {
            el.altText?.let { alt ->
                if (alt.isNotBlank()) {
                    if (currentTextAcc.isNotEmpty()) currentTextAcc.append("\n")
                    currentTextAcc.append("[Image: ").append(alt).append("]")
                    totalOffset += alt.length
                }
            }
        }

        fun appendTable(table: ParsedElement.Table) {
            val headerText =
                if (table.headers.isNotEmpty()) {
                    table.headers.joinToString(" | ") + "\n" + table.headers.joinToString(" | ") { "---" } + "\n"
                } else {
                    ""
                }

            var currentRows = mutableListOf<List<String>>()

            for (row in table.rows) {
                val testTable = headerText + (currentRows + listOf(row)).joinToString("\n") { it.joinToString(" | ") }
                val totalTokens = tokenEstimator.estimateTokens(testTable)

                if (totalTokens > maxTokens && currentRows.isNotEmpty()) {
                    val tableString = headerText + currentRows.joinToString("\n") { it.joinToString(" | ") }
                    chunks.add(createTableChunk(tableString, table.pageNumber))
                    currentRows = mutableListOf(row)
                } else {
                    currentRows.add(row)
                }
            }

            if (currentRows.isNotEmpty()) {
                val tableString = headerText + currentRows.joinToString("\n") { it.joinToString(" | ") }
                chunks.add(createTableChunk(tableString, table.pageNumber))
            }
        }

        private fun createTableChunk(
            content: String,
            pageNumber: Int?,
        ): Chunk {
            val meta =
                mutableMapOf(
                    "section_path" to currentSectionPath,
                    "content_hash" to ChunkUtils.sha256Hex(content),
                    "is_table" to "true",
                )
            pageNumber?.let {
                meta["page_start"] = it.toString()
                meta["page_end"] = it.toString()
            }
            return Chunk(
                id = "${docId}_tbl_${seq++}",
                documentId = docId,
                content = content,
                sequenceNumber = seq,
                tokenCount = tokenEstimator.estimateTokens(content),
                metadata = meta,
            )
        }
    }
}
