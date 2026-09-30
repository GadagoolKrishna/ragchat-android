package com.ragchat.api.chat

import com.ragchat.api.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Public conversational interface for querying RAG pipelines.
 */
public interface ChatManager {
    /**
     * Submits a conversational query and streams the generation process.
     *
     * @param query User prompt or question.
     * @param options Execution settings, session IDs, and retrieval parameters.
     * @return Cold [Flow] emitting [ChatEvent] lifecycle items.
     */
    public fun ask(
        query: String,
        options: ChatOptions = ChatOptions(),
    ): Flow<ChatEvent>

    /**
     * Retrieves conversational history for a workspace and session.
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     * @return List of prior [ChatMessage] entries.
     */
    public suspend fun getHistory(
        workspaceId: String,
        sessionId: String,
    ): List<ChatMessage>

    /**
     * Clears conversational history for a workspace and session.
     *
     * @param workspaceId Logical workspace scope.
     * @param sessionId Session identifier.
     */
    public suspend fun clearHistory(
        workspaceId: String,
        sessionId: String,
    )
}
