package com.ragchat.api.routing

import com.ragchat.api.llm.LlmProvider

/**
 * Standardized categorization reasons determining provider selection.
 */
public enum class RoutingReasonCode {
    /**
     * Selected because configuration forced local-only execution.
     */
    LOCAL_ONLY_CONFIG,

    /**
     * Selected because configuration forced cloud-only execution.
     */
    CLOUD_ONLY_CONFIG,

    /**
     * Cloud inference prohibited because one or more retrieved chunks are labeled confidential.
     */
    CONFIDENTIAL_RESTRICTION,

    /**
     * Cloud inference prohibited for the active workspace.
     */
    WORKSPACE_RESTRICTION,

    /**
     * Cloud inference prohibited because the device is roaming on cellular data.
     */
    ROAMING_RESTRICTION,

    /**
     * Local execution avoided due to severe device thermal throttling.
     */
    THERMAL_THROTTLED,

    /**
     * Local execution avoided due to critically low battery state.
     */
    BATTERY_LOW,

    /**
     * Cloud selected because prompt context exceeds local model context window.
     */
    CONTEXT_WINDOW_EXCEEDED,

    /**
     * Cloud selected because query complexity heuristic requires a higher capability tier.
     */
    QUERY_COMPLEXITY_HIGH,

    /**
     * Local provider skipped because its circuit breaker is currently tripped open.
     */
    CIRCUIT_BREAKER_TRIPPED,

    /**
     * Cloud execution rejected because daily token quota has been exceeded.
     */
    DAILY_TOKEN_QUOTA_EXCEEDED,

    /**
     * Cloud fallback engaged after primary local execution failure.
     */
    FALLBACK_ALLOWED,

    /**
     * Local execution chosen as optimal for the device capability tier.
     */
    DEVICE_TIER_OPTIMAL,

    /**
     * Local execution chosen because no network connection is available.
     */
    OFFLINE_NETWORK,

    /**
     * Default routing strategy preference applied.
     */
    DEFAULT_PREFERENCE,
}

/**
 * Result of the ModelRouter decision process.
 *
 * @property provider The selected [LlmProvider] to handle generation.
 * @property reasonCodes Ordered list of justification codes that influenced this routing decision.
 * @property isFallback True if this selection was triggered as a fallback after an initial failure.
 */
public data class RoutingDecision(
    val provider: LlmProvider,
    val reasonCodes: List<RoutingReasonCode>,
    val isFallback: Boolean = false,
)
