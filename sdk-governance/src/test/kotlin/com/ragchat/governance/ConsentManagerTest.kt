package com.ragchat.governance

import com.ragchat.api.governance.ConsentPurpose
import com.ragchat.governance.consent.ConsentManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentManagerTest {
    @Test
    fun testConsentGrantAndWithdrawalWorkflow() =
        runTest {
            val manager = ConsentManager()
            var withdrawalTriggered = false
            var withdrawnPurpose: ConsentPurpose? = null

            manager.onConsentWithdrawn { purpose ->
                withdrawalTriggered = true
                withdrawnPurpose = purpose
            }

            // Initially no consent
            assertFalse(manager.hasConsent("CLOUD_PROCESSING"))

            // Grant consent
            manager.updateConsent(ConsentPurpose.CLOUD_PROCESSING, granted = true, version = 1)
            assertTrue(manager.hasConsent("CLOUD_PROCESSING"))
            assertFalse(withdrawalTriggered)

            // Withdraw consent
            manager.updateConsent(ConsentPurpose.CLOUD_PROCESSING, granted = false, version = 1)
            assertFalse(manager.hasConsent("CLOUD_PROCESSING"))
            assertTrue(withdrawalTriggered)
            assertEquals(ConsentPurpose.CLOUD_PROCESSING, withdrawnPurpose)
        }
}
