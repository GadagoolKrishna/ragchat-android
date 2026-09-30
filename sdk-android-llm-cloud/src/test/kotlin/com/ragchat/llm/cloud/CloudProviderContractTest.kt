package com.ragchat.llm.cloud

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.error.SdkError
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.MessageRole
import com.ragchat.llm.cloud.anthropic.AnthropicProvider
import com.ragchat.llm.cloud.config.CloudClientConfig
import com.ragchat.llm.cloud.gemini.GeminiApiProvider
import com.ragchat.llm.cloud.openai.OpenAiCompatibleProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudProviderContractTest {
    private lateinit var server: MockWebServer

    private val fakeAuthProvider =
        object : AuthProvider {
            override suspend fun getAuthorizationHeader(): String = "Bearer test-jwt-token-xyz"
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testGeminiApiProviderContract() =
        runTest {
            val sseBody =
                """
                data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}

                data: {"candidates":[{"content":{"parts":[{"text":" world"}]},"finishReason":"STOP","usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2}}]}

                data: [DONE]

                """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setResponseCode(200),
            )

            val config = CloudClientConfig(baseUrl = server.url("/").toString())
            val provider =
                GeminiApiProvider(
                    modelId = "gemini-1.5-flash",
                    authProvider = fakeAuthProvider,
                    config = config,
                )

            val request =
                LlmRequest(
                    messages = listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "Hi")),
                )

            val events = provider.generate(request).toList()
            val tokens = events.filterIsInstance<LlmEvent.Token>().map { it.text }
            assertEquals(listOf("Hello", " world"), tokens)
            assertTrue(events.any { it is LlmEvent.Metadata })
            assertTrue(events.any { it is LlmEvent.Done })

            val recorded = server.takeRequest()
            assertEquals("Bearer test-jwt-token-xyz", recorded.getHeader("Authorization"))
            assertTrue(recorded.path?.contains("streamGenerateContent") == true)
        }

    @Test
    fun testOpenAiCompatibleProviderContract() =
        runTest {
            val sseBody =
                """
                data: {"choices":[{"delta":{"content":"Hi"}}]}

                data: {"choices":[{"delta":{"content":" there"}}],"usage":{"prompt_tokens":3,"completion_tokens":2}}

                data: [DONE]

                """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setResponseCode(200),
            )

            val config = CloudClientConfig(baseUrl = server.url("/").toString())
            val provider =
                OpenAiCompatibleProvider(
                    modelId = "gpt-4o",
                    authProvider = fakeAuthProvider,
                    config = config,
                )

            val request =
                LlmRequest(
                    messages = listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "Hi")),
                )

            val events = provider.generate(request).toList()
            val tokens = events.filterIsInstance<LlmEvent.Token>().map { it.text }
            assertEquals(listOf("Hi", " there"), tokens)
            assertTrue(events.any { it is LlmEvent.Done })
        }

    @Test
    fun testAnthropicProviderContract() =
        runTest {
            val sseBody =
                """
                data: {"type":"content_block_delta","delta":{"text":"Claude"}}

                data: {"type":"content_block_delta","delta":{"text":" online"}}

                data: {"type":"message_stop"}

                """.trimIndent()

            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setResponseCode(200),
            )

            val config = CloudClientConfig(baseUrl = server.url("/").toString())
            val provider =
                AnthropicProvider(
                    modelId = "claude-3-5-sonnet",
                    authProvider = fakeAuthProvider,
                    config = config,
                )

            val request =
                LlmRequest(
                    messages = listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "Hi")),
                )

            val events = provider.generate(request).toList()
            val tokens = events.filterIsInstance<LlmEvent.Token>().map { it.text }
            assertEquals(listOf("Claude", " online"), tokens)
            assertTrue(events.any { it is LlmEvent.Done })
        }

    @Test
    fun testHttp401MapsToAuthenticationError() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorized"),
            )

            val config = CloudClientConfig(baseUrl = server.url("/").toString())
            val provider =
                OpenAiCompatibleProvider(
                    modelId = "gpt-4o",
                    authProvider = fakeAuthProvider,
                    config = config,
                )

            val request = LlmRequest(messages = emptyList())
            val events = provider.generate(request).toList()
            val errorEvent = events.filterIsInstance<LlmEvent.Error>().firstOrNull()
            assertTrue("Error must be SdkError.AuthenticationError", errorEvent?.error is SdkError.AuthenticationError)
        }
}
