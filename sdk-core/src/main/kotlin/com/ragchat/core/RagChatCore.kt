package com.ragchat.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * RagChat core engine entry point.
 */
public class RagChatCore {
    private val initialized = AtomicBoolean(true)

    /**
     * Checks if core engine is initialized.
     */
    public fun isInitialized(): Boolean = initialized.get()
}
