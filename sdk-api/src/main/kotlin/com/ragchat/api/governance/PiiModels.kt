package com.ragchat.api.governance

/**
 * Standard categories of Personally Identifiable Information (PII) recognized by the SDK.
 */
public enum class PiiType {
    EMAIL,
    PHONE,
    AADHAAR,
    PAN,
    CREDIT_CARD,
    IBAN,
    CUSTOM,
}

/**
 * A detected Personally Identifiable Information entity within source text.
 *
 * @property type Classified category of PII.
 * @property value Extracted raw text span representing the PII.
 * @property startOffset 0-indexed character start position in the source text.
 * @property endOffset 0-indexed character exclusive end position in the source text.
 * @property confidence Detection confidence score from 0.0 to 1.0.
 */
public data class PiiEntity(
    val type: PiiType,
    val value: String,
    val startOffset: Int,
    val endOffset: Int,
    val confidence: Float = 1.0f,
)

/**
 * Result of de-identifying or masking text containing PII.
 *
 * @property maskedText The transformed text where PII spans are replaced with deterministic surrogates.
 * @property tokenMap Bidirectional surrogate lookup table (token -> original value) for local unmasking.
 * @property detectedEntities All identified PII entities.
 */
public data class PiiMaskResult(
    val maskedText: String,
    val tokenMap: Map<String, String>,
    val detectedEntities: List<PiiEntity>,
)

/**
 * Service Provider Interface (SPI) for optional on-device machine learning based PII recognition.
 */
public interface MlPiiDetector {
    /**
     * Scans [text] and returns detected PII entities.
     */
    public suspend fun detect(text: String): List<PiiEntity>
}
