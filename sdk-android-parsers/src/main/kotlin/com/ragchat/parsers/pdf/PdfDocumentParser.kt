package com.ragchat.parsers.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.parsers.ocr.OcrEngine
import com.ragchat.parsers.security.ParserLimits
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

/**
 * Streaming PDF document parser utilizing PDFBox-Android for electronic text extraction
 * with automatic on-device OCR fallback via [PdfRenderer] and [OcrEngine] for scanned pages.
 */
public class PdfDocumentParser(
    private val ocrEngine: OcrEngine? = null,
    private val limits: ParserLimits = ParserLimits(),
    private val minTextCharsForOcrThreshold: Int = 30,
) : DocumentParser {
    override fun supports(mimeType: String): Boolean = mimeType.equals("application/pdf", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val tempFile = File.createTempFile("ragchat_pdf_", ".tmp")
            try {
                source.openStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                PDDocument.load(tempFile).use { pdDoc ->
                    val totalPages = pdDoc.numberOfPages
                    var renderer: PdfRenderer? = null
                    var pfd: ParcelFileDescriptor? = null

                    try {
                        val stripper =
                            PDFTextStripper().apply {
                                sortByPosition = true
                            }

                        for (pageIndex in 1..totalPages) {
                            limits.checkPageLimit(pageIndex)

                            stripper.startPage = pageIndex
                            stripper.endPage = pageIndex
                            val pageText = stripper.getText(pdDoc).trim()

                            if (pageText.length >= minTextCharsForOcrThreshold || ocrEngine == null) {
                                if (pageText.isNotEmpty()) {
                                    emit(ParsedElement.Text(text = pageText, pageNumber = pageIndex))
                                }
                            } else {
                                if (renderer == null) {
                                    pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                    renderer = PdfRenderer(pfd)
                                }
                                val ocrText = renderAndOcrPage(renderer, pageIndex - 1)
                                if (ocrText.isNotBlank()) {
                                    emit(ParsedElement.Text(text = ocrText, pageNumber = pageIndex))
                                }
                            }
                            emit(ParsedElement.PageBreak(pageNumber = pageIndex))
                        }
                    } finally {
                        renderer?.close()
                        pfd?.close()
                    }
                }
            } finally {
                tempFile.delete()
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun renderAndOcrPage(
        renderer: PdfRenderer,
        pageIndex: Int,
    ): String {
        val ocr = ocrEngine ?: return ""
        val page = renderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        try {
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return ocr.recognizeText(bitmap).trim()
        } finally {
            page.close()
            bitmap.recycle()
        }
    }
}
