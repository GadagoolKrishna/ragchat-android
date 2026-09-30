# RagChat Android SDK Architecture Guide

Enterprise-grade on-device RAG framework engineered with clean architecture, strict module boundaries, zero-PII logging, and pluggable Service Provider Interfaces (SPI).

---

## 1. High-Level Architecture Overview

```mermaid
graph TD
    HostApp[Host Application / UI] --> Facade[:sdk Aggregator Facade]
    Facade --> UI[:sdk-ui-compose]
    Facade --> Core[:sdk-core]
    Facade --> Storage[:sdk-android-storage]
    
    subgraph Pure JVM Layer [Zero android.* Dependencies]
        Core --> SPI[:sdk-api Interfaces]
        Retrieval[:sdk-retrieval] --> SPI
        Ingestion[:sdk-ingestion] --> SPI
        Governance[:sdk-governance] --> SPI
    end

    subgraph Android Platform Adapters
        LocalLLM[:sdk-android-llm-local] -.implements.-> SPI
        CloudLLM[:sdk-android-llm-cloud] -.implements.-> SPI
        Embeddings[:sdk-android-embeddings] -.implements.-> SPI
        Parsers[:sdk-android-parsers] -.implements.-> SPI
        Storage -.implements.-> SPI
        Models[:sdk-android-models]
        Work[:sdk-android-work]
    end
```

---

## 2. Module Responsibilities & Boundary Rules

| Module Group | Modules | Rules & Guarantees |
| :--- | :--- | :--- |
| **Pure JVM Domain** | `:sdk-api`, `:sdk-core`, `:sdk-retrieval`, `:sdk-ingestion`, `:sdk-governance` | **Zero `android.*` imports**. Enforced via ArchUnit and CI checks. Fully testable via standard JVM unit tests. |
| **Android Platform** | `:sdk-android-storage`, `:sdk-android-embeddings`, `:sdk-android-llm-local`, `:sdk-android-llm-cloud`, `:sdk-android-parsers`, `:sdk-android-models`, `:sdk-android-work` | Hardware acceleration (GPU/NPU delegates), Android Keystore, SQLCipher, AICore Gemini Nano, OkHttp/SSE. |
| **User Interface** | `:sdk-ui-compose` | Material 3 Compose components, themeable tokens (`RagChatTheme`), TalkBack accessibility, localization (`en`, `hi`, `kn`, `ta`). |
| **Aggregator** | `:sdk` | Fluent DSL builder (`RagChat.builder`), secure defaults, convenience delegates. |

---

## 3. The RAG Query Pipeline

```mermaid
sequenceDiagram
    participant User as User / App
    participant Chat as ChatManager (:sdk-core)
    participant Gov as Governance / Redactor
    participant Store as VectorStore (:sdk-android-storage)
    participant Router as ModelRouter (:sdk-core)
    participant Engine as LlmProvider

    User->>Chat: ask(query, options)
    Chat->>Gov: Check Consent & PII Redaction
    Gov-->>Chat: Masked Query
    Chat->>Store: Hybrid Dense + BM25 FTS Retrieval
    Store-->>Chat: Top-K Grounded Chunks
    Chat->>Router: Evaluate Context & Device State
    Router-->>Chat: Route to Local (Gemini Nano) or Cloud
    Chat->>Engine: Stream generation with injection barriers
    Engine-->>Chat: Token stream (Flow<LlmEvent>)
    Chat->>User: Emits Flow<ChatEvent> (Token, Citation, Done)
```

---

## 4. Concurrency & Memory Management
- **Reactive Streams**: 100% Kotlin Coroutines and `Flow`. No RxJava.
- **Dispatchers**: Compute-heavy tokenization and vector math execute strictly on `Dispatchers.Default`; file and database I/O execute on `Dispatchers.IO`.
- **Memory Pressure**: All model inference engines implement `ComponentCallbacks2.onTrimMemory` to release weights on system memory alerts.
