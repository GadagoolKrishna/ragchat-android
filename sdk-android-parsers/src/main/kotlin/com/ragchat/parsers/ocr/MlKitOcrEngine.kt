package com.ragchat.parsers.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR engine using Google ML Kit Text Recognition.
 */
public class MlKitOcrEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer
                .process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }.addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    override fun close() {
        recognizer.close()
    }
}
