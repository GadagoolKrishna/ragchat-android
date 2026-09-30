package com.ragchat.api

import com.ragchat.api.config.RagChatConfig
import com.ragchat.api.config.RagChatConfigBuilder

/**
 * Primary entry point for constructing and configuring the RagChat SDK.
 */
public object RagChat {
    /**
     * Constructs a validated [RagChatConfig] using a Kotlin DSL block.
     *
     * @param block Configuration lambda executed in the context of [RagChatConfigBuilder].
     * @return Configured [RagChatConfig] instance.
     */
    public fun builder(block: RagChatConfigBuilder.() -> Unit): RagChatConfig = RagChatConfigBuilder().apply(block).build()
}
