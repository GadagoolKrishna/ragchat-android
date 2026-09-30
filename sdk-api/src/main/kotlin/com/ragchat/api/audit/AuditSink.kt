package com.ragchat.api.audit

/**
 * Service Provider Interface (SPI) for recording immutable compliance and security audit logs.
 */
public interface AuditSink {
    /**
     * Records an audit event for enterprise compliance tracking.
     *
     * @param action Audit action type (e.g., "QUERY_EXECUTED", "DOCUMENT_INGESTED", "COLLECTION_PURGED").
     * @param metadata Structured metadata explaining the event context without exposing PII.
     * @param timestampEpochMs Epoch timestamp in milliseconds.
     */
    public suspend fun recordAudit(
        action: String,
        metadata: Map<String, String>,
        timestampEpochMs: Long = System.currentTimeMillis(),
    )
}
