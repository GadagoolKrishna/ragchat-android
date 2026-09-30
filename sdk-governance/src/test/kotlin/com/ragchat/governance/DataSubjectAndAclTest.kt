package com.ragchat.governance

import com.ragchat.api.governance.ConsentPurpose
import com.ragchat.api.governance.SecurityClassification
import com.ragchat.api.governance.UserAcl
import com.ragchat.api.model.Chunk
import com.ragchat.api.storage.SearchResult
import com.ragchat.governance.acl.AccessControlEnforcer
import com.ragchat.governance.consent.ConsentManager
import com.ragchat.governance.datasubject.DataSubjectStoragePurgeDelegate
import com.ragchat.governance.datasubject.DefaultDataSubjectManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSubjectAndAclTest {
    @Test
    fun testDataSubjectPurgeAndCryptographicReceipt() =
        runTest {
            val consentManager = ConsentManager()
            consentManager.updateConsent(ConsentPurpose.LOCAL_PROCESSING, granted = true, version = 1)

            val mockStorage =
                object : DataSubjectStoragePurgeDelegate {
                    override suspend fun purgeUserData(userId: String): Pair<Int, Int> = Pair(3, 45)

                    override suspend fun purgeDocument(documentId: String): Int = 15

                    override suspend fun exportStoredDocuments(userId: String): List<Map<String, String>> =
                        listOf(mapOf("docId" to "doc-101", "name" to "Passport.pdf"))

                    override suspend fun exportChatSessions(userId: String): List<Map<String, Any>> =
                        listOf(mapOf("sessionId" to "sess-1", "turnCount" to 4))
                }

            val dsar = DefaultDataSubjectManager(consentManager, mockStorage)

            // Test export
            val export = dsar.exportAllUserData("user-alice")
            assertEquals("user-alice", export.userId)
            assertEquals(1, export.documents.size)
            assertEquals(1, export.chatSessions.size)
            assertEquals(1, export.consentHistory.size)

            // Test delete with receipt
            val receipt = dsar.deleteAllUserData("user-alice")
            assertEquals("user-alice", receipt.userId)
            assertEquals(3, receipt.documentCount)
            assertEquals(45, receipt.chunkCount)
            assertTrue(receipt.proofHash.length == 64, "Proof hash must be 64-char hex SHA-256")
        }

    @Test
    fun testAclClassificationAndRoleFiltering() {
        val enforcer = AccessControlEnforcer()

        val publicChunk = Chunk("c1", "d1", "Public FAQ", 0, 10, mapOf("sensitivity" to "PUBLIC"))
        val internalChunk = Chunk("c2", "d1", "Internal Wiki", 1, 10, mapOf("sensitivity" to "INTERNAL"))
        val confidentialChunk =
            Chunk(
                "c3",
                "d1",
                "Executive Salary",
                2,
                10,
                mapOf("sensitivity" to "CONFIDENTIAL", "required_roles" to "hr,executive"),
            )
        val restrictedChunk = Chunk("c4", "d1", "National Defense Secret", 3, 10, mapOf("sensitivity" to "RESTRICTED"))

        val candidates =
            listOf(
                SearchResult(publicChunk, 0.95f),
                SearchResult(internalChunk, 0.88f),
                SearchResult(confidentialChunk, 0.82f),
                SearchResult(restrictedChunk, 0.75f),
            )

        // Standard employee ACL: INTERNAL clearance, no HR role
        val employeeAcl = UserAcl(userId = "emp-1", clearance = SecurityClassification.INTERNAL, roles = setOf("eng"))
        val employeeResults = enforcer.filterAuthorizedResults(candidates, employeeAcl)
        assertEquals(2, employeeResults.size)
        assertEquals("c1", employeeResults[0].chunk.id)
        assertEquals("c2", employeeResults[1].chunk.id)

        // HR Manager ACL: CONFIDENTIAL clearance + HR role
        val hrAcl = UserAcl(userId = "hr-1", clearance = SecurityClassification.CONFIDENTIAL, roles = setOf("hr"))
        val hrResults = enforcer.filterAuthorizedResults(candidates, hrAcl)
        assertEquals(3, hrResults.size)
        assertEquals("c3", hrResults[2].chunk.id)

        // Unauthorized role for confidential chunk: CONFIDENTIAL clearance but lacks HR role
        val salesAcl = UserAcl(userId = "sales-1", clearance = SecurityClassification.CONFIDENTIAL, roles = setOf("sales"))
        val salesResults = enforcer.filterAuthorizedResults(candidates, salesAcl)
        assertEquals(2, salesResults.size) // Cannot see c3
    }
}
