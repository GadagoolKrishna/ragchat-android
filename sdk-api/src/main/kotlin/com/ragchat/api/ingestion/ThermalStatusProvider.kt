package com.ragchat.api.ingestion

/**
 * Normalized thermal throttling states of the host device.
 */
public enum class ThermalState {
    /**
     * Normal thermal operation with no throttling.
     */
    NORMAL,

    /**
     * Light to moderate thermal load.
     */
    MODERATE,

    /**
     * Severe thermal throttling active. Background inference should be throttled.
     */
    THROTTLED,

    /**
     * Critical thermal emergency. All background inference should immediately pause.
     */
    CRITICAL,
}

/**
 * Service Provider Interface (SPI) for querying host device thermal state.
 *
 * Implemented in Android platform modules (e.g. `:sdk-android-work`) to bridge `PowerManager`.
 */
public interface ThermalStatusProvider {
    /**
     * Returns the current thermal status of the device.
     */
    public fun getCurrentThermalState(): ThermalState
}
