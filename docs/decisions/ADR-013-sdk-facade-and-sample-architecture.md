# ADR-013: SDK Facade Builder DSL and Sample Application Architecture

## Status
Accepted

## Context
Host applications require an ergonomic, secure-by-default initialization flow that configures LLM engines, on-device embeddings, envelope encryption, policy engines, and UI bindings in 10 lines of code or fewer. Simultaneously, a comprehensive sample application is needed to demonstrate on-device vs cloud routing, document management, model weight downloads, and DPDP/GDPR compliance tools.

## Decisions
1. **Aggregator Facade (`:sdk`)**:
   - Provide a type-safe Kotlin DSL builder `RagChat.builder(context) { ... }`.
   - Provide secure defaults: AES-256-GCM envelope encryption backed by Android Keystore, SQLCipher database, on-device LiteRT embeddings, recursive chunking, and local-first routing.
2. **Sample Application (`:sample-app`)**:
   - Single-Activity Jetpack Compose application featuring 5 tabs: Chat, Documents, Settings, Models, and Privacy.
   - Live Model Routing Inspection Sheet presenting zero-PII diagnostic metadata (latency, routing decision reason codes, fallback state).
3. **Pluggable Architecture Documentation**:
   - Deliver comprehensive guides for custom LLMs, embedding models, vector stores, and parsers alongside a security whitepaper and integration checklist.

## Consequences
- Developers can initialize enterprise-grade RAG in minutes while retaining the power to customize every component via SPI interfaces.
- Binary compatibility and strict module isolation are preserved.
