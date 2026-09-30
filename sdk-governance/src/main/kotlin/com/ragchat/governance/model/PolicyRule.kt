package com.ragchat.governance.model

/**
 * Declarative rule restricting or guiding LLM and data operations.
 */
public sealed interface PolicyRule {
    /**
     * Unique identifier for the policy rule.
     */
    public val ruleId: String

    /**
     * Categorical classification of the rule.
     */
    public val category: String

    /**
     * Prevents data leaving the device if any chunk contains confidential or restricted sensitivity labels.
     */
    public data class ConfidentialChunksNeverLeaveDevice(
        override val ruleId: String = "RULE-RESIDENCY-001",
        override val category: String = "DATA_RESIDENCY",
        val sensitiveLabels: Set<String> = setOf("CONFIDENTIAL", "RESTRICTED", "SECRET", "PII"),
    ) : PolicyRule

    /**
     * Forbids cloud model routing for specified workspace identifiers.
     *
     * @property workspaceIds Set of workspace IDs where cloud inference is blocked.
     */
    public data class DisallowCloudForWorkspace(
        val workspaceIds: Set<String>,
        override val ruleId: String = "RULE-WORKSPACE-001",
        override val category: String = "WORKSPACE_RESTRICTION",
    ) : PolicyRule

    /**
     * Prohibits cloud inference if the device is currently in a cellular roaming state.
     */
    public data class BlockCloudWhenRoaming(
        override val ruleId: String = "RULE-NETWORK-001",
        override val category: String = "COST_AND_NETWORK_CONTROL",
    ) : PolicyRule

    /**
     * Restricts cloud inference to designated geographic cloud service regions.
     *
     * @property allowedRegions Whitelist of permissible region codes (e.g. "us-central1", "europe-west1").
     */
    public data class AllowedCloudRegions(
        val allowedRegions: Set<String>,
        override val ruleId: String = "RULE-REGION-001",
        override val category: String = "GEOGRAPHIC_RESTRICTION",
    ) : PolicyRule

    /**
     * Restricts LLM routing exclusively to an explicit whitelist of provider IDs.
     *
     * @property allowedProviderIds Whitelist of valid provider IDs.
     */
    public data class AllowedProviders(
        val allowedProviderIds: Set<String>,
        override val ruleId: String = "RULE-PROVIDER-001",
        override val category: String = "PROVIDER_WHITELIST",
    ) : PolicyRule

    /**
     * Establishes a daily cap on cloud token usage to prevent bill overrun.
     *
     * @property maxTokens Maximum cumulative cloud tokens permitted per calendar day.
     */
    public data class MaxCloudTokensPerDay(
        val maxTokens: Long,
        override val ruleId: String = "RULE-QUOTA-001",
        override val category: String = "BUDGET_QUOTA",
    ) : PolicyRule
}
