package com.ragchat.api.governance

/**
 * Host-supplied callback interface for prompting users with consent requests.
 */
public interface ConsentChoiceCallback {
    /**
     * Requests user consent for the designated [purpose] and policy [version].
     *
     * @return `true` if user explicitly accepted, `false` otherwise.
     */
    public suspend fun requestConsent(
        purpose: ConsentPurpose,
        version: Int,
    ): Boolean
}
