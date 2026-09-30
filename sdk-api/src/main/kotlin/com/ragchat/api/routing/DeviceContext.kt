package com.ragchat.api.routing

/**
 * Classification of device hardware compute capability.
 */
public enum class DeviceTier {
    /**
     * Entry-level devices with constrained CPU/NPU and memory (< 4GB RAM).
     */
    LOW_END,

    /**
     * Mainstream devices capable of mid-sized quantized models (4GB - 8GB RAM).
     */
    MID_RANGE,

    /**
     * Premium devices with dedicated NPU acceleration and ample RAM (>= 8GB RAM).
     */
    HIGH_END,

    /**
     * Unknown or unprofiled hardware tier.
     */
    UNKNOWN,
}

/**
 * Active network connectivity type.
 */
public enum class NetworkType {
    /**
     * High-bandwidth, unmetered Wi-Fi connection.
     */
    WIFI,

    /**
     * Cellular data connection (may be metered or subject to carrier limits).
     */
    CELLULAR,

    /**
     * Wired Ethernet connection.
     */
    ETHERNET,

    /**
     * No active network connection available.
     */
    NONE,
}

/**
 * Device battery state metrics.
 *
 * @property levelPercent Current battery percentage (0 to 100).
 * @property isCharging Whether the device is actively connected to power.
 */
public data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
)

/**
 * Hardware thermal status levels.
 */
public enum class ThermalLevel {
    /**
     * Normal operational temperature with no throttling.
     */
    NORMAL,

    /**
     * Moderate thermal elevation; slight throttling may be advised.
     */
    MODERATE,

    /**
     * Severe thermal stress; intense computations should be throttled or deferred.
     */
    SEVERE,

    /**
     * Critical thermal ceiling reached; device shutdown or strict idle mandated.
     */
    CRITICAL,
}

/**
 * Comprehensive snapshot of runtime device and network conditions.
 *
 * @property deviceTier Profiled hardware capability tier.
 * @property battery Battery status.
 * @property thermal Thermal throttling level.
 * @property networkType Active network transport.
 * @property isRoaming Whether cellular network is currently in a roaming state.
 */
public data class DeviceContext(
    val deviceTier: DeviceTier = DeviceTier.UNKNOWN,
    val battery: BatteryStatus = BatteryStatus(levelPercent = 100, isCharging = true),
    val thermal: ThermalLevel = ThermalLevel.NORMAL,
    val networkType: NetworkType = NetworkType.WIFI,
    val isRoaming: Boolean = false,
)
