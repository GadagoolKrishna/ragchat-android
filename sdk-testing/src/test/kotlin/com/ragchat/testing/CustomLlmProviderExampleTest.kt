package com.ragchat.testing

import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.Locality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verified 20-line custom LlmProvider example featured in docs/api-design.md.
 */
class CustomEchoLlmProvider : LlmProvider {
    override val id: String = "custom-echo-llm"
    override val capabilities: LlmCapabilities = LlmCapabilities(2048, 512, true, false, false, Locality.LOCAL)

    override suspend fun availability(): Availability = Availability.AVAILABLE

    override fun generate(request: LlmRequest): Flow<LlmEvent> =
        flow {
            val prompt =
                request.messages
                    .lastOrNull()
                    ?.content
                    .orEmpty()
            emit(LlmEvent.Token("Echo: $prompt"))
            emit(LlmEvent.Done)
        }

    override suspend fun countTokens(text: String): Int = text.length / 4

    override fun close() {
        // No-op for custom echo provider
    }
}

class CustomLlmProviderExampleTest {
    @Test
    fun testCustomEchoLlmProvider() =
        runTest {
            val provider = CustomEchoLlmProvider()
            assertEquals("custom-echo-llm", provider.id)
            assertEquals(Availability.AVAILABLE, provider.availability())

            val userMessage =
                com.ragchat.api.model.ChatMessage(
                    id = "1",
                    role = com.ragchat.api.model.MessageRole.USER,
                    content = "Testing 123",
                )
            val request = LlmRequest(messages = listOf(userMessage))
            val events = provider.generate(request).toList()

            assertEquals(2, events.size)
            assertTrue(events[0] is LlmEvent.Token)
            assertEquals("Echo: Testing 123", (events[0] as LlmEvent.Token).text)
            assertTrue(events[1] is LlmEvent.Done)

            provider.close()
        }
}
