package com.ragchat.parsers

import com.ragchat.api.model.ParsedElement
import com.ragchat.parsers.docx.DocxDocumentParser
import com.ragchat.parsers.text.PlainTextParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoldenAndMemoryCeilingTest {
    @Test
    fun testTableHeavyDocxParsing() =
        runTest {
            val parser = DocxDocumentParser()

            val docxXml =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        <w:p>
                            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                            <w:r><w:t>Quarterly Financial Results</w:t></w:r>
                        </w:p>
                        <w:tbl>
                            <w:tr>
                                <w:tc><w:p><w:r><w:t>Quarter</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:t>Revenue</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:t>Q1</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:t>$10M</w:t></w:r></w:p></w:tc>
                            </w:tr>
                            <w:tr>
                                <w:tc><w:p><w:r><w:t>Q2</w:t></w:r></w:p></w:tc>
                                <w:tc><w:p><w:r><w:t>$15M</w:t></w:r></w:p></w:tc>
                            </w:tr>
                        </w:tbl>
                    </w:body>
                </w:document>
                """.trimIndent()

            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(docxXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val source =
                InMemoryDocumentSource(
                    bos.toByteArray(),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                )
            val elements = parser.parse(source).toList()

            val heading = elements.filterIsInstance<ParsedElement.Heading>().firstOrNull()
            assertNotNull(heading)
            assertEquals("Quarterly Financial Results", heading.title)

            val table = elements.filterIsInstance<ParsedElement.Table>().firstOrNull()
            assertNotNull(table)
            assertEquals(listOf("Quarter", "Revenue"), table.headers)
            assertEquals(2, table.rows.size)
        }

    @Test
    fun testMemoryCeilingLargeDocumentStream() =
        runTest {
            // Generate a 200-page equivalent text stream
            val sb = StringBuilder()
            for (page in 1..200) {
                sb.append("# Page Header ").append(page).append("\n\n")
                for (para in 1..10) {
                    sb
                        .append("This is paragraph ")
                        .append(para)
                        .append(" on page ")
                        .append(page)
                        .append(" containing detailed technical explanation and retrieval metadata.")
                        .append("\n\n")
                }
            }
            val data = sb.toString().toByteArray(Charsets.UTF_8)
            val source = InMemoryDocumentSource(data, "text/markdown")
            val parser = PlainTextParser()

            System.gc()
            val heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

            var count = 0
            parser.parse(source).collect { element ->
                count++
            }

            val heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val heapGrowthMb = (heapAfter - heapBefore) / (1024 * 1024)

            assertTrue(count > 200, "Should have streamed over 200 elements")
            assertTrue(heapGrowthMb < 150, "Heap growth ($heapGrowthMb MB) must remain under 150 MB ceiling")
        }
}
