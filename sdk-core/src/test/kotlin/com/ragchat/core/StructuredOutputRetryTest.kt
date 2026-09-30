package com.ragchat.core

import com.ragchat.api.chat.ChatEvent
import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.config.RagChatConfigBuilder
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.Locality
import com.ragchat.core.chat.DefaultChatManager
import com.ragchat.testing.FakeEmbeddingProvider
import com.ragchat.testing.FakeVectorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredOutputRetryTest {
    private class MalformedThenValidLlmProvider : LlmProvider {
        override val id: String = "test-structured-llm"
        override val capabilities: LlmCapabilities =
            LlmCapabilities(
                contextWindow = 4096,
                maxOutputTokens = 1024,
                streaming = true,
                toolCalling = false,
                multimodal = false,
                locality = Locality.LOCAL,
            )
        private var attemptCount = 0

        override suspend fun availability(): Availability = Availability.AVAILABLE

        override fun generate(request: LlmRequest): Flow<LlmEvent> =
            flow {
                attemptCount++
                if (attemptCount == 1) {
                    emit(LlmEvent.Token("{\"title\": \"Report\""))
                    emit(LlmEvent.Done)
                } else {
                    emit(LlmEvent.Token("{\"title\": \"Report\", \"summary\": \"Valid summary [chunk_0].\"}"))
                    emit(LlmEvent.Done)
                }
            }

        override suspend fun countTokens(text: String): Int = text.length / 4

        override fun close() {
            // No-op for test double
        }
    }

    @Test
    fun testRetriesUntilJsonSchemaIsValid() =
        runBlocking {
            val vectorStore = FakeVectorStore()
            val embeddingProvider = FakeEmbeddingProvider(dimensions = 16)
            val llmProvider = MalformedThenValidLlmProvider()

            val queryText = "Summarize the report"
            val vector = embeddingProvider.embed(listOf(queryText)).first()
            vectorStore.upsert(
                "default",
                listOf(
                    Chunk(
                        id = "chunk_0",
                        documentId = "doc_1",
                        content = "Annual financial report summary.",
                        sequenceNumber = 0,
                        tokenCount = 10,
                    ) to vector,
                ),
            )

            val config =
                RagChatConfigBuilder()
                    .apply {
                        this.localLlmProvider = llmProvider
                        this.vectorStore = vectorStore
                        this.embeddingProvider = embeddingProvider
                    }.build()

            val chatManager = DefaultChatManager(config)

            val schema =
                """
                {
                   "type": "object",
                   "required": ["title", "summary"]
                }
                """.trimIndent()

            val events =
                chatManager
                    .ask(
                        query = queryText,
                        options = ChatOptions(jsonSchema = schema, maxStructuredRetries = 2, confidenceThreshold = 0.5f),
                    ).toList()

            val doneEvent = events.filterIsInstance<ChatEvent.Done>().firstOrNull()
            checkNotNull(doneEvent) { "Expected Done event" }

            assertTrue(doneEvent.answer.text.contains("\"title\": \"Report\""))
            assertTrue(doneEvent.answer.text.contains("\"summary\""))
            assertEquals(1, doneEvent.answer.citations.size)
            assertEquals(
                "chunk_0",
                doneEvent.answer.citations
                    .first()
                    .chunkId,
            )
        }
}
