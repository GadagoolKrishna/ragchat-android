package com.ragchat.embeddings.cloud

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

/**
 * Cloud embedding provider supporting Gemini Developer API and Google Cloud Vertex AI embeddings.
 *
 * Adheres strictly to enterprise governance rules:
 * - Queries [PolicyProvider] before sending any network traffic; blocks request if intent or data
 *   residency is marked `LOCAL_ONLY`.
 * - Acquires authentication credentials dynamically via [AuthProvider] without hardcoding API keys.
 * - Supports task-type guiding and Matryoshka dimension truncation.
 *
 * @property endpoint Cloud REST endpoint URL.
 * @property authProvider Provider for dynamic Bearer authorization headers.
 * @property policyProvider Optional governance policy engine.
 * @property modelId Model identifier (e.g. "text-embedding-004", "gemini-embedding-001").
 * @property version Model version.
 * @property dimensions Target vector dimensions.
 * @property maxInputTokens Maximum token length.
 * @property normalize Whether to apply L2 normalization.
 * @property tenantId Scope identifier for policy governance evaluation.
 * @property connectTimeoutMs Network connection timeout.
 * @property readTimeoutMs Network read timeout.
 * @property logger SDK logger.
 */
public class CloudEmbeddingProvider(
    private val endpoint: String,
    private val authProvider: AuthProvider,
    private val policyProvider: PolicyProvider? = null,
    override val modelId: String = "text-embedding-004",
    override val version: String = "1.0.0",
    override val dimensions: Int = 768,
    override val maxInputTokens: Int = 2048,
    override val normalize: Boolean = true,
    private val tenantId: String = "default",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val logger: RagChatLogger? = null,
) : EmbeddingProvider {
    override suspend fun embed(
        texts: List<String>,
        taskType: EmbeddingTaskType,
    ): List<FloatArray> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()

            // 1. Governance Policy Evaluation
            evaluateGovernancePolicy(texts.size)

            // 2. Resolve Auth Token
            val authHeader =
                authProvider.getAuthorizationHeader()
                    ?: throw SdkError.AuthenticationError(providerId = "cloud", errorCode = "NO_AUTH_HEADER_SUPPLIED")

            // 3. Dispatch batch to Cloud Endpoint
            val vectors = ArrayList<FloatArray>(texts.size)
            for (batch in texts.chunked(16)) {
                val batchResult = executeHttpEmbeddingRequest(batch, taskType, authHeader)
                vectors.addAll(batchResult)
            }

            vectors
        }

    private suspend fun evaluateGovernancePolicy(itemCount: Int) {
        if (policyProvider != null) {
            val decision =
                policyProvider.evaluate(
                    intent = "CLOUD_EMBEDDING_REQUEST",
                    context =
                        mapOf(
                            "tenant_id" to tenantId,
                            "model_id" to modelId,
                            "item_count" to itemCount.toString(),
                        ),
                )

            if (decision is PolicyDecision.Denied) {
                logger?.log(LogLevel.WARN, "CloudEmbeddingProvider", "Cloud embedding blocked by policy: ${decision.category}")
                throw SdkError.PolicyViolationError(
                    ruleId = decision.ruleId,
                    category = decision.category,
                )
            }
        }
    }

    private fun executeHttpEmbeddingRequest(
        texts: List<String>,
        taskType: EmbeddingTaskType,
        authHeader: String,
    ): List<FloatArray> {
        val url = URL(endpoint)
        val conn =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", authHeader)
            }

        val requestPayload = buildRequestJson(texts, taskType)

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestPayload)
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            logger?.log(LogLevel.WARN, "CloudEmbeddingProvider", "Cloud embedding HTTP failed ($responseCode): $errorText")
            throw SdkError.EmbeddingError(
                modelId = modelId,
                errorCode = "HTTP_ERROR_$responseCode",
            )
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        return parseResponseJson(responseBody, texts.size)
    }

    private fun buildRequestJson(
        texts: List<String>,
        taskType: EmbeddingTaskType,
    ): String {
        val mappedTask =
            when (taskType) {
                EmbeddingTaskType.RETRIEVAL_QUERY -> "RETRIEVAL_QUERY"
                EmbeddingTaskType.RETRIEVAL_DOCUMENT -> "RETRIEVAL_DOCUMENT"
                EmbeddingTaskType.SEMANTIC_SIMILARITY -> "SEMANTIC_SIMILARITY"
                EmbeddingTaskType.CLASSIFICATION -> "CLASSIFICATION"
            }

        val root = JSONObject()
        val instances = JSONArray()
        for (text in texts) {
            val instance =
                JSONObject().apply {
                    put("content", text)
                    put("task_type", mappedTask)
                }
            instances.put(instance)
        }
        root.put("instances", instances)

        val parameters =
            JSONObject().apply {
                put("autoTruncate", true)
                put("outputDimensionality", dimensions)
            }
        root.put("parameters", parameters)

        return root.toString()
    }

    private fun parseResponseJson(
        jsonString: String,
        expectedSize: Int,
    ): List<FloatArray> {
        val root = JSONObject(jsonString)
        val results = ArrayList<FloatArray>(expectedSize)

        if (root.has("predictions")) {
            val predictions = root.getJSONArray("predictions")
            for (i in 0 until predictions.length()) {
                val pred = predictions.getJSONObject(i)
                val embeddingsObj = pred.getJSONObject("embeddings")
                val valuesArray = embeddingsObj.getJSONArray("values")
                val vector = FloatArray(valuesArray.length())
                for (j in 0 until valuesArray.length()) {
                    vector[j] = valuesArray.getDouble(j).toFloat()
                }
                results.add(normalizeIfNeeded(vector))
            }
        } else if (root.has("embedding")) {
            val valuesArray = root.getJSONObject("embedding").getJSONArray("values")
            val vector = FloatArray(valuesArray.length())
            for (j in 0 until valuesArray.length()) {
                vector[j] = valuesArray.getDouble(j).toFloat()
            }
            results.add(normalizeIfNeeded(vector))
        }

        return results
    }

    private fun normalizeIfNeeded(raw: FloatArray): FloatArray {
        val sliced = if (raw.size > dimensions) raw.copyOf(dimensions) else raw
        if (!normalize) return sliced

        var sumSq = 0.0
        for (v in sliced) {
            sumSq += (v * v).toDouble()
        }
        val norm = sqrt(sumSq).toFloat()
        if (norm > 1e-12f) {
            for (i in sliced.indices) {
                sliced[i] /= norm
            }
        }
        return sliced
    }

    override fun close() {
        // No persistent resources to release
    }
}
