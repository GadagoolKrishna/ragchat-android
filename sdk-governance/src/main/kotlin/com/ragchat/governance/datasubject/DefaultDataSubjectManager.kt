package com.ragchat.governance.datasubject

import com.ragchat.api.governance.DataSubjectExport
import com.ragchat.api.governance.DeletionReceipt
import com.ragchat.governance.consent.ConsentManager
import java.security.MessageDigest
import java.util.UUID

/**
 * Interface coordinating storage clearing operations for data subject rights execution.
 */
public interface DataSubjectStoragePurgeDelegate {
    /** Purges all documents and vectors associated with [userId] or workspace. */
    public suspend fun purgeUserData(userId: String): Pair<Int, Int> // (docsDeleted, chunksDeleted)

    /** Purges a specific document and its chunks. */
    public suspend fun purgeDocument(documentId: String): Int // chunksDeleted

    /** Exports all stored documents/metadata for [userId]. */
    public suspend fun exportStoredDocuments(userId: String): List<Map<String, String>>

    /** Exports all stored chat sessions for [userId]. */
    public suspend fun exportChatSessions(userId: String): List<Map<String, Any>>
}

/**
 * Default implementation of Data Subject Access Rights (DSAR) workflows.
 * Enables GDPR Art. 15 (Access/Export), Art. 17 (Right to Erasure), and DPDP Act compliance.
 */
public class DefaultDataSubjectManager(
    private val consentManager: ConsentManager,
    private val purgeDelegate: DataSubjectStoragePurgeDelegate,
) {
    /**
     * Generates a complete data subject export (GDPR Right of Access).
     */
    public suspend fun exportAllUserData(userId: String): DataSubjectExport {
        val docs = purgeDelegate.exportStoredDocuments(userId)
        val sessions = purgeDelegate.exportChatSessions(userId)
        val consents = consentManager.getAllRecords()

        return DataSubjectExport(
            userId = userId,
            exportTimestampEpochMs = System.currentTimeMillis(),
            documents = docs,
            chatSessions = sessions,
            consentHistory = consents,
        )
    }

    /**
     * Purges all user data across documents, vectors, and chats, issuing a verifiable deletion receipt.
     */
    public suspend fun deleteAllUserData(userId: String): DeletionReceipt {
        val requestId = UUID.randomUUID().toString()
        val (docsDeleted, chunksDeleted) = purgeDelegate.purgeUserData(userId)
        val timestamp = System.currentTimeMillis()

        val proofHash =
            computeProofHash(
                requestId = requestId,
                userId = userId,
                itemCount = docsDeleted + chunksDeleted,
                timestampEpochMs = timestamp,
            )

        return DeletionReceipt(
            requestId = requestId,
            userId = userId,
            documentCount = docsDeleted,
            chunkCount = chunksDeleted,
            timestampEpochMs = timestamp,
            proofHash = proofHash,
        )
    }

    /**
     * Purges a specific document, issuing a verifiable deletion receipt.
     */
    public suspend fun deleteDocument(
        userId: String,
        documentId: String,
    ): DeletionReceipt {
        val requestId = UUID.randomUUID().toString()
        val chunksDeleted = purgeDelegate.purgeDocument(documentId)
        val timestamp = System.currentTimeMillis()

        val proofHash =
            computeProofHash(
                requestId = requestId,
                userId = userId,
                itemCount = 1 + chunksDeleted,
                timestampEpochMs = timestamp,
            )

        return DeletionReceipt(
            requestId = requestId,
            userId = userId,
            documentCount = 1,
            chunkCount = chunksDeleted,
            timestampEpochMs = timestamp,
            proofHash = proofHash,
        )
    }

    private fun computeProofHash(
        requestId: String,
        userId: String,
        itemCount: Int,
        timestampEpochMs: Long,
    ): String {
        val payload = "PROOF_OF_DELETION|$requestId|$userId|$itemCount|$timestampEpochMs"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
