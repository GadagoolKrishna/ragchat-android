package com.ragchat.api.llm

/**
 * Operational availability status for an LLM provider.
 */
public enum class Availability {
    /**
     * Provider is fully initialized, loaded, and ready for inference.
     */
    AVAILABLE,

    /**
     * Provider model weights or assets must be downloaded before use.
     */
    DOWNLOAD_REQUIRED,

    /**
     * Target hardware acceleration (NPU/GPU/RAM) is insufficient for this provider.
     */
    HARDWARE_UNSUPPORTED,

    /**
     * Provider is temporarily or permanently unavailable.
     */
    UNAVAILABLE,
}
