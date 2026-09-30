package com.ragchat.testing

import com.ragchat.api.chat.ChatHistoryStore
import com.ragchat.api.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe test double for [ChatHistoryStore].
 */
public class FakeChatHistoryStore : ChatHistoryStore {
    private val storage = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private fun key(
        workspaceId: String,
        sessionId: String,
    ): String = "$workspaceId::$sessionId"

    override suspend fun getMessages(
        workspaceId: String,
        sessionId: String,
    ): List<ChatMessage> {
        val list = storage[key(workspaceId, sessionId)] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    override suspend fun addMessage(
        workspaceId: String,
        sessionId: String,
        message: ChatMessage,
    ) {
        val list = storage.computeIfAbsent(key(workspaceId, sessionId)) { mutableListOf() }
        synchronized(list) {
            list.add(message)
        }
    }

    override suspend fun setMessages(
        workspaceId: String,
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        val list = storage.computeIfAbsent(key(workspaceId, sessionId)) { mutableListOf() }
        synchronized(list) {
            list.clear()
            list.addAll(messages)
        }
    }

    override suspend fun clearHistory(
        workspaceId: String,
        sessionId: String,
    ) {
        storage.remove(key(workspaceId, sessionId))
    }
}
