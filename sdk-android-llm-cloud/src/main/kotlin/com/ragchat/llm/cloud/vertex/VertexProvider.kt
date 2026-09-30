package com.ragchat.llm.cloud.vertex

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.Locality
import com.ragchat.llm.cloud.config.CloudClientConfig
import com.ragchat.llm.cloud.gemini.GeminiApiProvider

/**
 * Enterprise Google Cloud Vertex AI provider for Gemini models.
 *
 * Directs traffic to Google Cloud enterprise project/region endpoints and authenticates via GCP OAuth2 tokens.
 *
 * @property projectId GCP Project ID.
 * @property region GCP Region (e.g. "us-central1", "asia-south1").
 * @property modelId Vertex AI model identifier (e.g. "gemini-1.5-pro", "gemini-1.5-flash").
 * @property authProvider Supplies Google Cloud OAuth2 / IAM bearer tokens.
 * @property config Custom network configuration (mTLS, pinning, timeouts).
 * @property logger Redacted SDK logger.
 */
public class VertexProvider(
    public val projectId: String,
    public val region: String = "us-central1",
    public val modelId: String = "gemini-1.5-flash",
    private val authProvider: AuthProvider,
    private val config: CloudClientConfig =
        CloudClientConfig(
            baseUrl = "https://$region-aiplatform.googleapis.com",
        ),
    private val logger: RagChatLogger? = null,
) : LlmProvider by GeminiApiProvider(
        modelId = modelId,
        authProvider = authProvider,
        config =
            config.copy(
                baseUrl =
                    if (config.baseUrl.contains(region)) {
                        config.baseUrl
                    } else {
                        "https://$region-aiplatform.googleapis.com"
                    },
            ),
        logger = logger,
    ) {
    override val id: String = "vertex-$region-$modelId"

    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 1_000_000,
            maxOutputTokens = 8192,
            streaming = true,
            toolCalling = true,
            multimodal = true,
            locality = Locality.CLOUD,
            dataResidency = region,
        )
}
