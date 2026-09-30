package com.ragchat.governance.audit

import com.ragchat.api.audit.AuditSink
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Single cryptographic hash-chained audit block.
 */
public data class AuditLedgerEntry(
    val sequenceNumber: Long,
    val previousHash: String,
    val timestampEpochMs: Long,
    val action: String,
    val actorId: String,
    val modelId: String,
    val documentIds: List<String>,
    val metadataHash: String,
    val currentHash: String,
)

/**
 * Result of ledger integrity validation.
 */
public sealed interface LedgerVerificationResult {
    /** Ledger integrity is intact and unbroken. */
    public data class Valid(
        val totalEntries: Int,
    ) : LedgerVerificationResult

    /** Ledger detected tampering, truncation, or broken link. */
    public data class Tampered(
        val brokenSequenceNumber: Long,
        val expectedHash: String,
        val actualHash: String,
        val reason: String,
    ) : LedgerVerificationResult
}

/**
 * Tamper-evident cryptographic audit ledger implementing [AuditSink].
 * Links each audit record via SHA-256 hash chains to ensure non-repudiation and detect record tampering.
 * Enforces zero-PII logging: prompts, vectors, and texts are strictly prohibited.
 */
@Suppress("ReturnCount")
public class HashChainedAuditLedger(
    private val genesisSeed: String = "RAGCHAT_GENESIS_CHAIN_V1",
    private val maxRetentionEntries: Int = 10_000,
) : AuditSink {
    private val entries = CopyOnWriteArrayList<AuditLedgerEntry>()
    private val sequenceCounter = AtomicLong(0L)

    /**
     * Appends an audit event to the ledger with SHA-256 chaining.
     */
    override suspend fun recordAudit(
        action: String,
        metadata: Map<String, String>,
        timestampEpochMs: Long,
    ) {
        val seq = sequenceCounter.getAndIncrement()
        val prevHash =
            synchronized(this) {
                entries.lastOrNull()?.currentHash ?: computeSha256(genesisSeed)
            }

        val actorId = metadata["actorId"] ?: "system"
        val modelId = metadata["modelId"] ?: "none"
        val docIds = metadata["documentIds"]?.split(",")?.map { it.trim() } ?: emptyList()

        // Hash metadata excluding any direct content
        val sanitizedMetaString =
            metadata.entries
                .filter { it.key != "prompt" && it.key != "text" && it.key != "content" }
                .sortedBy { it.key }
                .joinToString(";") { "${it.key}=${it.value}" }
        val metadataHash = computeSha256(sanitizedMetaString)

        val payload = "$seq|$prevHash|$timestampEpochMs|$action|$actorId|$modelId|${docIds.joinToString(",")}|$metadataHash"
        val currentHash = computeSha256(payload)

        val entry =
            AuditLedgerEntry(
                sequenceNumber = seq,
                previousHash = prevHash,
                timestampEpochMs = timestampEpochMs,
                action = action,
                actorId = actorId,
                modelId = modelId,
                documentIds = docIds,
                metadataHash = metadataHash,
                currentHash = currentHash,
            )

        synchronized(this) {
            entries.add(entry)
            if (entries.size > maxRetentionEntries) {
                entries.removeAt(0)
            }
        }
    }

    /**
     * Verifies the cryptographic chain across all retained audit entries.
     */
    public fun verifyIntegrity(): LedgerVerificationResult {
        synchronized(this) {
            if (entries.isEmpty()) return LedgerVerificationResult.Valid(0)

            for (i in entries.indices) {
                val current = entries[i]
                val expectedPrevHash =
                    if (i == 0) {
                        current.previousHash // First retained block in pruned window
                    } else {
                        entries[i - 1].currentHash
                    }

                if (current.previousHash != expectedPrevHash) {
                    return LedgerVerificationResult.Tampered(
                        brokenSequenceNumber = current.sequenceNumber,
                        expectedHash = expectedPrevHash,
                        actualHash = current.previousHash,
                        reason = "Previous hash mismatch at sequence ${current.sequenceNumber}",
                    )
                }

                val payload =
                    "${current.sequenceNumber}|${current.previousHash}|${current.timestampEpochMs}|" +
                        "${current.action}|${current.actorId}|${current.modelId}|${current.documentIds.joinToString(
                            ",",
                        )}|${current.metadataHash}"
                val recomputedHash = computeSha256(payload)
                if (current.currentHash != recomputedHash) {
                    return LedgerVerificationResult.Tampered(
                        brokenSequenceNumber = current.sequenceNumber,
                        expectedHash = recomputedHash,
                        actualHash = current.currentHash,
                        reason = "Block signature corrupted at sequence ${current.sequenceNumber}",
                    )
                }
            }
            return LedgerVerificationResult.Valid(entries.size)
        }
    }

    /**
     * Exports all audit records for compliance audit inspection.
     */
    public fun exportEntries(): List<AuditLedgerEntry> = entries.toList()

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
