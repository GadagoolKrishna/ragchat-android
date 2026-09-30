package com.ragchat.api.governance

/**
 * Service Provider Interface (SPI) for checking end-user consent status before operations.
 */
public interface ConsentProvider {
    /**
     * Checks if user consent is active for a given processing purpose.
     *
     * @param consentType Purpose identifier (e.g., "CLOUD_INFERENCE", "DOCUMENT_INDEXING", "TELEMETRY").
     * @return `true` if consent is granted, `false` otherwise.
     */
    public suspend fun hasConsent(consentType: String): Boolean
}
