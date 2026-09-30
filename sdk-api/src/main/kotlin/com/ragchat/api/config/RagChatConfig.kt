package com.ragchat.api.config

import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.crypto.KeyProvider
import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.governance.ConsentProvider
import com.ragchat.api.governance.PiiRedactor
import com.ragchat.api.governance.PolicyProvider
import com.ragchat.api.ingestion.Chunker
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.parser.DocumentParser
import com.ragchat.api.prompt.PromptTemplate
import com.ragchat.api.retrieval.QueryRewriter
import com.ragchat.api.retrieval.Reranker
import com.ragchat.api.storage.VectorStore
import com.ragchat.api.telemetry.TelemetrySink

/**
 * Immutable configuration defining the active RagChat SDK engine components.
 */
public class RagChatConfig internal constructor(
    public val routingMode: ModelRoutingMode,
    public val defaultTopK: Int,
    public val requestTimeoutMs: Long,
    public val localLlmProvider: LlmProvider?,
    public val cloudLlmProvider: LlmProvider?,
    public val embeddingProvider: EmbeddingProvider?,
    public val vectorStore: VectorStore?,
    public val parsers: List<DocumentParser>,
    public val chunker: Chunker?,
    public val reranker: Reranker?,
    public val queryRewriter: QueryRewriter?,
    public val promptTemplate: PromptTemplate?,
    public val authProvider: AuthProvider?,
    public val keyProvider: KeyProvider?,
    public val telemetrySink: TelemetrySink?,
    public val policyProvider: PolicyProvider?,
    public val consentProvider: ConsentProvider?,
    public val piiRedactor: PiiRedactor?,
    public val logger: RagChatLogger?,
    public val minLogLevel: LogLevel,
)

/**
 * Fluent builder for configuring and instantiating [RagChatConfig].
 */
public class RagChatConfigBuilder {
    /**
     * Strategy determining local vs cloud model execution. Defaults to [ModelRoutingMode.LOCAL_FIRST].
     */
    public var routingMode: ModelRoutingMode = ModelRoutingMode.LOCAL_FIRST

    /**
     * Default number of top results to retrieve in RAG search queries. Defaults to 5.
     */
    public var defaultTopK: Int = 5

    /**
     * Timeout in milliseconds for end-to-end generation requests. Defaults to 30,000 ms.
     */
    public var requestTimeoutMs: Long = 30_000L

    /**
     * Optional primary on-device LLM provider.
     */
    public var localLlmProvider: LlmProvider? = null

    /**
     * Optional primary cloud-hosted LLM provider.
     */
    public var cloudLlmProvider: LlmProvider? = null

    /**
     * Dense vector embedding provider.
     */
    public var embeddingProvider: EmbeddingProvider? = null

    /**
     * Persistent vector store backend.
     */
    public var vectorStore: VectorStore? = null

    /**
     * Document parsers registered with the pipeline.
     */
    public val parsers: MutableList<DocumentParser> = mutableListOf()

    /**
     * Text chunking strategy.
     */
    public var chunker: Chunker? = null

    /**
     * Reranking strategy.
     */
    public var reranker: Reranker? = null

    /**
     * Conversational query rewrite strategy.
     */
    public var queryRewriter: QueryRewriter? = null

    /**
     * Prompt formatting template.
     */
    public var promptTemplate: PromptTemplate? = null

    /**
     * Host-supplied authentication provider.
     */
    public var authProvider: AuthProvider? = null

    /**
     * Keystore passphrase or key material provider.
     */
    public var keyProvider: KeyProvider? = null

    /**
     * Operational metrics and traces sink.
     */
    public var telemetrySink: TelemetrySink? = null

    /**
     * Safety and compliance policy engine.
     */
    public var policyProvider: PolicyProvider? = null

    /**
     * User consent tracking provider.
     */
    public var consentProvider: ConsentProvider? = null

    /**
     * PII detection and redaction processor.
     */
    public var piiRedactor: PiiRedactor? = null

    /**
     * SDK internal logger.
     */
    public var logger: RagChatLogger? = null

    /**
     * Minimum log severity recorded by the SDK. Defaults to [LogLevel.INFO].
     */
    public var minLogLevel: LogLevel = LogLevel.INFO

    /**
     * Registers a document parser.
     */
    public fun addParser(parser: DocumentParser): RagChatConfigBuilder {
        parsers.add(parser)
        return this
    }

    /**
     * Builds and validates the immutable [RagChatConfig].
     */
    public fun build(): RagChatConfig =
        RagChatConfig(
            routingMode = routingMode,
            defaultTopK = defaultTopK,
            requestTimeoutMs = requestTimeoutMs,
            localLlmProvider = localLlmProvider,
            cloudLlmProvider = cloudLlmProvider,
            embeddingProvider = embeddingProvider,
            vectorStore = vectorStore,
            parsers = parsers.toList(),
            chunker = chunker,
            reranker = reranker,
            queryRewriter = queryRewriter,
            promptTemplate = promptTemplate,
            authProvider = authProvider,
            keyProvider = keyProvider,
            telemetrySink = telemetrySink,
            policyProvider = policyProvider,
            consentProvider = consentProvider,
            piiRedactor = piiRedactor,
            logger = logger,
            minLogLevel = minLogLevel,
        )
}
