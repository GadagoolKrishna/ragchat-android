package com.ragchat.api.governance

/**
 * Service Provider Interface (SPI) for runtime policy and guardrail enforcement.
 */
public interface PolicyProvider {
    /**
     * Evaluates whether an intended action or prompt complies with governance policies.
     *
     * @param intent High-level operation or sanitized category.
     * @param context Contextual metadata attributes.
     * @return [PolicyDecision.Allowed] or [PolicyDecision.Denied].
     */
    public suspend fun evaluate(
        intent: String,
        context: Map<String, String>,
    ): PolicyDecision
}
