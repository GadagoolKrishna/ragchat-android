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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelRouterTableTest {
    private val localProvider =
        FakeLlmProvider(
            id = "gemini-nano-local",
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
            id = "gemini-1.5-cloud",
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

    data class Scenario(
        val name: String,
        val mode: ModelRoutingMode,
        val query: String = "What is the capital of France?",
        val chunks: List<Chunk> = emptyList(),
        val contextTokens: Int = 500,
        val deviceTier: DeviceTier = DeviceTier.MID_RANGE,
        val battery: BatteryStatus = BatteryStatus(levelPercent = 80, isCharging = true),
        val thermal: ThermalLevel = ThermalLevel.NORMAL,
        val network: NetworkType = NetworkType.WIFI,
        val isRoaming: Boolean = false,
        val tripLocalCircuitBreaker: Boolean = false,
        val workspaceId: String = "default-ws",
        val expectedProviderId: String,
        val expectedPrimaryReason: RoutingReasonCode,
    )

    @Test
    @Suppress("LongMethod")
    fun testTableDrivenRoutingMatrix() =
        runTest {
            val policyEngine =
                DefaultPolicyEngine(
                    policy {
                        denyConfidentialToCloud()
                        disallowCloudForWorkspace("finance-restricted")
                        blockCloudWhenRoaming()
                    },
                )

            val scenarios = mutableListOf<Scenario>()

            // 1-5: Explicit LOCAL_ONLY scenarios
            scenarios.add(
                Scenario(
                    name = "Local Only Normal",
                    mode = ModelRoutingMode.LOCAL_ONLY,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.LOCAL_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local Only Low Battery",
                    mode = ModelRoutingMode.LOCAL_ONLY,
                    battery = BatteryStatus(10, false),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.LOCAL_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local Only Thermal Severe",
                    mode = ModelRoutingMode.LOCAL_ONLY,
                    thermal = ThermalLevel.SEVERE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.LOCAL_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local Only Offline",
                    mode = ModelRoutingMode.LOCAL_ONLY,
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.LOCAL_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local Only Roaming",
                    mode = ModelRoutingMode.LOCAL_ONLY,
                    isRoaming = true,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.LOCAL_ONLY_CONFIG,
                ),
            )

            // 6-10: Explicit CLOUD_ONLY scenarios
            scenarios.add(
                Scenario(
                    name = "Cloud Only Normal",
                    mode = ModelRoutingMode.CLOUD_ONLY,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CLOUD_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud Only Low Battery",
                    mode = ModelRoutingMode.CLOUD_ONLY,
                    battery = BatteryStatus(10, false),
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CLOUD_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud Only High Context",
                    mode = ModelRoutingMode.CLOUD_ONLY,
                    contextTokens = 10_000,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CLOUD_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud Only Cellular",
                    mode = ModelRoutingMode.CLOUD_ONLY,
                    network = NetworkType.CELLULAR,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CLOUD_ONLY_CONFIG,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud Only Low End Tier",
                    mode = ModelRoutingMode.CLOUD_ONLY,
                    deviceTier = DeviceTier.LOW_END,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CLOUD_ONLY_CONFIG,
                ),
            )

            // 11-15: Offline network handling across modes
            scenarios.add(
                Scenario(
                    name = "Local First Offline",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.OFFLINE_NETWORK,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud First Offline",
                    mode = ModelRoutingMode.CLOUD_FIRST,
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.OFFLINE_NETWORK,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Auto Offline",
                    mode = ModelRoutingMode.AUTO,
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.OFFLINE_NETWORK,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Complex Query Offline",
                    mode = ModelRoutingMode.AUTO,
                    query = "Compare quantum vs classical computing step by step",
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.OFFLINE_NETWORK,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Low Battery Offline",
                    mode = ModelRoutingMode.AUTO,
                    battery = BatteryStatus(5, false),
                    network = NetworkType.NONE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.OFFLINE_NETWORK,
                ),
            )

            // 16-20: Confidential data protection across modes
            val confidentialChunk =
                Chunk(
                    id = "c1",
                    documentId = "d1",
                    content = "Secret",
                    sequenceNumber = 0,
                    tokenCount = 10,
                    metadata = mapOf("sensitivity" to "CONFIDENTIAL"),
                )
            scenarios.add(
                Scenario(
                    name = "Confidential Local First",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    chunks = listOf(confidentialChunk),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONFIDENTIAL_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Confidential Cloud First",
                    mode = ModelRoutingMode.CLOUD_FIRST,
                    chunks = listOf(confidentialChunk),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONFIDENTIAL_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Confidential Auto",
                    mode = ModelRoutingMode.AUTO,
                    chunks = listOf(confidentialChunk),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONFIDENTIAL_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Confidential Complex Query",
                    mode = ModelRoutingMode.AUTO,
                    query = "Analyze and summarize in depth the confidential data",
                    chunks = listOf(confidentialChunk),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONFIDENTIAL_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Confidential Low Battery",
                    mode = ModelRoutingMode.AUTO,
                    battery = BatteryStatus(5, false),
                    chunks = listOf(confidentialChunk),
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONFIDENTIAL_RESTRICTION,
                ),
            )

            // 21-25: Policy workspace & roaming restrictions
            scenarios.add(
                Scenario(
                    name = "Restricted Workspace Local First",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    workspaceId = "finance-restricted",
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.WORKSPACE_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Restricted Workspace Cloud First",
                    mode = ModelRoutingMode.CLOUD_FIRST,
                    workspaceId = "finance-restricted",
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.WORKSPACE_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Restricted Workspace Auto",
                    mode = ModelRoutingMode.AUTO,
                    workspaceId = "finance-restricted",
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.WORKSPACE_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Roaming Cloud First",
                    mode = ModelRoutingMode.CLOUD_FIRST,
                    isRoaming = true,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.ROAMING_RESTRICTION,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Roaming Auto",
                    mode = ModelRoutingMode.AUTO,
                    isRoaming = true,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.ROAMING_RESTRICTION,
                ),
            )

            // 26-30: Device thermal and battery constraints
            scenarios.add(
                Scenario(
                    name = "Local First Thermal Severe",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    thermal = ThermalLevel.SEVERE,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.THERMAL_THROTTLED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local First Thermal Critical",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    thermal = ThermalLevel.CRITICAL,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.THERMAL_THROTTLED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Local First Low Battery",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    battery = BatteryStatus(10, false),
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.BATTERY_LOW,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Auto Thermal Severe",
                    mode = ModelRoutingMode.AUTO,
                    thermal = ThermalLevel.SEVERE,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.THERMAL_THROTTLED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Auto Low Battery",
                    mode = ModelRoutingMode.AUTO,
                    battery = BatteryStatus(12, false),
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.BATTERY_LOW,
                ),
            )

            // 31-35: Context window overflow
            scenarios.add(
                Scenario(
                    name = "Local First Context Exceeded",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    contextTokens = 5000,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONTEXT_WINDOW_EXCEEDED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Auto Context Exceeded",
                    mode = ModelRoutingMode.AUTO,
                    contextTokens = 8000,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONTEXT_WINDOW_EXCEEDED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Cloud First Context Exceeded",
                    mode = ModelRoutingMode.CLOUD_FIRST,
                    contextTokens = 10000,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CONTEXT_WINDOW_EXCEEDED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Normal Context Fits Local",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    contextTokens = 2000,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.DEFAULT_PREFERENCE,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Small Context Fits Local",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    contextTokens = 200,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.DEFAULT_PREFERENCE,
                ),
            )

            // 36-40: Circuit breaker & Query complexity heuristics
            val complexQuery = "Synthesize and compare in depth the trade-offs step by step"
            scenarios.add(
                Scenario(
                    name = "Local Circuit Breaker Tripped",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    tripLocalCircuitBreaker = true,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CIRCUIT_BREAKER_TRIPPED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Auto Circuit Breaker Tripped",
                    mode = ModelRoutingMode.AUTO,
                    tripLocalCircuitBreaker = true,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.CIRCUIT_BREAKER_TRIPPED,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Complex Query Mid Range",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    query = complexQuery,
                    deviceTier = DeviceTier.MID_RANGE,
                    expectedProviderId = cloudProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.QUERY_COMPLEXITY_HIGH,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Complex Query High End Remains Local",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    query = complexQuery,
                    deviceTier = DeviceTier.HIGH_END,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.DEFAULT_PREFERENCE,
                ),
            )
            scenarios.add(
                Scenario(
                    name = "Simple Query Local First Mid Range",
                    mode = ModelRoutingMode.LOCAL_FIRST,
                    query = "Hello",
                    deviceTier = DeviceTier.MID_RANGE,
                    expectedProviderId = localProvider.id,
                    expectedPrimaryReason = RoutingReasonCode.DEFAULT_PREFERENCE,
                ),
            )

            // Run all 40 scenarios
            for ((idx, sc) in scenarios.withIndex()) {
                val circuitBreaker = CircuitBreaker()
                if (sc.tripLocalCircuitBreaker) {
                    repeat(3) { circuitBreaker.recordFailure(localProvider.id) }
                }

                val config =
                    RagChatConfigBuilder()
                        .apply {
                            localLlmProvider = localProvider
                            cloudLlmProvider = cloudProvider
                            this.policyProvider = policyEngine
                        }.build()

                val router = ModelRouter(config, circuitBreaker)
                val input =
                    RouterInput(
                        query = sc.query,
                        routingMode = sc.mode,
                        workspaceId = sc.workspaceId,
                        sessionId = "test-session",
                        chunks = sc.chunks,
                        requiredContextTokens = sc.contextTokens,
                        deviceContext =
                            DeviceContext(
                                deviceTier = sc.deviceTier,
                                battery = sc.battery,
                                thermal = sc.thermal,
                                networkType = sc.network,
                                isRoaming = sc.isRoaming,
                            ),
                    )

                val decision = router.route(input)
                assertEquals(
                    expected = sc.expectedProviderId,
                    actual = decision.provider.id,
                    message = "Scenario #${idx + 1} (${sc.name}) failed on provider selection",
                )
                assertTrue(
                    actual = decision.reasonCodes.contains(sc.expectedPrimaryReason),
                    message = "Scenario #${idx + 1} failed reason",
                )
            }
        }
}
