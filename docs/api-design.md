# RagChat Android SDK — API Design & Extension Points

The RagChat Android SDK is architected with a decoupled Service Provider Interface (SPI) layer in `:sdk-api`. All public interfaces are pure Kotlin/JVM (`zero android.* imports`), strictly typed, and reactive via Kotlin Coroutines and `kotlinx.coroutines.flow.Flow`.

---

## 1. Architectural Principles

1. **Pure Kotlin Domain Core**:
   - The `:sdk-api`, `:sdk-core`, `:sdk-ingestion`, `:sdk-retrieval`, `:sdk-governance`, and `:sdk-testing` modules contain no references to the Android framework (`android.*`).
   - Platform-dependent logic (Android Keystore, MediaPipe/LiteRT, SQLite/Room, WorkManager, Jetpack Compose) resides strictly within `:sdk-android-*` adapter modules.

2. **Zero-PII Guarantees**:
   - Errors (`SdkError`) and internal logs (`RagChatLogger`) never serialize or leak raw user prompts, chunk text, document contents, embeddings, or personally identifiable information (PII).

3. **Decoupled Pluggable SPI**:
   - Every major system capability is declared as an SPI interface in `:sdk-api`. Default implementations are swappable, and developers can integrate custom on-device or cloud components by implementing these interfaces.

---

## 2. Core Service Provider Interfaces (SPIs)

| Interface | Package | Purpose |
|---|---|---|
| `LlmProvider` | `com.ragchat.api.llm` | Pluggable LLM inference engine (streaming, token counting, availability). |
| `EmbeddingProvider` | `com.ragchat.api.embedding` | Dense vector embedding generator with configurable dimensionality. |
| `VectorStore` | `com.ragchat.api.storage` | Encrypted vector indexing, ANN queries, hybrid FTS, and crypto-shredding. |
| `DocumentParser` | `com.ragchat.api.parser` | Multi-format document parser converting binary streams into structured elements. |
| `Chunker` | `com.ragchat.api.ingestion` | Text segmenter slicing structured elements into indexed chunks. |
| `Reranker` | `com.ragchat.api.retrieval` | Re-ranking model scoring retrieved candidates against queries. |
| `QueryRewriter` | `com.ragchat.api.retrieval` | Contextual query reformulation resolving conversation anaphoras. |
| `PromptTemplate` | `com.ragchat.api.prompt` | Formatter generating grounding prompts with citations and context. |
| `AuthProvider` | `com.ragchat.api.auth` | Host-supplied authentication provider supplying short-lived tokens. |
| `KeyProvider` | `com.ragchat.api.crypto` | Host-supplied key management provider supplying encryption keys. |
| `TelemetrySink` | `com.ragchat.api.telemetry` | OpenTelemetry-compatible metrics and event sink. |
| `AuditSink` | `com.ragchat.api.audit` | Enterprise compliance audit logger. |
| `PolicyProvider` | `com.ragchat.api.governance` | Guardrail evaluation before inference and tool execution. |
| `ConsentProvider` | `com.ragchat.api.governance` | Verifier for end-user data processing consent. |
| `PiiRedactor` | `com.ragchat.api.governance` | Pattern-based and NER-based sensitive data redactor. |
| `RagChatLogger` | `com.ragchat.api.logging` | Diagnostic logger with configurable severity levels and PII redaction. |

---

## 3. Custom Extension Examples

### 3.1. 20-Line Custom LlmProvider Example

Implementing a custom LLM provider requires implementing the `LlmProvider` interface and emitting streaming `LlmEvent` items:

```kotlin
class CustomEchoLlmProvider : LlmProvider {
    override val id: String = "custom-echo-llm"
    override val capabilities: LlmCapabilities = LlmCapabilities(2048, 512, true, false, false, Locality.LOCAL)
    override suspend fun availability(): Availability = Availability.AVAILABLE
    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow {
        val prompt = request.messages.lastOrNull()?.content.orEmpty()
        emit(LlmEvent.Token("Echo: $prompt"))
        emit(LlmEvent.Done)
    }
    override suspend fun countTokens(text: String): Int = text.length / 4
    override fun close() {}
}
```

*(Note: This exact 20-line implementation is verified by automated tests in `:sdk-testing`.)*

### 3.2. Configuring RagChat with the DSL

```kotlin
val ragChatConfig = RagChat.builder {
    routingMode = ModelRoutingMode.LOCAL_FIRST
    defaultTopK = 5
    localLlmProvider = CustomEchoLlmProvider()
    embeddingProvider = FakeEmbeddingProvider(dimensions = 384)
    vectorStore = FakeVectorStore()
    authProvider = FakeAuthProvider()
}
```

---

## 4. Testing Support (`:sdk-testing`)

The `:sdk-testing` module supplies ready-to-use, thread-safe test doubles for all public SPI interfaces:
- `FakeLlmProvider`: Emits deterministic token sequences and records received requests.
- `FakeEmbeddingProvider`: Generates deterministic normalized dense vectors.
- `FakeVectorStore`: In-memory concurrent vector store supporting exact cosine nearest-neighbor queries.
- `FakeDocumentParser`: Generates structured mock headings and paragraphs.
- `FakeChunker`: Deterministic text chunker with sequence tracking.
- `FakeAuthProvider`, `FakeKeyProvider`, `FakeTelemetrySink`, `FakeAuditSink`, `FakePolicyProvider`, `FakeConsentProvider`, `FakePiiRedactor`, `FakeRagChatLogger`.
