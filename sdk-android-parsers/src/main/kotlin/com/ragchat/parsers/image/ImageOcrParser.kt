package com.ragchat.parsers.image

import android.graphics.BitmapFactory
import com.ragchat.api.model.ParsedElement
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.parser.DocumentSource
import com.ragchat.parsers.ocr.OcrEngine
import com.ragchat.parsers.security.ParserLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Image document parser extracting text via an on-device [OcrEngine] (Google ML Kit).
 *
 * Supports `image/jpeg`, `image/png`, `image/webp`.
 */
public class ImageOcrParser(
    private val ocrEngine: OcrEngine,
    private val limits: ParserLimits = ParserLimits(),
) : DocumentParser {
    override fun supports(mimeType: String): Boolean =
        mimeType.equals("image/jpeg", ignoreCase = true) ||
            mimeType.equals("image/png", ignoreCase = true) ||
            mimeType.equals("image/webp", ignoreCase = true)

    override fun parse(source: DocumentSource): Flow<ParsedElement> =
        flow {
            limits.checkFileSize(source.sizeBytes)

            val bytes = source.openStream().use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                try {
                    val text = ocrEngine.recognizeText(bitmap).trim()
                    if (text.isNotEmpty()) {
                        emit(ParsedElement.Text(text = text))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }.flowOn(Dispatchers.IO)
}
