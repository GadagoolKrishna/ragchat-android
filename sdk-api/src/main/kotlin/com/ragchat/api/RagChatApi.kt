package com.ragchat.api

import com.ragchat.api.chat.ChatManager
import com.ragchat.api.document.DocumentManager

/**
 * Marker interface for the RagChat SDK API.
 */
public interface RagChatApi {
    /**
     * The version string of the SDK.
     */
    public val version: String

    /**
     * Document management and ingestion engine.
     */
    public val documents: DocumentManager

    /**
     * Conversational chat and retrieval-augmented generation engine.
     */
    public val chat: ChatManager
}
