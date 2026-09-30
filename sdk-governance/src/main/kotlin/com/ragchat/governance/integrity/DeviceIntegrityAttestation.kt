package com.ragchat.governance.integrity

/**
 * Result of device integrity evaluation (Play Integrity / Root detection).
 *
 * @property isDeviceRecognized True if the device satisfies basic hardware-backed integrity.
 * @property isAppLicensed True if the app was installed via official Google Play distribution.
 * @property isRooted True if su binary, test-keys, or rooting indicators are detected.
 * @property verdictSummary Human-readable diagnostic summary (sanitized, zero-PII).
 */
public data class DeviceIntegrityVerdict(
    public val isDeviceRecognized: Boolean,
    public val isAppLicensed: Boolean,
    public val isRooted: Boolean,
    public val verdictSummary: String,
)

/**
 * Interface hook for providing hardware and platform attestation to governance policies.
 */
public interface DeviceIntegrityProvider {
    /**
     * Obtains the current device attestation verdict.
     */
    public suspend fun fetchVerdict(): DeviceIntegrityVerdict
}
