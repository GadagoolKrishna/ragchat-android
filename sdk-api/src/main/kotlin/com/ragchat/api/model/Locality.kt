package com.ragchat.api.model

/**
 * Execution locality for models, inference, and storage components.
 */
public enum class Locality {
    /**
     * Executes entirely on-device without network transmission.
     */
    LOCAL,

    /**
     * Executes remotely in cloud infrastructure.
     */
    CLOUD,
}
