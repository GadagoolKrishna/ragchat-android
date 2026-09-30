package com.ragchat.core.memory

import com.ragchat.api.chat.ChatHistoryStore
import com.ragchat.api.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, in-memory implementation of [ChatHistoryStore] providing session and workspace scoping.
 */
public class InMemoryChatHistoryStore : ChatHistoryStore {
    private val sessions = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private fun sessionKey(
        workspaceId: String,
        sessionId: String,
    ): String = "$workspaceId::$sessionId"

    override suspend fun getMessages(
        workspaceId: String,
        sessionId: String,
    ): List<ChatMessage> {
        val list = sessions[sessionKey(workspaceId, sessionId)] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    override suspend fun addMessage(
        workspaceId: String,
        sessionId: String,
        message: ChatMessage,
    ) {
        val list = sessions.computeIfAbsent(sessionKey(workspaceId, sessionId)) { mutableListOf() }
        synchronized(list) {
            list.add(message)
        }
    }

    override suspend fun setMessages(
        workspaceId: String,
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        val list = sessions.computeIfAbsent(sessionKey(workspaceId, sessionId)) { mutableListOf() }
        synchronized(list) {
            list.clear()
            list.addAll(messages)
        }
    }

    override suspend fun clearHistory(
        workspaceId: String,
        sessionId: String,
    ) {
        sessions.remove(sessionKey(workspaceId, sessionId))
    }
}
