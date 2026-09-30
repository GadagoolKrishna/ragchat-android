package com.ragchat.api.governance

/**
 * Cryptographic proof-of-deletion receipt issued upon successful data subject erasure (GDPR Art 17, DPDP Act).
 *
 * @property requestId Unique request tracking identifier.
 * @property userId Identifier of the data subject whose data was purged.
 * @property documentCount Total documents deleted.
 * @property chunkCount Total vector/chunk embeddings purged.
 * @property timestampEpochMs Timestamp of deletion execution.
 * @property proofHash Cryptographic SHA-256 digest proving deletion of the specified entities.
 */
public data class DeletionReceipt(
    val requestId: String,
    val userId: String,
    val documentCount: Int,
    val chunkCount: Int,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val proofHash: String,
)

/**
 * Structured export payload containing all user-associated data (GDPR Art 15).
 *
 * @property userId Subject user ID.
 * @property exportTimestampEpochMs Timestamp when export was generated.
 * @property documents Exported document metadata and raw attributes.
 * @property chatSessions Exported multi-turn conversation logs.
 * @property consentHistory Complete audit of historical consent actions.
 */
public data class DataSubjectExport(
    val userId: String,
    val exportTimestampEpochMs: Long = System.currentTimeMillis(),
    val documents: List<Map<String, String>>,
    val chatSessions: List<Map<String, Any>>,
    val consentHistory: List<ConsentRecord>,
)
