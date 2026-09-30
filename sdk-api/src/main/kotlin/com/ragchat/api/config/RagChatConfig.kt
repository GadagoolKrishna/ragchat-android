package com.ragchat.api.config

import com.ragchat.api.audit.AuditSink
import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.chat.ChatHistoryStore
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
import com.ragchat.api.routing.DeviceContext
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
    public val chatHistoryStore: ChatHistoryStore?,
    public val authProvider: AuthProvider?,
    public val keyProvider: KeyProvider?,
    public val telemetrySink: TelemetrySink?,
    public val auditSink: AuditSink?,
    public val policyProvider: PolicyProvider?,
    public val consentProvider: ConsentProvider?,
    public val piiRedactor: PiiRedactor?,
    public val logger: RagChatLogger?,
    public val minLogLevel: LogLevel,
    public val deviceContext: DeviceContext = DeviceContext(),
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
     * Maximum timeout duration in milliseconds for retrieval or LLM inference operations.
     */
    public var requestTimeoutMs: Long = 30_000L

    /**
     * Local on-device LLM provider (e.g., Gemini Nano or LiteRT Gemma).
     */
    public var localLlmProvider: LlmProvider? = null

    /**
     * Remote cloud LLM provider (e.g., Gemini API, Vertex, Claude, OpenAI).
     */
    public var cloudLlmProvider: LlmProvider? = null

    /**
     * Embedding provider generating dense vectors.
     */
    public var embeddingProvider: EmbeddingProvider? = null

    /**
     * Persistent or in-memory vector storage engine.
     */
    public var vectorStore: VectorStore? = null

    /**
     * Registered document parsers.
     */
    public val parsers: MutableList<DocumentParser> = mutableListOf()

    /**
     * Document chunker implementation.
     */
    public var chunker: Chunker? = null

    /**
     * Optional re-ranking provider.
     */
    public var reranker: Reranker? = null

    /**
     * Contextual query reformulation engine.
     */
    public var queryRewriter: QueryRewriter? = null

    /**
     * Prompt formatting and citation grounding template.
     */
    public var promptTemplate: PromptTemplate? = null

    /**
     * Persistent multi-turn chat history store.
     */
    public var chatHistoryStore: ChatHistoryStore? = null

    /**
     * Authentication credentials provider.
     */
    public var authProvider: AuthProvider? = null

    /**
     * Cryptographic key management provider.
     */
    public var keyProvider: KeyProvider? = null

    /**
     * Operational metrics and traces sink.
     */
    public var telemetrySink: TelemetrySink? = null

    /**
     * Security and compliance audit log sink.
     */
    public var auditSink: AuditSink? = null

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
     * Dynamic device environmental context.
     */
    public var deviceContext: DeviceContext = DeviceContext()

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
            chatHistoryStore = chatHistoryStore,
            authProvider = authProvider,
            keyProvider = keyProvider,
            telemetrySink = telemetrySink,
            auditSink = auditSink,
            policyProvider = policyProvider,
            consentProvider = consentProvider,
            piiRedactor = piiRedactor,
            logger = logger,
            minLogLevel = minLogLevel,
            deviceContext = deviceContext,
        )
}
