package com.ragchat.governance.dsl

import com.ragchat.governance.model.PolicyRule

/**
 * Type-safe builder for constructing [PolicyRule] collections.
 */
public class PolicyBuilder {
    private val rules = mutableListOf<PolicyRule>()

    /**
     * Prevents any chunk labeled with confidential/restricted sensitivity from leaving the device.
     */
    public fun denyConfidentialToCloud(sensitiveLabels: Set<String> = setOf("CONFIDENTIAL", "RESTRICTED", "SECRET", "PII")) {
        rules.add(PolicyRule.ConfidentialChunksNeverLeaveDevice(sensitiveLabels = sensitiveLabels))
    }

    /**
     * Disallows cloud inference for specified workspace IDs.
     */
    public fun disallowCloudForWorkspace(vararg workspaceIds: String) {
        rules.add(PolicyRule.DisallowCloudForWorkspace(workspaceIds.toSet()))
    }

    /**
     * Blocks cloud inference when roaming on cellular data.
     */
    public fun blockCloudWhenRoaming() {
        rules.add(PolicyRule.BlockCloudWhenRoaming())
    }

    /**
     * Restricts cloud endpoints to an allowed set of regions.
     */
    public fun allowedCloudRegions(vararg regions: String) {
        rules.add(PolicyRule.AllowedCloudRegions(regions.toSet()))
    }

    /**
     * Whitelists specific LLM providers.
     */
    public fun allowedProviders(vararg providerIds: String) {
        rules.add(PolicyRule.AllowedProviders(providerIds.toSet()))
    }

    /**
     * Caps daily cloud token consumption.
     */
    public fun maxDailyCloudTokens(maxTokens: Long) {
        rules.add(PolicyRule.MaxCloudTokensPerDay(maxTokens))
    }

    /**
     * Builds and returns the compiled list of [PolicyRule] objects.
     */
    public fun build(): List<PolicyRule> = rules.toList()
}

/**
 * Entry point for declaring policies via a clean Kotlin DSL.
 */
public fun policy(block: PolicyBuilder.() -> Unit): List<PolicyRule> = PolicyBuilder().apply(block).build()
