package com.ragchat.api.governance

/**
 * Result of evaluating a prompt or action against governance policies.
 */
public sealed interface PolicyDecision {
    /**
     * Action is permitted.
     */
    public data object Allowed : PolicyDecision

    /**
     * Action is rejected due to policy restrictions.
     *
     * @property ruleId Identifier of the violated rule.
     * @property category Categorical reason (e.g., "DATA_EXFILTRATION", "HARMFUL_CONTENT").
     */
    public data class Denied(
        val ruleId: String,
        val category: String,
    ) : PolicyDecision
}
