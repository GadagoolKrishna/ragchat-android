package com.ragchat.core

import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.MessageRole
import com.ragchat.core.chat.DefaultChatManager
import com.ragchat.core.memory.InMemoryChatHistoryStore
import com.ragchat.core.memory.RollingHistorySummarizer
import com.ragchat.testing.FakeEmbeddingProvider
import com.ragchat.testing.FakeLlmProvider
import com.ragchat.testing.FakeVectorStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiTurnMemoryTest {
    @Test
    fun testMultiTurnHistoryIsolationAndClear() =
        runBlocking {
            val vectorStore = FakeVectorStore()
            val embeddingProvider = FakeEmbeddingProvider(dimensions = 16)
            val llm = FakeLlmProvider()

            val vector = embeddingProvider.embed(listOf("test query")).first()
            vectorStore.upsert(
                "default",
                listOf(
                    Chunk("chunk_1", "doc_1", "Server is deployed on port 8080.", 0, 10) to vector,
                ),
            )

            val historyStore = InMemoryChatHistoryStore()
            val config =
                RagChatConfigBuilder()
                    .apply {
                        this.localLlmProvider = llm
                        this.vectorStore = vectorStore
                        this.embeddingProvider = embeddingProvider
                        this.chatHistoryStore = historyStore
                    }.build()

            val chatManager = DefaultChatManager(config, historyStore)

            // Turn 1 in session A
            val sessionA = ChatOptions(workspaceId = "ws1", sessionId = "sessA")
            chatManager.ask("What is the port?", sessionA).toList()

            val historyA = chatManager.getHistory("ws1", "sessA")
            assertEquals(2, historyA.size)
            assertEquals(MessageRole.USER, historyA[0].role)
            assertEquals(MessageRole.ASSISTANT, historyA[1].role)

            // Session B should have 0 messages
            val historyB = chatManager.getHistory("ws1", "sessB")
            assertEquals(0, historyB.size)

            // Clear history in session A
            chatManager.clearHistory("ws1", "sessA")
            val clearedA = chatManager.getHistory("ws1", "sessA")
            assertEquals(0, clearedA.size)
        }

    @Test
    fun testRollingSummarizationCondensesHistory() =
        runBlocking {
            val historyStore = InMemoryChatHistoryStore()
            val llm = FakeLlmProvider()
            val summarizer = RollingHistorySummarizer(maxHistoryTokens = 30, recentTurnsToKeep = 2)

            // Seed 6 messages that exceed token limit
            for (i in 1..6) {
                historyStore.addMessage(
                    "ws1",
                    "sess1",
                    com.ragchat.api.model.ChatMessage(
                        id = "msg-$i",
                        role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                        content = "Detailed lengthy conversational turn number $i with many words to exceed token budget.",
                    ),
                )
            }

            val condensed = summarizer.condenseIfNeeded("ws1", "sess1", historyStore, llm)

            // Should have 1 summary system message + 2 recent kept messages = 3 messages total
            assertEquals(3, condensed.size)
            assertEquals(MessageRole.SYSTEM, condensed[0].role)
            assertTrue(condensed[0].content.contains("PRIOR CONVERSATION SUMMARY"))
            assertEquals("msg-5", condensed[1].id)
            assertEquals("msg-6", condensed[2].id)
        }
}
