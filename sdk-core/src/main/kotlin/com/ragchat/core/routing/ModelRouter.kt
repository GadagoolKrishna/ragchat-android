package com.ragchat.core.routing

import com.ragchat.api.audit.AuditSink
import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.api.config.RagChatConfig
import com.ragchat.api.error.SdkError
import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.model.Chunk
import com.ragchat.api.routing.DeviceContext
import com.ragchat.api.routing.DeviceTier
import com.ragchat.api.routing.NetworkType
import com.ragchat.api.routing.RoutingDecision
import com.ragchat.api.routing.RoutingReasonCode
import com.ragchat.api.routing.ThermalLevel

/**
 * Request parameter payload consumed by [ModelRouter.route].
 */
public data class RouterInput(
    val query: String,
    val routingMode: ModelRoutingMode,
    val workspaceId: String,
    val sessionId: String,
    val chunks: List<Chunk> = emptyList(),
    val requiredContextTokens: Int = 0,
    val historyTurnCount: Int = 0,
    val requestedMaxTokens: Int = 256,
    val deviceContext: DeviceContext = DeviceContext(),
)

/**
 * Intelligent LLM Model Router evaluating device capabilities, policy rules, thermal/battery constraints,
 * query complexity, and circuit breakers to select optimal execution providers.
 */
@Suppress("ReturnCount", "TooManyFunctions")
public class ModelRouter(
    private val config: RagChatConfig,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(),
) {
    /**
     * Determines the optimal provider for the given [input] and logs the decision to [AuditSink].
     */
    public suspend fun route(input: RouterInput): RoutingDecision {
        val decision = computeDecision(input)
        logAudit(decision, input)
        return decision
    }

    private suspend fun computeDecision(input: RouterInput): RoutingDecision {
        val local = config.localLlmProvider
        val cloud = config.cloudLlmProvider

        checkExplicitModes(input, local, cloud)?.let { return it }
        checkOfflineConnectivity(input, local)?.let { return it }
        checkConfidentialData(input, local)?.let { return it }
        checkPolicyPreconditions(input, local, cloud)?.let { return it }
        checkDeviceEnvironment(input, cloud)?.let { return it }
        checkCapacityAndHealth(input, local, cloud)?.let { return it }
        checkQueryComplexity(input, cloud)?.let { return it }

        return resolveDefaultPreferences(input.routingMode, local, cloud)
    }

    private suspend fun checkExplicitModes(
        input: RouterInput,
        local: LlmProvider?,
        cloud: LlmProvider?,
    ): RoutingDecision? {
        if (input.routingMode == ModelRoutingMode.LOCAL_ONLY) {
            val p = checkNotNull(local) { "Local LLM provider required for LOCAL_ONLY routing" }
            validateLocalAvailability(p)
            return RoutingDecision(p, listOf(RoutingReasonCode.LOCAL_ONLY_CONFIG))
        }
        if (input.routingMode == ModelRoutingMode.CLOUD_ONLY) {
            val p = checkNotNull(cloud) { "Cloud LLM provider required for CLOUD_ONLY routing" }
            assertCloudPolicyPermitted(p, input)
            return RoutingDecision(p, listOf(RoutingReasonCode.CLOUD_ONLY_CONFIG))
        }
        return null
    }

    private suspend fun checkOfflineConnectivity(
        input: RouterInput,
        local: LlmProvider?,
    ): RoutingDecision? {
        if (input.deviceContext.networkType != NetworkType.NONE) return null
        if (local != null && local.availability() == Availability.AVAILABLE) {
            return RoutingDecision(local, listOf(RoutingReasonCode.OFFLINE_NETWORK))
        }
        throw SdkError.ModelUnavailableError("offline", "No network connection available and local model is unavailable")
    }

    private suspend fun checkConfidentialData(
        input: RouterInput,
        local: LlmProvider?,
    ): RoutingDecision? {
        val sensitiveLabels = setOf("CONFIDENTIAL", "RESTRICTED", "SECRET", "PII")
        val isConfidential =
            input.chunks.any { chunk ->
                val s = chunk.metadata["sensitivity"]?.uppercase()
                s != null && sensitiveLabels.contains(s)
            }
        if (!isConfidential) return null

        val p = checkNotNull(local) { "Confidential data cannot route to cloud, but no local LLM available" }
        validateLocalAvailability(p)
        return RoutingDecision(p, listOf(RoutingReasonCode.CONFIDENTIAL_RESTRICTION))
    }

    private suspend fun checkPolicyPreconditions(
        input: RouterInput,
        local: LlmProvider?,
        cloud: LlmProvider?,
    ): RoutingDecision? {
        val cloudPolicy = evaluateCloudPolicy(cloud, input)
        if (cloudPolicy !is PolicyDecision.Denied) return null

        val p = checkNotNull(local) { "Cloud route denied by policy and no local provider configured" }
        validateLocalAvailability(p)
        val code =
            when {
                cloudPolicy.ruleId.contains("NETWORK") || input.deviceContext.isRoaming ->
                    RoutingReasonCode.ROAMING_RESTRICTION
                cloudPolicy.ruleId.contains("QUOTA") ->
                    RoutingReasonCode.DAILY_TOKEN_QUOTA_EXCEEDED
                else ->
                    RoutingReasonCode.WORKSPACE_RESTRICTION
            }
        return RoutingDecision(p, listOf(code))
    }

    private fun checkDeviceEnvironment(
        input: RouterInput,
        cloud: LlmProvider?,
    ): RoutingDecision? {
        if (cloud == null) return null
        val isThermalSevere =
            input.deviceContext.thermal == ThermalLevel.SEVERE ||
                input.deviceContext.thermal == ThermalLevel.CRITICAL
        if (isThermalSevere) {
            return RoutingDecision(cloud, listOf(RoutingReasonCode.THERMAL_THROTTLED))
        }
        val isBatteryLow = input.deviceContext.battery.levelPercent < 15 && !input.deviceContext.battery.isCharging
        if (isBatteryLow) {
            return RoutingDecision(cloud, listOf(RoutingReasonCode.BATTERY_LOW))
        }
        return null
    }

    private fun checkCapacityAndHealth(
        input: RouterInput,
        local: LlmProvider?,
        cloud: LlmProvider?,
    ): RoutingDecision? {
        if (local == null || cloud == null) return null
        if (input.requiredContextTokens > local.capabilities.contextWindow) {
            return RoutingDecision(cloud, listOf(RoutingReasonCode.CONTEXT_WINDOW_EXCEEDED))
        }
        if (circuitBreaker.getState(local.id) == CircuitState.OPEN) {
            return RoutingDecision(cloud, listOf(RoutingReasonCode.CIRCUIT_BREAKER_TRIPPED))
        }
        return null
    }

    private fun checkQueryComplexity(
        input: RouterInput,
        cloud: LlmProvider?,
    ): RoutingDecision? {
        if (cloud == null || input.deviceContext.deviceTier == DeviceTier.HIGH_END) return null
        val isComplex =
            QueryComplexityHeuristic.isHighComplexity(
                query = input.query,
                historyTurnCount = input.historyTurnCount,
                requestedMaxTokens = input.requestedMaxTokens,
            )
        return if (isComplex) {
            RoutingDecision(cloud, listOf(RoutingReasonCode.QUERY_COMPLEXITY_HIGH))
        } else {
            null
        }
    }

    private suspend fun resolveDefaultPreferences(
        mode: ModelRoutingMode,
        local: LlmProvider?,
        cloud: LlmProvider?,
    ): RoutingDecision =
        when (mode) {
            ModelRoutingMode.LOCAL_FIRST -> {
                if (local != null && local.availability() == Availability.AVAILABLE) {
                    RoutingDecision(local, listOf(RoutingReasonCode.DEFAULT_PREFERENCE))
                } else if (cloud != null) {
                    RoutingDecision(cloud, listOf(RoutingReasonCode.FALLBACK_ALLOWED))
                } else {
                    val p = checkNotNull(local) { "No LLM provider available" }
                    RoutingDecision(p, listOf(RoutingReasonCode.DEFAULT_PREFERENCE))
                }
            }
            else -> {
                val p = cloud ?: checkNotNull(local) { "No LLM provider available" }
                RoutingDecision(p, listOf(RoutingReasonCode.DEFAULT_PREFERENCE))
            }
        }

    private suspend fun evaluateCloudPolicy(
        cloud: LlmProvider?,
        input: RouterInput,
    ): PolicyDecision {
        if (cloud == null) return PolicyDecision.Denied("NO_CLOUD_CONFIGURED", "Cloud provider not present")
        val policy = config.policyProvider ?: return PolicyDecision.Allowed
        return policy.evaluate("LLM_INFERENCE", buildPolicyContext("CLOUD", cloud.id, input))
    }

    private suspend fun assertCloudPolicyPermitted(
        cloud: LlmProvider,
        input: RouterInput,
    ) {
        val policy = config.policyProvider ?: return
        val decision = policy.evaluate("LLM_INFERENCE", buildPolicyContext("CLOUD", cloud.id, input))
        if (decision is PolicyDecision.Denied) {
            throw SdkError.PolicyViolationError(decision.ruleId, decision.category)
        }
    }

    private fun buildPolicyContext(
        locality: String,
        providerId: String,
        input: RouterInput,
    ): Map<String, String> {
        val labels =
            input.chunks
                .mapNotNull { it.metadata["sensitivity"] }
                .distinct()
                .joinToString(",")
        return mapOf(
            "targetLocality" to locality,
            "providerId" to providerId,
            "workspaceId" to input.workspaceId,
            "sessionId" to input.sessionId,
            "isRoaming" to input.deviceContext.isRoaming.toString(),
            "requestedTokens" to input.requestedMaxTokens.toString(),
            "chunkLabels" to labels,
        )
    }

    private suspend fun validateLocalAvailability(provider: LlmProvider) {
        val avail = provider.availability()
        if (avail != Availability.AVAILABLE) {
            throw SdkError.ModelUnavailableError(provider.id, "Local model status: $avail")
        }
    }

    private suspend fun logAudit(
        decision: RoutingDecision,
        input: RouterInput,
    ) {
        val audit = config.auditSink ?: return
        val meta =
            mapOf(
                "selectedProvider" to decision.provider.id,
                "locality" to decision.provider.capabilities.locality.name,
                "reasons" to decision.reasonCodes.joinToString(","),
                "deviceTier" to input.deviceContext.deviceTier.name,
                "thermalLevel" to input.deviceContext.thermal.name,
                "isFallback" to decision.isFallback.toString(),
            )
        audit.recordAudit(action = "MODEL_ROUTED", metadata = meta)
    }
}
