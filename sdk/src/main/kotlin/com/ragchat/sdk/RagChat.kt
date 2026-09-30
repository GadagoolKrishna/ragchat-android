package com.ragchat.sdk

import com.ragchat.api.RagChatApi

/**
 * Main facade entry point for the RagChat Android SDK.
 */
public object RagChat : RagChatApi {
    override val version: String = "0.1.0"

    /**
     * Initializes the RagChat SDK facade.
     */
    public fun initialize() {
        // Initialization logic
    }
}
