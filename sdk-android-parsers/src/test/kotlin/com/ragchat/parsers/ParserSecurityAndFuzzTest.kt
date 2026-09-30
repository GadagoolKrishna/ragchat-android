package com.ragchat.parsers

import com.ragchat.api.error.SdkError
import com.ragchat.parsers.docx.DocxDocumentParser
import com.ragchat.parsers.security.ParserLimits
import com.ragchat.parsers.text.PlainTextParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ParserSecurityAndFuzzTest {
    @Test
    fun testFileSizeBytesEnforcement() =
        runTest {
            val limits = ParserLimits(maxFileSizeBytes = 100)
            val parser = PlainTextParser(limits = limits)
            val oversizedData = ByteArray(200) { 'a'.code.toByte() }
            val source = InMemoryDocumentSource(oversizedData, "text/plain")

            assertFailsWith<SdkError.ValidationError> {
                parser.parse(source).toList()
            }
        }

    @Test
    fun testZipBombExpansionProtection() =
        runTest {
            val limits = ParserLimits(maxUncompressedSizeBytes = 1024)
            val parser = DocxDocumentParser(limits = limits)

            val docxXml =
                "<?xml version=\"1.0\"?><w:document><w:body>" +
                    "<w:p><w:r><w:t>" + "A".repeat(4000) + "</w:t></w:r></w:p>" +
                    "</w:body></w:document>"

            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(docxXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val source = InMemoryDocumentSource(bos.toByteArray(), docxMime)
            assertFailsWith<SdkError.ValidationError> {
                parser.parse(source).toList()
            }
        }

    @Test
    fun testPathTraversalZipSlipProtection() =
        runTest {
            val parser = DocxDocumentParser()

            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("../../etc/passwd"))
                zip.write("root:x:0:0:root:/root:/bin/bash".toByteArray())
                zip.closeEntry()
            }

            val docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val source = InMemoryDocumentSource(bos.toByteArray(), docxMime)
            assertFailsWith<SdkError.ValidationError> {
                parser.parse(source).toList()
            }
        }
}
