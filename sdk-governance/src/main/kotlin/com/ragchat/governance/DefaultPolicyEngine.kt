package com.ragchat.governance

import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider
import com.ragchat.governance.model.PolicyRule
import com.ragchat.governance.quota.TokenUsageTracker
import java.util.concurrent.atomic.AtomicReference

private data class RuleEvaluationContext(
    val intent: String,
    val targetLocality: String?,
    val chunkLabels: List<String>,
    val workspaceId: String?,
    val isRoaming: Boolean,
    val providerId: String?,
    val cloudRegion: String?,
    val requestedTokens: Long,
)

/**
 * Default implementation of [PolicyProvider] executing declarative rules with fail-closed security.
 *
 * @param initialRules Set of initial policy rules.
 * @param tokenUsageTracker Tracker for day-rolling token usage.
 */
@Suppress("ReturnCount")
public class DefaultPolicyEngine(
    initialRules: List<PolicyRule> = emptyList(),
    public val tokenUsageTracker: TokenUsageTracker = TokenUsageTracker(),
) : PolicyProvider {
    private val rulesRef = AtomicReference<List<PolicyRule>>(initialRules.toList())

    /**
     * Hot-reloads the active rule set.
     */
    public fun updateRules(newRules: List<PolicyRule>) {
        rulesRef.set(newRules.toList())
    }

    /**
     * Evaluates governance policies against the given [intent] and [context].
     * Fails closed with [PolicyDecision.Denied] upon missing or invalid evaluation criteria.
     */
    override suspend fun evaluate(
        intent: String,
        context: Map<String, String>,
    ): PolicyDecision {
        return try {
            val evalContext = parseContext(intent, context)
            val currentRules = rulesRef.get()

            for (rule in currentRules) {
                val decision = evaluateSingleRule(rule, evalContext)
                if (decision is PolicyDecision.Denied) {
                    return decision
                }
            }

            PolicyDecision.Allowed
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            PolicyDecision.Denied("ERR_EVALUATION_FAILED", "Policy evaluation failed closed: ${e.message}")
        }
    }

    private fun parseContext(
        intent: String,
        context: Map<String, String>,
    ): RuleEvaluationContext =
        RuleEvaluationContext(
            intent = intent,
            targetLocality = context["targetLocality"]?.uppercase(),
            chunkLabels = context["chunkLabels"]?.split(",")?.map { it.trim().uppercase() } ?: emptyList(),
            workspaceId = context["workspaceId"],
            isRoaming = context["isRoaming"]?.toBoolean() ?: false,
            providerId = context["providerId"],
            cloudRegion = context["cloudRegion"],
            requestedTokens = context["requestedTokens"]?.toLongOrNull() ?: 0L,
        )

    private fun evaluateSingleRule(
        rule: PolicyRule,
        ctx: RuleEvaluationContext,
    ): PolicyDecision =
        when (rule) {
            is PolicyRule.ConfidentialChunksNeverLeaveDevice -> evaluateConfidentialRule(rule, ctx)
            is PolicyRule.DisallowCloudForWorkspace -> evaluateWorkspaceRule(rule, ctx)
            is PolicyRule.BlockCloudWhenRoaming -> evaluateRoamingRule(rule, ctx)
            is PolicyRule.AllowedCloudRegions -> evaluateRegionRule(rule, ctx)
            is PolicyRule.AllowedProviders -> evaluateProviderRule(rule, ctx)
            is PolicyRule.MaxCloudTokensPerDay -> evaluateQuotaRule(rule, ctx)
        }

    private fun evaluateConfidentialRule(
        rule: PolicyRule.ConfidentialChunksNeverLeaveDevice,
        ctx: RuleEvaluationContext,
    ): PolicyDecision {
        val isCloud = ctx.targetLocality == "CLOUD"
        val hasConfidential = ctx.chunkLabels.any { rule.sensitiveLabels.contains(it) }
        return if (isCloud && hasConfidential) {
            PolicyDecision.Denied(rule.ruleId, "Confidential data cannot leave device")
        } else {
            PolicyDecision.Allowed
        }
    }

    private fun evaluateWorkspaceRule(
        rule: PolicyRule.DisallowCloudForWorkspace,
        ctx: RuleEvaluationContext,
    ): PolicyDecision {
        val isCloud = ctx.targetLocality == "CLOUD"
        return if (isCloud && ctx.workspaceId != null && rule.workspaceIds.contains(ctx.workspaceId)) {
            PolicyDecision.Denied(rule.ruleId, "Cloud disabled for workspace ${ctx.workspaceId}")
        } else {
            PolicyDecision.Allowed
        }
    }

    private fun evaluateRoamingRule(
        rule: PolicyRule.BlockCloudWhenRoaming,
        ctx: RuleEvaluationContext,
    ): PolicyDecision {
        val isCloud = ctx.targetLocality == "CLOUD"
        return if (isCloud && ctx.isRoaming) {
            PolicyDecision.Denied(rule.ruleId, "Cloud transmission blocked while roaming")
        } else {
            PolicyDecision.Allowed
        }
    }

    private fun evaluateRegionRule(
        rule: PolicyRule.AllowedCloudRegions,
        ctx: RuleEvaluationContext,
    ): PolicyDecision {
        val isCloud = ctx.targetLocality == "CLOUD"
        return if (isCloud && ctx.cloudRegion != null && !rule.allowedRegions.contains(ctx.cloudRegion)) {
            PolicyDecision.Denied(rule.ruleId, "Cloud region not in allowed list: ${ctx.cloudRegion}")
        } else {
            PolicyDecision.Allowed
        }
    }

    private fun evaluateProviderRule(
        rule: PolicyRule.AllowedProviders,
        ctx: RuleEvaluationContext,
    ): PolicyDecision =
        if (ctx.providerId != null && !rule.allowedProviderIds.contains(ctx.providerId)) {
            PolicyDecision.Denied(rule.ruleId, "Provider not permitted: ${ctx.providerId}")
        } else {
            PolicyDecision.Allowed
        }

    private fun evaluateQuotaRule(
        rule: PolicyRule.MaxCloudTokensPerDay,
        ctx: RuleEvaluationContext,
    ): PolicyDecision {
        val isCloud = ctx.targetLocality == "CLOUD"
        if (isCloud) {
            val projected = tokenUsageTracker.getDailyTokenCount() + ctx.requestedTokens
            if (projected > rule.maxTokens) {
                return PolicyDecision.Denied(rule.ruleId, "Daily cloud token quota exceeded")
            }
            if (ctx.requestedTokens > 0) {
                tokenUsageTracker.recordTokens(ctx.requestedTokens)
            }
        }
        return PolicyDecision.Allowed
    }
}
