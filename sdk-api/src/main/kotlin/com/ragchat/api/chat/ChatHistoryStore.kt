package com.ragchat.api.chat

import com.ragchat.api.model.ChatMessage

/**
 * Service Provider Interface (SPI) for persisting and retrieving multi-turn conversation messages.
 */
public interface ChatHistoryStore {
    /**
     * Retrieves recorded chat messages for a specific session.
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     * @return Ordered list of [ChatMessage] items.
     */
    public suspend fun getMessages(
        workspaceId: String,
        sessionId: String,
    ): List<ChatMessage>

    /**
     * Records a new message in the conversation history.
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     * @param message Message to append.
     */
    public suspend fun addMessage(
        workspaceId: String,
        sessionId: String,
        message: ChatMessage,
    )

    /**
     * Overwrites the complete history for a session (e.g. after rolling summarization).
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     * @param messages Replacement message list.
     */
    public suspend fun setMessages(
        workspaceId: String,
        sessionId: String,
        messages: List<ChatMessage>,
    )

    /**
     * Clears all recorded messages for a specific session.
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     */
    public suspend fun clearHistory(
        workspaceId: String,
        sessionId: String,
    )
}
