package com.ragchat.api.config

/**
 * Strategy determining how LLM inference requests are routed between local on-device models and cloud endpoints.
 */
public enum class ModelRoutingMode {
    /**
     * Executes exclusively on-device. Fails if local model is unavailable or unsupported.
     */
    LOCAL_ONLY,

    /**
     * Executes exclusively via remote cloud API. Fails if offline or unauthenticated.
     */
    CLOUD_ONLY,

    /**
     * Attempts on-device inference first; falls back to cloud if local model is unavailable or capacity is exceeded.
     */
    LOCAL_FIRST,

    /**
     * Attempts cloud inference first; falls back to on-device model when offline or cloud error occurs.
     */
    CLOUD_FIRST,

    /**
     * Automatically selects execution locality based on device thermal status, battery, prompt complexity, and latency.
     */
    AUTO,
}
