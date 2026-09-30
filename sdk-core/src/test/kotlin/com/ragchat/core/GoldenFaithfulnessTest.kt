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

class GoldenFaithfulnessTest {
    private class GroundedEchoLlm : LlmProvider {
        override val id: String = "grounded-echo-llm"
        override val capabilities: LlmCapabilities =
            LlmCapabilities(
                contextWindow = 4096,
                maxOutputTokens = 512,
                streaming = true,
                toolCalling = false,
                multimodal = false,
                locality = Locality.LOCAL,
            )

        override suspend fun availability(): Availability = Availability.AVAILABLE

        override fun generate(request: LlmRequest): Flow<LlmEvent> =
            flow {
                emit(LlmEvent.Token("The database is configured for AES-256 encryption [chunk_sec_0]."))
                emit(LlmEvent.Done)
            }

        override suspend fun countTokens(text: String): Int = text.length / 4

        override fun close() {
            // No-op for fake test double
        }
    }

    @Test
    fun testFaithfulAnswerProducesGroundedCitations() =
        runBlocking {
            val vectorStore = FakeVectorStore()
            val embeddingProvider = FakeEmbeddingProvider(dimensions = 16)
            val llm = GroundedEchoLlm()

            val queryText = "What encryption does the database use?"
            val vector = embeddingProvider.embed(listOf(queryText)).first()
            vectorStore.upsert(
                "default",
                listOf(
                    Chunk(
                        id = "chunk_sec_0",
                        documentId = "security_spec",
                        content = "The database is configured for AES-256 encryption.",
                        sequenceNumber = 0,
                        tokenCount = 8,
                        metadata = mapOf("page" to "3"),
                    ) to vector,
                ),
            )

            val config =
                RagChatConfigBuilder()
                    .apply {
                        this.localLlmProvider = llm
                        this.vectorStore = vectorStore
                        this.embeddingProvider = embeddingProvider
                    }.build()

            val chatManager = DefaultChatManager(config)

            val events =
                chatManager
                    .ask(
                        query = queryText,
                        options = ChatOptions(confidenceThreshold = 0.5f),
                    ).toList()

            val doneEvent = events.filterIsInstance<ChatEvent.Done>().first()
            assertEquals(1, doneEvent.answer.citations.size)
            val citation = doneEvent.answer.citations.first()
            assertEquals("chunk_sec_0", citation.chunkId)
            assertEquals("security_spec", citation.documentId)
            assertEquals(3, citation.pageNumber)
        }

    @Test
    fun testRefusalWhenNoEvidenceFound() =
        runBlocking {
            val vectorStore = FakeVectorStore()
            val embeddingProvider = FakeEmbeddingProvider(dimensions = 16)
            val llm = GroundedEchoLlm()

            val config =
                RagChatConfigBuilder()
                    .apply {
                        this.localLlmProvider = llm
                        this.vectorStore = vectorStore
                        this.embeddingProvider = embeddingProvider
                    }.build()

            val chatManager = DefaultChatManager(config)

            val events =
                chatManager
                    .ask(
                        query = "Where is the Martian colony located?",
                        options = ChatOptions(confidenceThreshold = 0.5f),
                    ).toList()

            val doneEvent = events.filterIsInstance<ChatEvent.Done>().first()
            assertTrue(doneEvent.answer.text.contains("I do not have enough information"))
            assertTrue(doneEvent.answer.citations.isEmpty())
            assertEquals(0.0f, doneEvent.answer.confidence)
        }
}
