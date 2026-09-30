package com.ragchat.parsers.ocr

import android.graphics.Bitmap

/**
 * Interface abstraction over on-device OCR capabilities.
 */
public interface OcrEngine : AutoCloseable {
    /**
     * Extracts text asynchronously from a given in-memory bitmap.
     *
     * @param bitmap Input image bitmap.
     * @return Extracted plain text.
     */
    public suspend fun recognizeText(bitmap: Bitmap): String
}
