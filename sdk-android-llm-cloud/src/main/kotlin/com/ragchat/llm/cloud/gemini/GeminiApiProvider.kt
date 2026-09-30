package com.ragchat.llm.cloud.gemini

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
 * Cloud LLM provider connecting to Google Generative Language API (Gemini Developer API).
 *
 * Auth headers are acquired per-request via [AuthProvider].
 */
public class GeminiApiProvider(
    public val modelId: String = "gemini-1.5-flash",
    private val authProvider: AuthProvider,
    private val config: CloudClientConfig = CloudClientConfig(baseUrl = "https://generativelanguage.googleapis.com"),
    private val logger: RagChatLogger? = null,
) : LlmProvider {
    override val id: String = "gemini-cloud-$modelId"

    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 1_000_000,
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
            val url = "${config.baseUrl.trimEnd('/')}/v1beta/models/$modelId:streamGenerateContent?alt=sse"

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
                    parseGeminiChunk(data)
                }

            emitAll(flow)
        }

    private fun buildRequestBody(request: LlmRequest): String {
        val root = JSONObject()
        val contentsArray = JSONArray()

        for (msg in request.messages) {
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.role.name == "USER") "user" else "model")
            val partsArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", msg.content)
            partsArray.put(textPart)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        root.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("temperature", request.temperature)
        request.maxTokens?.let { genConfig.put("maxOutputTokens", it) }
        if (request.stopSequences.isNotEmpty()) {
            val stopArray = JSONArray()
            request.stopSequences.forEach { stopArray.put(it) }
            genConfig.put("stopSequences", stopArray)
        }
        root.put("generationConfig", genConfig)

        return root.toString()
    }

    private fun parseGeminiChunk(jsonText: String): List<LlmEvent> {
        val events = mutableListOf<LlmEvent>()
        val json = JSONObject(jsonText)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val candidate = candidates.getJSONObject(0)
            extractTextTokens(candidate, events)
            extractMetadata(candidate, json, events)
        }
        return events
    }

    private fun extractTextTokens(
        candidate: JSONObject,
        events: MutableList<LlmEvent>,
    ) {
        val content = candidate.optJSONObject("content") ?: return
        val parts = content.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val text = part.optString("text", "")
            if (text.isNotEmpty()) {
                events.add(LlmEvent.Token(text))
            }
        }
    }

    private fun extractMetadata(
        candidate: JSONObject,
        root: JSONObject,
        events: MutableList<LlmEvent>,
    ) {
        val finishReason = candidate.optString("finishReason", "")
        if (finishReason.isEmpty()) return

        val usage = root.optJSONObject("usageMetadata")
        val promptTokens = usage?.optInt("promptTokenCount", 0) ?: 0
        val candidateTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0
        events.add(
            LlmEvent.Metadata(
                promptTokens = promptTokens,
                candidateTokens = candidateTokens,
                finishReason = finishReason,
            ),
        )
    }

    override suspend fun countTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.split("\\s+".toRegex()).size * 4) / 3 + 1
    }

    override fun close() {
        isClosed.set(true)
        logger?.log(LogLevel.DEBUG, "CloudLlm", "GeminiApiProvider closed")
    }
}
