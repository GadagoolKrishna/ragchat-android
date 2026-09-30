package com.ragchat.api.governance

/**
 * Processing purposes governed by data protection laws (e.g. DPDP Act, GDPR).
 */
public enum class ConsentPurpose {
    /** Processing performed strictly on-device (local parsing, local embeddings, local SLM). */
    LOCAL_PROCESSING,

    /** Transmitting data outside the device to cloud-hosted LLMs or APIs. */
    CLOUD_PROCESSING,

    /** Emitting anonymous latency, crash, and token diagnostics. */
    TELEMETRY,
}

/**
 * Immutable record of a user consent action.
 *
 * @property purpose The specific data processing purpose.
 * @property granted Whether user consent is active.
 * @property version Policy/terms version agreed to by the user.
 * @property timestampEpochMs Timestamp when the consent choice was registered.
 * @property metadata Contextual metadata (e.g., user locale, consent notice hash).
 */
public data class ConsentRecord(
    val purpose: ConsentPurpose,
    val granted: Boolean,
    val version: Int,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)
