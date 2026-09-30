package com.ragchat.ui.compose

import com.ragchat.ui.compose.components.MarkdownSegment
import com.ragchat.ui.compose.components.parseMarkdownSegments
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {
    @Test
    fun testParseSimpleText() {
        val raw = "Hello world from RagChat!"
        val segments = parseMarkdownSegments(raw)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertEquals("Hello world from RagChat!", (segments[0] as MarkdownSegment.Text).text)
    }

    @Test
    fun testParseFencedCodeBlock() {
        val raw = "Intro\n```kotlin\nval x = 42\nprintln(x)\n```\nOutro"
        val segments = parseMarkdownSegments(raw)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertTrue(segments[1] is MarkdownSegment.CodeBlock)
        assertTrue(segments[2] is MarkdownSegment.Text)

        val codeBlock = segments[1] as MarkdownSegment.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("val x = 42\nprintln(x)", codeBlock.code)
    }

    @Test
    fun testParseMarkdownTable() {
        val raw = "Table preview:\n| Col A | Col B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |"
        val segments = parseMarkdownSegments(raw)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertTrue(segments[1] is MarkdownSegment.Table)

        val table = segments[1] as MarkdownSegment.Table
        assertEquals(listOf(" Col A ", " Col B "), table.headers)
        assertEquals(2, table.rows.size)
    }
}
