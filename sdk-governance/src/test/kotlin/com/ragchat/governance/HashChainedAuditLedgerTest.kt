package com.ragchat.governance

import com.ragchat.governance.audit.HashChainedAuditLedger
import com.ragchat.governance.audit.LedgerVerificationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashChainedAuditLedgerTest {
    @Test
    fun testValidChainVerification() =
        runTest {
            val ledger = HashChainedAuditLedger()

            ledger.recordAudit("QUERY_EXECUTED", mapOf("actorId" to "user-1", "modelId" to "gemini-nano"))
            ledger.recordAudit("DOCUMENT_INGESTED", mapOf("actorId" to "user-1", "documentIds" to "doc-1,doc-2"))
            ledger.recordAudit("CONSENT_UPDATED", mapOf("actorId" to "user-1", "purpose" to "CLOUD_PROCESSING"))

            val result = ledger.verifyIntegrity()
            assertTrue(result is LedgerVerificationResult.Valid)
            assertEquals(3, result.totalEntries)
        }

    @Test
    fun testTamperDetectionOnModifiedRecord() =
        runTest {
            val ledger = HashChainedAuditLedger()
            ledger.recordAudit("ACTION_1", mapOf("actorId" to "u1"))
            ledger.recordAudit("ACTION_2", mapOf("actorId" to "u1"))
            ledger.recordAudit("ACTION_3", mapOf("actorId" to "u1"))

            val entries = ledger.exportEntries()
            // Reflectively or directly simulate tampering of entry 1's action or hash
            val tamperedEntries = entries.toMutableList()
            val originalSecond = tamperedEntries[1]
            val corruptedSecond = originalSecond.copy(action = "TAMPERED_ACTION")
            tamperedEntries[1] = corruptedSecond

            // Reconstruct or test tamper detector logic directly
            val field = HashChainedAuditLedger::class.java.getDeclaredField("entries")
            field.isAccessible = true
            val internalList = field.get(ledger) as MutableList<Any>
            internalList[1] = corruptedSecond

            val verifyResult = ledger.verifyIntegrity()
            assertTrue(verifyResult is LedgerVerificationResult.Tampered)
            assertEquals(1L, verifyResult.brokenSequenceNumber)
        }
}
