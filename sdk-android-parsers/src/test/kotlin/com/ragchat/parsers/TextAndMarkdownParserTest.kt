package com.ragchat.parsers

import com.ragchat.api.model.ParsedElement
import com.ragchat.parsers.html.HtmlDocumentParser
import com.ragchat.parsers.tabular.TabularDocumentParser
import com.ragchat.parsers.text.PlainTextParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextAndMarkdownParserTest {
    @Test
    fun testMarkdownParsingWithIndicText() =
        runTest {
            val parser = PlainTextParser()
            val mdContent =
                """
                # RagChat Overview
                This is a pure on-device RAG engine.

                ## बहुभाषी समर्थन (Multilingual Support)
                RagChat supports Indic languages seamlessly:
                - हिन्दी पाठ: यह एक उदाहरण वाक्य है।
                - ಕನ್ನಡ ಪಠ್ಯ: ಇದು ಕನ್ನಡದ ಒಂದು ಮಾದರಿ ವಾಕ್ಯವಾಗಿದೆ.

                | Language | Script | Code |
                | --- | --- | --- |
                | Hindi | Devanagari | hi |
                | Kannada | Kannada | kn |
                """.trimIndent()

            val source = InMemoryDocumentSource(mdContent.toByteArray(Charsets.UTF_8), "text/markdown")
            val elements = parser.parse(source).toList()

            assertTrue(elements.isNotEmpty())
            val headings = elements.filterIsInstance<ParsedElement.Heading>()
            assertEquals(2, headings.size)
            assertEquals("RagChat Overview", headings[0].title)
            assertTrue(headings[1].title.contains("बहुभाषी समर्थन"))

            val tables = elements.filterIsInstance<ParsedElement.Table>()
            assertEquals(1, tables.size)
            assertEquals(listOf("Language", "Script", "Code"), tables[0].headers)
            assertEquals(2, tables[0].rows.size)
            assertTrue(tables[0].rows[0].contains("Hindi"))
            assertTrue(tables[0].rows[1].contains("Kannada"))
        }

    @Test
    fun testHtmlSanitizationAndParsing() =
        runTest {
            val parser = HtmlDocumentParser()
            val html =
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>body { color: red; }</style>
                    <script>alert('malicious');</script>
                </head>
                <body>
                    <h1>Knowledge Base</h1>
                    <p>RagChat securely indexes corporate documents without PII leaks.</p>
                    <table>
                        <tr><th>Parameter</th><th>Value</th></tr>
                        <tr><td>Dimensions</td><td>384</td></tr>
                        <tr><td>Quantization</td><td>int8</td></tr>
                    </table>
                </body>
                </html>
                """.trimIndent()

            val source = InMemoryDocumentSource(html.toByteArray(Charsets.UTF_8), "text/html")
            val elements = parser.parse(source).toList()

            val headings = elements.filterIsInstance<ParsedElement.Heading>()
            assertEquals(1, headings.size)
            assertEquals("Knowledge Base", headings[0].title)

            val texts = elements.filterIsInstance<ParsedElement.Text>()
            assertTrue(texts.any { it.text.contains("RagChat securely indexes") })
            // Script and style text must be sanitized out
            assertTrue(texts.none { it.text.contains("alert") || it.text.contains("color: red") })

            val tables = elements.filterIsInstance<ParsedElement.Table>()
            assertEquals(1, tables.size)
            assertEquals(listOf("Parameter", "Value"), tables[0].headers)
            assertEquals(2, tables[0].rows.size)
        }

    @Test
    fun testCsvTabularParsing() =
        runTest {
            val parser = TabularDocumentParser(rowGroupBatchSize = 2)
            val csv =
                """
                id,name,role
                1,Alice,Engineer
                2,Bob,Architect
                3,Charlie,Product Manager
                """.trimIndent()

            val source = InMemoryDocumentSource(csv.toByteArray(Charsets.UTF_8), "text/csv")
            val elements = parser.parse(source).toList()

            val tables = elements.filterIsInstance<ParsedElement.Table>()
            assertEquals(2, tables.size) // 3 rows in batches of 2 -> 2 tables
            assertEquals(listOf("id", "name", "role"), tables[0].headers)
            assertEquals(listOf("id", "name", "role"), tables[1].headers)
            assertEquals(2, tables[0].rows.size)
            assertEquals(1, tables[1].rows.size)
        }
}
