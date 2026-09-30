package com.ragchat.core

import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Locality
import com.ragchat.api.routing.BatteryStatus
import com.ragchat.api.routing.DeviceContext
import com.ragchat.api.routing.DeviceTier
import com.ragchat.api.routing.NetworkType
import com.ragchat.api.routing.RoutingReasonCode
import com.ragchat.api.routing.ThermalLevel
import com.ragchat.core.routing.CircuitBreaker
import com.ragchat.core.routing.ModelRouter
import com.ragchat.core.routing.RouterInput
import com.ragchat.governance.DefaultPolicyEngine
import com.ragchat.governance.dsl.policy
import com.ragchat.testing.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfidentialExfiltrationPropertyTest {
    private val localProvider =
        FakeLlmProvider(
            id = "on-device-nano",
            currentAvailability = Availability.AVAILABLE,
            capabilities =
                LlmCapabilities(
                    contextWindow = 4096,
                    maxOutputTokens = 1024,
                    streaming = true,
                    toolCalling = false,
                    multimodal = false,
                    locality = Locality.LOCAL,
                ),
        )

    private val cloudProvider =
        FakeLlmProvider(
            id = "cloud-enterprise-llm",
            currentAvailability = Availability.AVAILABLE,
            capabilities =
                LlmCapabilities(
                    contextWindow = 128_000,
                    maxOutputTokens = 4096,
                    streaming = true,
                    toolCalling = false,
                    multimodal = false,
                    locality = Locality.CLOUD,
                ),
        )

    @Test
    @Suppress("LongMethod")
    fun propertyTestConfidentialChunksNeverReachCloud() =
        runTest {
            val policyEngine =
                DefaultPolicyEngine(
                    policy {
                        denyConfidentialToCloud()
                    },
                )

            val config =
                RagChatConfigBuilder()
                    .apply {
                        localLlmProvider = localProvider
                        cloudLlmProvider = cloudProvider
                        this.policyProvider = policyEngine
                    }.build()

            val router = ModelRouter(config, CircuitBreaker())
            val random = Random(42)

            val sensitiveLabels = listOf("CONFIDENTIAL", "RESTRICTED", "SECRET", "PII")
            val modes =
                listOf(
                    ModelRoutingMode.LOCAL_FIRST,
                    ModelRoutingMode.CLOUD_FIRST,
                    ModelRoutingMode.AUTO,
                )
            val networkTypes = listOf(NetworkType.WIFI, NetworkType.CELLULAR, NetworkType.ETHERNET)
            val thermalLevels = ThermalLevel.values().toList()
            val tiers = DeviceTier.values().toList()

            // Generate 200 randomized environmental permutations with confidential data
            for (iteration in 1..200) {
                val label = sensitiveLabels[random.nextInt(sensitiveLabels.size)]
                val nonSensitiveCount = random.nextInt(0, 5)
                val chunks = mutableListOf<Chunk>()

                for (i in 0 until nonSensitiveCount) {
                    chunks.add(
                        Chunk(
                            id = "chunk-$iteration-$i",
                            documentId = "doc-$iteration",
                            content = "Regular text chunk $i",
                            sequenceNumber = i,
                            tokenCount = 50,
                            metadata = mapOf("sensitivity" to "PUBLIC"),
                        ),
                    )
                }
                // Add the confidential chunk
                chunks.add(
                    Chunk(
                        id = "confidential-chunk-$iteration",
                        documentId = "doc-$iteration",
                        content = "Top Secret internal financial report",
                        sequenceNumber = nonSensitiveCount,
                        tokenCount = 100,
                        metadata = mapOf("sensitivity" to label),
                    ),
                )

                val mode = modes[random.nextInt(modes.size)]
                val network = networkTypes[random.nextInt(networkTypes.size)]
                val thermal = thermalLevels[random.nextInt(thermalLevels.size)]
                val tier = tiers[random.nextInt(tiers.size)]
                val batteryLevel = random.nextInt(1, 100)
                val isCharging = random.nextBoolean()
                val isRoaming = random.nextBoolean()
                val contextTokens = random.nextInt(100, 20_000)

                val input =
                    RouterInput(
                        query = "Evaluate the confidential data and synthesize conclusions",
                        routingMode = mode,
                        workspaceId = "workspace-$iteration",
                        sessionId = "session-$iteration",
                        chunks = chunks.shuffled(random),
                        requiredContextTokens = contextTokens,
                        deviceContext =
                            DeviceContext(
                                deviceTier = tier,
                                battery = BatteryStatus(levelPercent = batteryLevel, isCharging = isCharging),
                                thermal = thermal,
                                networkType = network,
                                isRoaming = isRoaming,
                            ),
                    )

                val decision = router.route(input)

                // Invariant: Provider MUST NEVER be CLOUD
                assertNotEquals(
                    illegal = cloudProvider.id,
                    actual = decision.provider.id,
                    message = "Violation in permutation #$iteration: Confidential chunk leaked to cloud!",
                )
                assertEquals(
                    expected = Locality.LOCAL,
                    actual = decision.provider.capabilities.locality,
                    message = "Violation in permutation #$iteration: Locality was not LOCAL",
                )
                assertTrue(
                    actual = decision.reasonCodes.contains(RoutingReasonCode.CONFIDENTIAL_RESTRICTION),
                    message = "Violation in permutation #$iteration: Reason did not record CONFIDENTIAL_RESTRICTION",
                )
            }
        }
}
