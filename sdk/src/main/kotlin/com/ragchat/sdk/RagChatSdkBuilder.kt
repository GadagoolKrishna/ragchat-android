package com.ragchat.sdk

import android.content.Context
import com.ragchat.api.audit.AuditSink
import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.api.config.RagChatConfigBuilder
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
import com.ragchat.api.telemetry.TelemetrySink
import com.ragchat.embeddings.local.OnDeviceEmbeddingProvider
import com.ragchat.ingestion.chunking.RecursiveChunker
import com.ragchat.llm.cloud.gemini.GeminiApiProvider
import com.ragchat.llm.local.nano.GeminiNanoProvider
import com.ragchat.parsers.DefaultParserRegistry
import com.ragchat.storage.SqlCipherVectorStore
import com.ragchat.storage.crypto.AndroidKeystoreKeyProvider
import com.ragchat.storage.crypto.EnvelopeEncryptionManager
import com.ragchat.storage.db.RagChatDatabase
import com.ragchat.storage.db.SqlCipherDatabaseProvider

/**
 * DSL scope for configuring LLM runtimes, models, and routing strategies.
 */
public class LlmScope internal constructor(
    private val context: Context,
) {
    /**
     * Local on-device LLM provider.
     */
    public var localProvider: LlmProvider? = null

    /**
     * Cloud-hosted LLM provider.
     */
    public var cloudProvider: LlmProvider? = null

    /**
     * Routing strategy between local and cloud providers. Defaults to [ModelRoutingMode.LOCAL_FIRST].
     */
    public var routing: ModelRoutingMode = ModelRoutingMode.LOCAL_FIRST

    /**
     * Configures Gemini Nano as the local provider.
     */
    public fun local(provider: LlmProvider? = null) {
        this.localProvider = provider ?: GeminiNanoProvider(context)
    }

    /**
     * Configures a cloud LLM provider.
     */
    public fun cloud(provider: LlmProvider) {
        this.cloudProvider = provider
    }

    /**
     * Convenience method to configure Google Generative Language API (Gemini Cloud).
     */
    public fun geminiCloud(
        authProvider: AuthProvider,
        modelId: String = "gemini-1.5-flash",
    ) {
        this.cloudProvider = GeminiApiProvider(modelId = modelId, authProvider = authProvider)
    }
}

/**
 * DSL scope for configuring vector embeddings.
 */
public class EmbeddingScope internal constructor() {
    /**
     * Active embedding provider.
     */
    public var provider: EmbeddingProvider? = null

    /**
     * Configures on-device LiteRT embeddings (e.g., EmbeddingGemma).
     */
    public fun onDevice(dimensions: Int = 768) {
        this.provider = OnDeviceEmbeddingProvider(dimensions = dimensions)
    }

    /**
     * Configures a custom embedding provider.
     */
    public fun custom(provider: EmbeddingProvider) {
        this.provider = provider
    }
}

/**
 * DSL scope for configuring secure persistence and vector indexing.
 */
public class StorageScope internal constructor(
    private val context: Context,
) {
    /**
     * Custom database instance or null for default encrypted SQLCipher.
     */
    public var customDatabase: RagChatDatabase? = null

    /**
     * Scope identifier for multi-tenant data isolation. Defaults to "default_scope".
     */
    public var scopeId: String = "default_scope"

    /**
     * Enables AES-256-GCM envelope encryption backed by Android Keystore.
     */
    public fun encrypted() {
        // Default behavior: encrypted storage
    }

    /**
     * Injects a pre-configured database instance.
     */
    public fun database(db: RagChatDatabase) {
        this.customDatabase = db
    }
}

/**
 * DSL scope for configuring governance and safety guardrails.
 */
public class PolicyScope internal constructor() {
    /**
     * Policy provider evaluating security rules.
     */
    public var provider: PolicyProvider? = null

    /**
     * Sets a policy provider.
     */
    public fun provider(provider: PolicyProvider) {
        this.provider = provider
    }
}

/**
 * DSL scope for telemetry, audit logging, and diagnostics.
 */
public class TelemetryScope internal constructor() {
    /**
     * Metrics and trace sink.
     */
    public var telemetrySink: TelemetrySink? = null

    /**
     * Security and compliance audit log sink.
     */
    public var auditSink: AuditSink? = null

    /**
     * Redacted diagnostic logger.
     */
    public var logger: RagChatLogger? = null

    /**
     * Minimum log level. Defaults to [LogLevel.INFO].
     */
    public var minLogLevel: LogLevel = LogLevel.INFO

    /**
     * Configures an operational telemetry sink.
     */
    public fun telemetry(sink: TelemetrySink) {
        this.telemetrySink = sink
    }

    /**
     * Configures a compliance audit sink.
     */
    public fun audit(sink: AuditSink) {
        this.auditSink = sink
    }

    /**
     * Configures a redacted logger.
     */
    public fun logging(
        logger: RagChatLogger,
        minLevel: LogLevel = LogLevel.INFO,
    ) {
        this.logger = logger
        this.minLogLevel = minLevel
    }
}

/**
 * DSL scope for privacy, user consent, and PII redaction.
 */
public class GovernanceScope internal constructor() {
    /**
     * User consent tracking provider.
     */
    public var consentProvider: ConsentProvider? = null

    /**
     * PII detector and redactor.
     */
    public var piiRedactor: PiiRedactor? = null

    /**
     * Configures user consent provider.
     */
    public fun consent(provider: ConsentProvider) {
        this.consentProvider = provider
    }

    /**
     * Configures PII redactor.
     */
    public fun pii(redactor: PiiRedactor) {
        this.piiRedactor = redactor
    }
}

/**
 * Top-level builder DSL constructing and initializing the RagChat SDK facade.
 */
public class RagChatSdkBuilder internal constructor(
    private val context: Context,
) {
    private val llmScope = LlmScope(context)
    private val embeddingScope = EmbeddingScope()
    private val storageScope = StorageScope(context)
    private val policyScope = PolicyScope()
    private val telemetryScope = TelemetryScope()
    private val governanceScope = GovernanceScope()

    private var chunkerInstance: Chunker? = null
    private var customParsers: MutableList<DocumentParser>? = null
    private var customPromptTemplate: PromptTemplate? = null
    private var customQueryRewriter: QueryRewriter? = null
    private var customReranker: Reranker? = null
    private var topKValue: Int = 5
    private var requestTimeoutValueMs: Long = 30_000L

    /**
     * Configures LLM inference engines and routing.
     */
    public fun llm(block: LlmScope.() -> Unit) {
        llmScope.apply(block)
    }

    /**
     * Configures vector embedding generation.
     */
    public fun embeddings(block: EmbeddingScope.() -> Unit) {
        embeddingScope.apply(block)
    }

    /**
     * Configures encrypted persistent storage.
     */
    public fun storage(block: StorageScope.() -> Unit) {
        storageScope.apply(block)
    }

    /**
     * Configures safety and access control policies.
     */
    public fun policy(block: PolicyScope.() -> Unit) {
        policyScope.apply(block)
    }

    /**
     * Configures telemetry and audit sinks.
     */
    public fun telemetry(block: TelemetryScope.() -> Unit) {
        telemetryScope.apply(block)
    }

    /**
     * Configures privacy, consent, and PII masking.
     */
    public fun governance(block: GovernanceScope.() -> Unit) {
        governanceScope.apply(block)
    }

    /**
     * Sets the default top-K retrieval count.
     */
    public fun topK(k: Int) {
        this.topKValue = k
    }

    /**
     * Sets document chunker.
     */
    public fun chunker(chunker: Chunker) {
        this.chunkerInstance = chunker
    }

    /**
     * Adds custom document parsers.
     */
    public fun parsers(vararg parsers: DocumentParser) {
        if (customParsers == null) {
            customParsers = mutableListOf()
        }
        customParsers?.addAll(parsers)
    }

    /**
     * Compiles configuration and initializes the [RagChat] singleton.
     */
    public suspend fun build(): RagChat {
        val keystoreProvider = AndroidKeystoreKeyProvider(context)
        val enc = EnvelopeEncryptionManager(context, keystoreProvider)
        val db = storageScope.customDatabase ?: SqlCipherDatabaseProvider(context, enc).createDatabase()
        val vectorStore = SqlCipherVectorStore(storageScope.scopeId, db, enc)

        // Default on-device embeddings if not specified
        val embeddings = embeddingScope.provider ?: OnDeviceEmbeddingProvider()
        val parsersList = customParsers ?: DefaultParserRegistry.createDefaultParsers()
        val chunker = chunkerInstance ?: RecursiveChunker()

        val config =
            RagChatConfigBuilder()
                .apply {
                    routingMode = llmScope.routing
                    defaultTopK = topKValue
                    requestTimeoutMs = requestTimeoutValueMs
                    localLlmProvider = llmScope.localProvider
                    cloudLlmProvider = llmScope.cloudProvider
                    embeddingProvider = embeddings
                    this.vectorStore = vectorStore
                    parsers.addAll(parsersList)
                    this.chunker = chunker
                    reranker = customReranker
                    queryRewriter = customQueryRewriter
                    promptTemplate = customPromptTemplate
                    policyProvider = policyScope.provider
                    consentProvider = governanceScope.consentProvider
                    piiRedactor = governanceScope.piiRedactor
                    telemetrySink = telemetryScope.telemetrySink
                    auditSink = telemetryScope.auditSink
                    logger = telemetryScope.logger
                    minLogLevel = telemetryScope.minLogLevel
                }.build()

        RagChat.initialize(
            context = context,
            config = config,
            database = db,
        )
        return RagChat
    }
}
