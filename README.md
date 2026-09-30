# RagChat Android SDK

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-green.svg)](https://developer.android.com)
[![OWASP MASVS L2](https://img.shields.io/badge/Security-OWASP%20MASVS%20L2-brightgreen.svg)](docs/security/threat-model-stride.md)
[![Zero-PII Logging](https://img.shields.io/badge/Privacy-Zero--PII%20Logging-success.svg)](docs/compliance/data-flow-diagram.md)

Enterprise-grade Android SDK providing chat UI, on-device Retrieval-Augmented Generation (RAG: document upload, chunking, embeddings, hybrid search), and a pluggable LLM layer (Gemini Nano via AICore, Gemma via MediaPipe/LiteRT, or cloud APIs).

---

## Key Capabilities

- **Zero-Cloud / On-Device First**: Complete conversational RAG workflow executed 100% locally on device using Gemini Nano (AICore) or LiteRT Gemma.
- **Pluggable Architecture (SPI)**: All major capabilities (LLM engines, embedding generators, vector stores, chunkers, tokenizers, auth providers, device attestation) are decoupled interfaces in `:sdk-api`.
- **Hybrid Retrieval & RRF Fusion**: Reciprocal Rank Fusion (RRF) blending dense vector similarity with sparse BM25 / SQLite FTS token search.
- **Enterprise Governance & Compliance**:
  - Hash-chained tamper-evident audit logs with cryptographic sequence proofs.
  - Reversible token PII masking (Aadhaar, PAN, Credit Cards, IBAN, Phone, Email).
  - DPDP Act & GDPR consent management with verified crypto-shredding and data subject export/purge tools.
  - Play Integrity and root detection guardrail hooks (`DeviceIntegrityPolicyProvider`).
- **Defense-in-Depth Prompt Safety**: Injection defense pipeline with untrusted context demarcation (`<untrusted_context>`), instruction neutralization, and grounding prompt constraints.
- **Production-Ready Material 3 UI**: Jetpack Compose chat components (`ChatScreen`, `MessageList`, `SourceViewerSheet`, `DocumentManagerScreen`, `CitationChip`) supporting streaming markdown, syntax highlighting, dark mode, TalkBack accessibility, and localization (en, hi, kn, ta).
- **Sub-1 MB Footprint**: Compressed core aggregator AAR is only **34 KB**; all 9 Android artifacts combined total **~836 KB** (excluding model weights).

---

## Architecture Overview

RagChat strictly enforces modular separation between pure Kotlin/JVM core logic and Android platform adapters:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        :sample-app / Host Application                  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                      :sdk (Public Aggregator Facade)                   │
├────────────────────────────────────────────────────────────────────────┤
│                           :sdk-ui-compose                              │
│         (ChatScreen, MessageList, DocumentManager, SourceViewer)       │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
    ┌───────────────▼──────────────┐  ┌──────────────▼──────────────┐
    │       :sdk-core              │  │      :sdk-governance        │
    │  • Prompt Builder & Safety   │  │  • PII Masking & Detekt     │
    │  • Multi-Turn Memory         │  │  • Tamper-Evident Audit Log │
    │  • Context Assembly          │  │  • Policy Engine & Rules    │
    │  • Citation Tracking         │  │  • Consent & Data Subject   │
    └───────────────┬──────────────┘  └──────────────┬──────────────┘
                    │                                │
    ┌───────────────▼────────────────────────────────▼──────────────┐
    │                        :sdk-retrieval                         │
    │  • Hybrid Search (Dense + Sparse BM25 / FTS)                   │
    │  • Reciprocal Rank Fusion (RRF) & Relevance Re-ranking         │
    └───────────────────────────────┬───────────────────────────────┘
                                    │
    ┌───────────────────────────────▼───────────────────────────────┐
    │                    :sdk-api (SPI Foundation)                  │
    │       Zero Android Imports • Pure Kotlin/JVM Domain Models    │
    └───────────────────────────────┬───────────────────────────────┘
                                    │
    ┌───────────────────────────────┴───────────────────────────────┐
    │                Pluggable Android Implementations              │
    ├───────────────────────────────┬───────────────────────────────┤
    │  :sdk-android-llm-local       │  Gemini Nano / AICore         │
    │  :sdk-android-llm-cloud       │  Cloud LLM with TLS 1.3 / SSE │
    │  :sdk-android-embeddings      │  LiteRT EmbeddingGemma        │
    │  :sdk-android-storage         │  Room + SQLCipher (AES-256)   │
    │  :sdk-android-parsers         │  PDFBox & ML Kit OCR          │
    │  :sdk-android-models          │  Catalog, Signature Verify    │
    │  :sdk-android-work            │  WorkManager Background Sync  │
    └───────────────────────────────┴───────────────────────────────┘
```

---

## 15-Minute Quick Start

### 1. Add Dependencies

Add the RagChat SDK dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core SDK aggregator facade
    implementation("com.ragchat.sdk:sdk:1.0.0")

    // Material 3 Compose Chat UI (Optional)
    implementation("com.ragchat.sdk:sdk-ui-compose:1.0.0")

    // Android platform adapters
    implementation("com.ragchat.sdk:sdk-android-storage:1.0.0")
    implementation("com.ragchat.sdk:sdk-android-embeddings:1.0.0")
    implementation("com.ragchat.sdk:sdk-android-llm-local:1.0.0")
    implementation("com.ragchat.sdk:sdk-android-llm-cloud:1.0.0")
    implementation("com.ragchat.sdk:sdk-android-parsers:1.0.0")
}
```

### 2. Configure Host Manifest for Data Protection

In your `AndroidManifest.xml`, configure data extraction rules to prevent database or key backup:

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/ragchat_data_extraction_rules"
    android:fullBackupContent="@xml/ragchat_backup_rules"
    ...>
</application>
```

### 3. Initialize the SDK (10-Line Setup)

Initialize the SDK in your `Application` class or DI module:

```kotlin
val rag = RagChat.builder(context) {
    llm {
        local(GeminiNano)
        cloud(GeminiApi(authProvider))
        routing = RoutingMode.LOCAL_FIRST
    }
    embeddings {
        onDevice()
    }
    storage {
        encrypted()
    }
    policy {
        blockCloudWhenRoaming()
        confidentialNeverLeavesDevice()
    }
}.build()
```

### 4. Index Documents & Ask Questions

```kotlin
// Ingest a document
val result = rag.ingestDocument(
    file = File(context.filesDir, "security_policy.pdf"),
    title = "Enterprise Security Policy"
)

// Ask a question with streaming reactive events
rag.chat.ask("What is our password retention rule?")
    .collect { event ->
        when (event) {
            is ChatEvent.Token -> print(event.text)
            is ChatEvent.Citations -> println("\nSources: ${event.citations.map { it.documentId }}")
            is ChatEvent.Complete -> println("\nCompleted in ${event.latencyMs}ms")
            is ChatEvent.Error -> eprintln("Error: ${event.message}")
        }
    }
```

### 5. Compose Chat UI Integration

Drop in the production-ready `ChatScreen`:

```kotlin
@Composable
fun MyChatRoute(rag: RagChat) {
    val viewModel = remember { RagChatViewModelFactory(rag).create(ChatViewModel::class.java) }
    
    RagChatTheme {
        ChatScreen(
            viewModel = viewModel,
            onOpenSourceViewer = { docId, page, chunkId ->
                // Displays source document sheet
            }
        )
    }
}
```

---

## Security, Privacy & MASVS L2 Verification

RagChat is engineered for regulated sectors (healthcare, banking, government, enterprise):

- **Encrypted at Rest**: All persistent storage uses SQLCipher with 256-bit AES page encryption. Master encryption keys are generated in `AndroidKeyStore` backed by hardware `StrongBox` or TEE.
- **Zero-PII Logging**: Internal logger strips all raw prompts, chunks, vectors, and chat history.
- **Crypto-Shredding**: Purging a document or collection permanently wipes the associated data encryption key (DEK), ensuring ciphertext is cryptographically unrecoverable.
- **OWASP MASVS L2 & Top 10 for LLMs**: Audited against Mobile Application Security Verification Standard Level 2 and OWASP Top 10 for LLM Applications (Prompt Injection, Insecure Output Handling, Training Data Poisoning).
- **STRIDE Threat Model**: Detailed threat vectors, mitigations, and data flow assessments are documented in [STRIDE Threat Model](docs/security/threat-model-stride.md).

---

## Modules Directory

| Module | Runtime | Purpose |
| :--- | :--- | :--- |
| **`:sdk-api`** | Pure Kotlin/JVM | Public interfaces, SPI contracts, and domain models (0 `android.*` imports). |
| **`:sdk-core`** | Pure Kotlin/JVM | Prompt builder, multi-turn memory, injection sanitization, citation mapper. |
| **`:sdk-retrieval`** | Pure Kotlin/JVM | Dense vector search, sparse BM25/FTS search, and Reciprocal Rank Fusion (RRF). |
| **`:sdk-governance`** | Pure Kotlin/JVM | Policy engine, PII masking, consent manager, and tamper-evident audit logs. |
| **`:sdk-ingestion`** | Pure Kotlin/JVM | Chunking strategies, semantic splitters, and metadata extraction pipelines. |
| **`:sdk-testing`** | Pure Kotlin/JVM | In-memory test doubles and fakes (`FakeLlmEngine`, `FakeVectorStore`). |
| **`:eval`** | Pure Kotlin/JVM | Golden dataset runner, RAG metrics (`Recall@K`, `Faithfulness`), and `LlmJudge`. |
| **`:sdk`** | Android | Aggregator facade providing the fluent `RagChat.builder(context)` DSL. |
| **`:sdk-ui-compose`** | Android | Material 3 Jetpack Compose chat components, sheets, and accessibility hooks. |
| **`:sdk-android-storage`** | Android | Room + SQLCipher encrypted database and vector index storage. |
| **`:sdk-android-embeddings`**| Android | On-device embedding inference using LiteRT / EmbeddingGemma. |
| **`:sdk-android-llm-local`** | Android | On-device LLM inference using Gemini Nano (AICore) and Gemma. |
| **`:sdk-android-llm-cloud`** | Android | Cloud LLM provider with TLS 1.3, SSE streaming, and circuit breakers. |
| **`:sdk-android-models`** | Android | Model catalog, signed integrity verification, and atomic weight management. |
| **`:sdk-android-parsers`** | Android | Document parsers (PDFBox text extractor and ML Kit OCR fallback). |
| **`:sdk-android-work`** | Android | WorkManager background sync and maintenance jobs. |
| **`:sample-app`** | Android | Multi-screen reference application showcasing full SDK capabilities. |
| **`:benchmarks`** | Android | Macrobenchmarks for TTFT, tokens/sec throughput, and cold start memory. |

---

## Documentation & Guides

- [15-Minute Quick Start Guide](docs/guides/15-minute-quick-start.md)
- [Architecture Guide & Design](docs/architecture-guide.md)
- [API Design Specification](docs/api-design.md)
- [STRIDE Threat Model & OWASP MASVS L2](docs/security/threat-model-stride.md)
- [Mobile Security Static Analysis Checklist](docs/security/mobile-security-static-analysis.md)
- [Security Whitepaper](docs/security-whitepaper.md)
- [Custom LLM Provider Guide](docs/guides/custom-providers/custom-llm-provider.md)
- [Custom Embedding Provider Guide](docs/guides/custom-providers/custom-embedding-provider.md)
- [Device Farm & Test Matrix (Firebase Test Lab)](docs/testing/device-test-matrix.md)
- [Supply Chain, SBOM & Model Licensing](docs/supply-chain/sbom-and-license-report.md)
- [Versioning, Deprecation & LTS Policy](docs/governance/versioning-and-lts-policy.md)
- [Quality Gates & Report](docs/quality-report.md)
- [Architectural Decision Records (ADRs)](docs/decisions/)
- [Release Changelog](CHANGELOG.md)
- [Vulnerability Disclosure Policy](SECURITY.md)

---

## Quality & Verification

To run formatting, static analysis, unit tests, and binary compatibility checks across the entire codebase:

```bash
# Code formatting
./gradlew spotlessApply

# Static analysis and linting
./gradlew detekt lint

# Binary compatibility check
./gradlew apiCheck

# Run all unit tests and quality verification
./gradlew check
```

---

## License

```
Copyright 2026 RagChat Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
