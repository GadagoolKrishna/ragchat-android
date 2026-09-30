package com.ragchat.governance.integrity

import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceIntegrityPolicyProviderTest {
    private class FakeIntegrityProvider(
        var recognized: Boolean = true,
        var licensed: Boolean = true,
        var rooted: Boolean = false,
    ) : DeviceIntegrityProvider {
        override suspend fun fetchVerdict(): DeviceIntegrityVerdict =
            DeviceIntegrityVerdict(
                isDeviceRecognized = recognized,
                isAppLicensed = licensed,
                isRooted = rooted,
                verdictSummary = "test_verdict",
            )
    }

    private class AllowAllPolicy : PolicyProvider {
        override suspend fun evaluate(
            intent: String,
            context: Map<String, String>,
        ): PolicyDecision = PolicyDecision.Allowed
    }

    @Test
    fun testRootedDeviceIsDenied() =
        runBlocking {
            val integrity = FakeIntegrityProvider(rooted = true)
            val provider =
                DeviceIntegrityPolicyProvider(
                    delegate = AllowAllPolicy(),
                    integrityProvider = integrity,
                    blockOnRooted = true,
                )

            val decision = provider.evaluate("query", emptyMap())
            assertTrue(decision is PolicyDecision.Denied)
            val denied = decision as PolicyDecision.Denied
            assertEquals("DEVICE_INTEGRITY_ROOT_DETECTED", denied.ruleId)
            assertEquals("DEVICE_COMPROMISE", denied.category)
        }

    @Test
    fun testUnrecognizedDeviceIsDenied() =
        runBlocking {
            val integrity = FakeIntegrityProvider(recognized = false)
            val provider =
                DeviceIntegrityPolicyProvider(
                    delegate = AllowAllPolicy(),
                    integrityProvider = integrity,
                    requireDeviceRecognition = true,
                )

            val decision = provider.evaluate("query", emptyMap())
            assertTrue(decision is PolicyDecision.Denied)
            val denied = decision as PolicyDecision.Denied
            assertEquals("DEVICE_INTEGRITY_PLAY_ATTESTATION_FAILED", denied.ruleId)
            assertEquals("HARDWARE_ATTESTATION_FAILURE", denied.category)
        }

    @Test
    fun testCleanDeviceIsAllowed() =
        runBlocking {
            val integrity = FakeIntegrityProvider(recognized = true, rooted = false)
            val provider =
                DeviceIntegrityPolicyProvider(
                    delegate = AllowAllPolicy(),
                    integrityProvider = integrity,
                )

            val decision = provider.evaluate("query", emptyMap())
            assertTrue(decision is PolicyDecision.Allowed)
        }
}
