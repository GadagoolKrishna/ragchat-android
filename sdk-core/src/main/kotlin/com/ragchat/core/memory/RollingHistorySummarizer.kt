package com.ragchat.core.memory

import com.ragchat.api.chat.ChatHistoryStore
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.MessageRole
import kotlinx.coroutines.flow.filterIsInstance
import java.util.UUID

/**
 * Manages rolling multi-turn memory summarization when conversation length approaches model context thresholds.
 */
public class RollingHistorySummarizer(
    public val maxHistoryTokens: Int = 1500,
    public val recentTurnsToKeep: Int = 4,
) {
    /**
     * Checks history size and condenses older turns if token threshold is exceeded.
     */
    public suspend fun condenseIfNeeded(
        workspaceId: String,
        sessionId: String,
        historyStore: ChatHistoryStore,
        llmProvider: LlmProvider,
    ): List<ChatMessage> {
        val messages = historyStore.getMessages(workspaceId, sessionId)
        val shouldCondense =
            messages.size > recentTurnsToKeep + 1 &&
                messages.sumOf { llmProvider.countTokens(it.content) } > maxHistoryTokens

        return if (shouldCondense) {
            condense(workspaceId, sessionId, messages, historyStore, llmProvider)
        } else {
            messages
        }
    }

    private suspend fun condense(
        workspaceId: String,
        sessionId: String,
        messages: List<ChatMessage>,
        historyStore: ChatHistoryStore,
        llmProvider: LlmProvider,
    ): List<ChatMessage> {
        val splitIndex = messages.size - recentTurnsToKeep
        val toSummarize = messages.subList(0, splitIndex)
        val toKeep = messages.subList(splitIndex, messages.size)

        val summaryText = generateSummary(toSummarize, llmProvider)
        val condensed = mutableListOf<ChatMessage>()
        condensed.add(
            ChatMessage(
                id = "summary-" + UUID.randomUUID().toString().take(8),
                role = MessageRole.SYSTEM,
                content = "PRIOR CONVERSATION SUMMARY:\n$summaryText",
            ),
        )
        condensed.addAll(toKeep)

        historyStore.setMessages(workspaceId, sessionId, condensed)
        return condensed
    }

    private suspend fun generateSummary(
        messages: List<ChatMessage>,
        llmProvider: LlmProvider,
    ): String {
        val transcript =
            buildString {
                for (msg in messages) {
                    append("${msg.role.name}: ${msg.content}\n")
                }
            }

        val prompt = "Summarize the key facts and decisions concisely in 2-3 sentences:\n\n$transcript"
        val request =
            LlmRequest(
                messages = listOf(ChatMessage(id = "summary-prompt", role = MessageRole.USER, content = prompt)),
                maxTokens = 256,
                temperature = 0.2f,
            )

        val summaryTokens = StringBuilder()
        llmProvider
            .generate(request)
            .filterIsInstance<LlmEvent.Token>()
            .collect { event -> summaryTokens.append(event.text) }

        val res = summaryTokens.toString().trim()
        return if (res.isNotEmpty()) res else "Prior dialogue discussed document contents."
    }
}
