package com.ragchat.llm.cloud.anthropic

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
 * Cloud LLM provider connecting to Anthropic Claude Messages API (`/v1/messages`).
 *
 * @property modelId Claude model (e.g. "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307").
 * @property authProvider Dynamic provider of Anthropic API key / bearer token.
 * @property config Network configuration including base URL and custom headers.
 * @property anthropicVersion Anthropic API protocol version header.
 * @property logger Redacted SDK logger.
 */
public class AnthropicProvider(
    public val modelId: String = "claude-3-5-sonnet-20241022",
    private val authProvider: AuthProvider,
    private val config: CloudClientConfig = CloudClientConfig(baseUrl = "https://api.anthropic.com"),
    private val anthropicVersion: String = "2023-06-01",
    private val logger: RagChatLogger? = null,
) : LlmProvider {
    override val id: String = "anthropic-$modelId"

    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 200_000,
            maxOutputTokens = 8192,
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
            val url = "${config.baseUrl.trimEnd('/')}/v1/messages"

            val requestBuilder =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("anthropic-version", anthropicVersion)
                    .addHeader("Accept", "text/event-stream")
                    .post(jsonBody.toByteArray().toRequestBody("application/json".toMediaType()))

            // Anthropic supports x-api-key or Authorization Bearer
            if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                requestBuilder.addHeader("Authorization", authHeader)
            } else {
                requestBuilder.addHeader("x-api-key", authHeader)
            }

            config.customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val flow =
                sseReader.stream(id, requestBuilder.build()) { data ->
                    parseAnthropicChunk(data)
                }

            emitAll(flow)
        }

    private fun buildRequestBody(request: LlmRequest): String {
        val root = JSONObject()
        root.put("model", modelId)
        root.put("stream", true)
        root.put("max_tokens", request.maxTokens ?: 1024)
        root.put("temperature", request.temperature)

        val messagesArray = JSONArray()
        var systemInstruction: String? = null

        for (msg in request.messages) {
            if (msg.role.name == "SYSTEM") {
                systemInstruction = msg.content
            } else {
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.role.name == "USER") "user" else "assistant")
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
            }
        }

        systemInstruction?.let { root.put("system", it) }
        root.put("messages", messagesArray)

        if (request.stopSequences.isNotEmpty()) {
            val stopArray = JSONArray()
            request.stopSequences.forEach { stopArray.put(it) }
            root.put("stop_sequences", stopArray)
        }

        return root.toString()
    }

    private fun parseAnthropicChunk(jsonText: String): List<LlmEvent> {
        val events = mutableListOf<LlmEvent>()
        val json = JSONObject(jsonText)
        val type = json.optString("type")

        when (type) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                val text = delta?.optString("text")
                if (!text.isNullOrEmpty()) {
                    events.add(LlmEvent.Token(text))
                }
            }
            "message_delta" -> {
                val delta = json.optJSONObject("delta")
                val stopReason = delta?.optString("stop_reason")
                val usage = json.optJSONObject("usage")
                val outputTokens = usage?.optInt("output_tokens", 0) ?: 0
                events.add(
                    LlmEvent.Metadata(
                        promptTokens = 0,
                        candidateTokens = outputTokens,
                        finishReason = stopReason,
                    ),
                )
            }
            "message_stop" -> {
                events.add(LlmEvent.Done)
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
        logger?.log(LogLevel.DEBUG, "CloudLlm", "AnthropicProvider closed")
    }
}
