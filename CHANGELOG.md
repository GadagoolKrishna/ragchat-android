# Changelog

All notable changes to the **RagChat Android SDK** are documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-30

### Added
- **Aggregator Facade (`:sdk`)**:
  - Fluent, type-safe builder DSL: `RagChat.builder(context) { llm { ... } embeddings { ... } storage { ... } policy { ... } }`.
  - Secure-by-default architecture with zero-PII logging, SQLCipher encryption, and local-first routing.
- **Pure Kotlin/JVM Core & Architecture (`:sdk-api`, `:sdk-core`, `:sdk-retrieval`, `:sdk-governance`, `:eval`)**:
  - Full modular separation with zero `android.*` framework dependencies in core modules.
  - Streaming conversational flow: query rewriting, policy evaluation, hybrid dense/sparse search (RRF), prompt injection demarcation (`<untrusted_context>`), and post-generation safety/PII redaction.
  - Multi-turn conversation memory with per-session and per-workspace scoping.
- **On-Device & Cloud Model Providers (`:sdk-android-llm-local`, `:sdk-android-llm-cloud`)**:
  - Gemini Nano integration via AICore.
  - Pluggable Cloud LLM provider with TLS 1.3, Server-Sent Events (SSE) streaming, and exponential backoff circuit breakers.
- **On-Device Embeddings & Document Ingestion (`:sdk-android-embeddings`, `:sdk-android-parsers`, `:sdk-android-storage`)**:
  - LiteRT EmbeddingGemma 768-dim inference with Matryoshka Representation Learning (MRL) truncation.
  - PDFBox text extraction and ML Kit OCR fallback for scanned documents.
  - Encrypted vector storage with Room and SQLCipher.
- **Governance, Privacy & Security (`:sdk-governance`)**:
  - Tamper-evident hash-chained audit log with sequence proofs.
  - PII masking with reversible tokens (Aadhaar, PAN, Credit Cards, IBAN, Phone, Email).
  - DPDP & GDPR consent management and data subject rights (export all, delete all).
  - Play Integrity and root detection policy hooks.
- **Material 3 Jetpack Compose UI (`:sdk-ui-compose`)**:
  - Complete standalone composables: `ChatScreen`, `MessageList`, `InputBar`, `CitationChip`, `SourceViewerSheet`, `DocumentManagerScreen`, `ModelStatusBanner`.
  - RTL, TalkBack accessibility, and localization in English, Hindi, Kannada, and Tamil.
- **Quality Gates & Evaluation Suite (`:eval`, `:benchmarks`)**:
  - Golden dataset schema, pluggable `LlmJudge` SPI, deterministic evaluator, and RAG regression thresholds.
  - Macrobenchmarks for cold start, TTFT, throughput, ingestion pages/min, and memory footprint.
- **Sample App (`:sample-app`)**:
  - Interactive multi-screen demo showcasing chat, documents, model catalog, privacy settings, and live debug routing inspectability.

---

## [0.9.0-rc1] - 2026-09-28
- Release candidate with complete SPI abstractions and initial Android adapters.
