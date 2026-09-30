package com.ragchat.api.governance

/**
 * Service Provider Interface (SPI) for identifying and redacting Personally Identifiable Information (PII).
 */
public interface PiiRedactor {
    /**
     * Replaces sensitive PII patterns with safe placeholder tokens.
     *
     * @param text Raw input text.
     * @return Sanitized string free of PII.
     */
    public fun redact(text: String): String
}
