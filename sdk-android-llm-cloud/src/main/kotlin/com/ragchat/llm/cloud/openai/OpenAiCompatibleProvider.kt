package com.ragchat.llm.cloud.openai

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.error.SdkError
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.Locality
import com.ragchat.llm.cloud.config.CloudClientConfig
import com.ragchat.llm.cloud.sse.SseStreamReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Universal OpenAI-compatible LLM provider.
 *
 * Connects to any OpenAI-compatible API endpoint including:
 * - OpenAI / Azure OpenAI Service
 * - LiteLLM proxy
 * - vLLM / Ollama server
 * - Custom enterprise API gateways
 *
 * @property modelId Target model identifier (e.g. "gpt-4o", "llama-3.1-70b").
 * @property authProvider Supplies dynamic authorization header.
 * @property config Network configuration including base URL and custom headers.
 * @property chatCompletionsPath URL path for completions (default: "/v1/chat/completions").
 * @property logger Redacted SDK logger.
 */
public class OpenAiCompatibleProvider(
    public val modelId: String = "gpt-4o",
    private val authProvider: AuthProvider,
    private val config: CloudClientConfig = CloudClientConfig(baseUrl = "https://api.openai.com"),
    private val chatCompletionsPath: String = "/v1/chat/completions",
    private val logger: RagChatLogger? = null,
) : LlmProvider {
    override val id: String = "openai-compat-$modelId"

    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 128_000,
            maxOutputTokens = 4096,
            streaming = true,
            toolCalling = true,
            multimodal = true,
            locality = Locality.CLOUD,
            dataResidency = "global",
        )

    private val isClosed = AtomicBoolean(false)
    private val sseReader = SseStreamReader(config, logger)

    override suspend fun availability(): Availability {
        if (isClosed.get()) return Availability.UNAVAILABLE
        val auth = authProvider.getAuthorizationHeader()
        return if (auth != null) Availability.AVAILABLE else Availability.UNAVAILABLE
    }

    override fun generate(request: LlmRequest): Flow<LlmEvent> =
        flow {
            if (isClosed.get()) {
                emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "Provider is closed")))
                return@flow
            }

            val authHeader = authProvider.getAuthorizationHeader()
            if (authHeader == null) {
                emit(LlmEvent.Error(SdkError.AuthenticationError(id, "NO_AUTH_HEADER_SUPPLIED")))
                return@flow
            }

            val jsonBody = buildRequestBody(request)
            val url = "${config.baseUrl.trimEnd('/')}/${chatCompletionsPath.trimStart('/')}"

            val requestBuilder =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Accept", "text/event-stream")
                    .post(jsonBody.toByteArray().toRequestBody("application/json".toMediaType()))

            config.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val flow =
                sseReader.stream(id, requestBuilder.build()) { data ->
                    parseOpenAiChunk(data)
                }

            emitAll(flow)
        }

    private fun buildRequestBody(request: LlmRequest): String {
        val root = JSONObject()
        root.put("model", modelId)
        root.put("stream", true)
        root.put("temperature", request.temperature)
        request.maxTokens?.let { root.put("max_tokens", it) }

        val messagesArray = JSONArray()
        for (msg in request.messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role.name.lowercase())
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }
        root.put("messages", messagesArray)

        if (request.stopSequences.isNotEmpty()) {
            val stopArray = JSONArray()
            request.stopSequences.forEach { stopArray.put(it) }
            root.put("stop", stopArray)
        }

        return root.toString()
    }

    private fun parseOpenAiChunk(jsonText: String): List<LlmEvent> {
        val events = mutableListOf<LlmEvent>()
        val json = JSONObject(jsonText)

        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.getJSONObject(0)
            val delta = choice.optJSONObject("delta")
            val content = delta?.optString("content", "")
            if (!content.isNullOrEmpty()) {
                events.add(LlmEvent.Token(content))
            }

            val finishReason = choice.optString("finish_reason", "")
            if (finishReason.isNotEmpty() && finishReason != "null") {
                val usage = json.optJSONObject("usage")
                val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
                val compTokens = usage?.optInt("completion_tokens", 0) ?: 0
                events.add(
                    LlmEvent.Metadata(
                        promptTokens = promptTokens,
                        candidateTokens = compTokens,
                        finishReason = finishReason,
                    ),
                )
            }
        }

        return events
    }

    override suspend fun countTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.split("\\s+".toRegex()).size * 4) / 3 + 1
    }

    override fun close() {
        isClosed.set(true)
        logger?.log(LogLevel.DEBUG, "CloudLlm", "OpenAiCompatibleProvider closed")
    }
}
