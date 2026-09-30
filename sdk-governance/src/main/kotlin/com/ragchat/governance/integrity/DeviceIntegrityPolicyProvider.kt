package com.ragchat.governance.integrity

import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider

/**
 * PolicyProvider decorator enforcing Play Integrity and root detection guardrails.
 *
 * Blocks sensitive on-device indexing, crypto operations, or cloud offload if device integrity fails.
 *
 * @param delegate Underlying [PolicyProvider] to consult if device integrity passes.
 * @param integrityProvider Source of Play Integrity and root status attestations.
 * @param blockOnRooted Whether to deny all RAG operations if root indicators are detected.
 * @param requireDeviceRecognition Whether to require Google Play basic device recognition.
 */
public class DeviceIntegrityPolicyProvider(
    private val delegate: PolicyProvider,
    private val integrityProvider: DeviceIntegrityProvider,
    private val blockOnRooted: Boolean = true,
    private val requireDeviceRecognition: Boolean = true,
) : PolicyProvider {
    override suspend fun evaluate(
        intent: String,
        context: Map<String, String>,
    ): PolicyDecision {
        val verdict = integrityProvider.fetchVerdict()

        val denialDecision =
            when {
                blockOnRooted && verdict.isRooted ->
                    PolicyDecision.Denied(
                        ruleId = "DEVICE_INTEGRITY_ROOT_DETECTED",
                        category = "DEVICE_COMPROMISE",
                    )
                requireDeviceRecognition && !verdict.isDeviceRecognized ->
                    PolicyDecision.Denied(
                        ruleId = "DEVICE_INTEGRITY_PLAY_ATTESTATION_FAILED",
                        category = "HARDWARE_ATTESTATION_FAILURE",
                    )
                else -> null
            }

        return denialDecision ?: delegate.evaluate(intent, context)
    }
}
