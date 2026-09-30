# ADR-001: Public SPI and API Surface Architecture

- **Status**: Accepted
- **Date**: 2026-09-30
- **Author(s)**: RagChat Architecture Team
- **PR / Issue**: #1
- **Supersedes**: N/A
- **Superseded By**: N/A

---

## Context and Problem Statement
The RagChat Android SDK provides enterprise on-device RAG, chat UI, document ingestion, and pluggable LLM backends (Gemini Nano, Gemma via LiteRT/MediaPipe, and cloud endpoints). To maintain long-term stability and binary compatibility across diverse customer applications, the public API surface must:
1. Provide a decoupled Service Provider Interface (SPI) for every swappable capability.
2. Maintain strict module isolation: `:sdk-api` must be pure Kotlin/JVM with zero `android.*` imports and zero third-party dependencies in public signatures.
3. Protect enterprise user privacy by strictly prohibiting raw prompt, chunk, or PII leaks in errors and logging.
4. Support rapid host app integration and unit testing without requiring real on-device hardware models.

---

## Decision Drivers
- **Pluggability**: Seamless switching between on-device models (Gemini Nano / Gemma) and cloud providers.
- **Portability & Testability**: Core logic can be unit-tested without Android runtime emulators or physical devices.
- **Privacy & Security**: Zero-PII logging, encrypted storage abstraction, and host-supplied authentication.
- **Binary Compatibility**: Strict public API tracking using Kotlin Binary Compatibility Validator (`apiDump`).

---

## Considered Options
1. **Option 1: Monolithic Android SDK**: All interfaces and Android implementations combined in a single library module.
2. **Option 2: Decoupled SPI with Pure Kotlin `:sdk-api` and dedicated `:sdk-testing` module**: Interfaces in pure Kotlin module, testing fakes in `:sdk-testing`, and platform adapters in `:sdk-android-*`.

---

## Decision Outcome
Chosen option: **Option 2 (Decoupled SPI in `:sdk-api`)**.

### Positive Consequences
- Guarantees zero Android framework coupling in domain contracts.
- Host applications can supply custom LLMs, vector stores, and parsers by implementing `:sdk-api` SPIs.
- Instant unit testing with full coverage using `:sdk-testing` fakes.
- Clear binary compatibility boundaries verified by `binary-compatibility-validator`.

### Negative Consequences / Trade-offs
- Requires data models (e.g. `DocumentSource`) to wrap streams rather than directly using Android `android.net.Uri` or `android.content.Context`.

---

## Security, Privacy & Governance Impact
- `SdkError` sealed hierarchy enforces machine-readable error codes and explicitly bans raw prompt/chunk string interpolation.
- `RagChatLogger` routes all diagnostic messages through redaction filters.
- `AuthProvider` guarantees no hardcoded secrets or API keys exist inside the SDK.

---

## Compliance with Definition of Done (DoD)
- [x] Interfaces and models defined with complete KDoc and `explicitApi()` mode.
- [x] Zero `android.*` imports verified in `:sdk-api` and `:sdk-testing`.
- [x] Test doubles implemented for all SPIs in `:sdk-testing`.
- [x] Verified 20-line custom `LlmProvider` example test passes.
- [x] ADR documented in `docs/decisions/ADR-001-public-spi-and-api-surface.md`.
