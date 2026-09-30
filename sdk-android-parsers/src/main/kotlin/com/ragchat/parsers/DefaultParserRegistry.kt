package com.ragchat.parsers

import com.ragchat.api.parser.DocumentParser
import com.ragchat.parsers.docx.DocxDocumentParser
import com.ragchat.parsers.html.HtmlDocumentParser
import com.ragchat.parsers.image.ImageOcrParser
import com.ragchat.parsers.ocr.OcrEngine
import com.ragchat.parsers.pdf.PdfDocumentParser
import com.ragchat.parsers.security.ParserLimits
import com.ragchat.parsers.tabular.TabularDocumentParser
import com.ragchat.parsers.text.PlainTextParser

/**
 * Convenience factory providing standard document parsers configured with defensive security limits.
 */
public object DefaultParserRegistry {
    /**
     * Creates a standard list of document parsers.
     *
     * @param ocrEngine Optional on-device OCR engine for PDF fallback and image parsing.
     * @param limits Security and resource thresholds.
     * @return List of configured [DocumentParser] instances.
     */
    public fun createDefaultParsers(
        ocrEngine: OcrEngine? = null,
        limits: ParserLimits = ParserLimits(),
    ): List<DocumentParser> {
        val list =
            mutableListOf<DocumentParser>(
                PdfDocumentParser(ocrEngine = ocrEngine, limits = limits),
                DocxDocumentParser(limits = limits),
                PlainTextParser(limits = limits),
                HtmlDocumentParser(limits = limits),
                TabularDocumentParser(limits = limits),
            )
        if (ocrEngine != null) {
            list.add(ImageOcrParser(ocrEngine = ocrEngine, limits = limits))
        }
        return list
    }
}
