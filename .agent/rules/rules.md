# Workspace Rules: RagChat Android SDK

Enterprise-grade Android SDK providing chat UI, on-device RAG (document upload, chunking, embeddings, retrieval), and a pluggable LLM layer (Gemini Nano, Gemma via MediaPipe/LiteRT, or cloud APIs).

---

## 1. Language, Runtimes & Concurrency
- **Language**: Kotlin only.
- **Concurrency & Reactive Streams**: Coroutines and `kotlinx.coroutines.flow.Flow` only. **No RxJava** permitted anywhere in the codebase.
- **Target Platforms**: Min SDK `26`, compile/target latest stable Android SDK.
- **Toolchain / JDK**: Java 17 toolchain for compilation and bytecode target.

---

## 2. Architecture & Module Separation
- **Pure Kotlin/JVM Modules**:
  - Modules: `:api`, `:core`, `:retrieval`, `:governance` (and any related pure domain/logic modules).
  - Must remain completely free of Android framework dependencies (**zero `android.*` imports**).
  - Continuous Integration (CI) and build checks must explicitly fail if any pure Kotlin/JVM module introduces an `android.*` dependency or import.
- **Android Modules**:
  - Modules: `:adapters`, `:ui` (and any platform-specific implementations).
  - Contain Android framework-dependent components, Android UI, hardware bindings, and platform service adapters.

---

## 3. Public API Rigor & Binary Compatibility
- **API Visibility**: Enforce `explicitApiMode = ExplicitApiMode.Strict` (or `explicitApi()`) on all published modules.
- **Minimal Surface**: Keep public surfaces strictly scoped; internal utilities, helpers, and state must be `internal` or `private`.
- **Documentation**: All public declarations (classes, interfaces, functions, properties) must have complete, descriptive KDoc.
- **Binary Compatibility**: Public API changes must be tracked and validated using the Kotlin Binary Compatibility Validator (`binary-compatibility-validator`).

---

## 4. Pluggable Architecture & Service Provider Interfaces (SPI)
- **SPI in `:sdk-api`**: Every replaceable capability (LLM engine, chunker, vector store, embedding generator, tokenizer, auth provider) must be declared as an interface / SPI in `:sdk-api`.
- **Module Decoupling**: Default implementations of these interfaces must live in their own dedicated modules (e.g., `:adapter-gemini-nano`, `:retrieval-memory`, `:retrieval-sqlite-vec`). The core SDK orchestrates strictly against interfaces.

---

## 5. Privacy, Redaction & Zero-PII Logging
- **Strict Data Redaction**: Under no circumstances should prompts, raw document text, vector embeddings, chat messages, or Personally Identifiable Information (PII) be logged.
- **SDK Logger**: Never use `android.util.Log` or `println` directly. Always route logging through the internal SDK Logger with redaction and configurable log levels.
- Sanitized diagnostics only (e.g., latency, token count metrics, chunk counts, sanitized error categories).

---

## 6. Authentication & Secret Management
- **No API Keys in SDK**: No hardcoded API keys, secrets, or fallback credentials may exist in the SDK code, resources, or configuration.
- **Host-Supplied Auth**: All credential acquisition, token refresh, and authentication headers must be resolved through a host-supplied `AuthProvider` callback/interface.

---

## 7. Storage & Data Protection
- **Encryption at Rest**: All persistent data (retrieval indexes, SQLite databases, document caches, vector stores, preferences) must be encrypted.
- **App-Private Storage**: All stored files must reside exclusively in app-private storage (`context.noBackupFilesDir`, `context.filesDir`, or protected internal directories) and never on shared/external storage.

---

## 8. Google AI API Usage & ADR Tracking
- **Documentation Verification**: Before introducing, upgrading, or modifying any Google AI API integration (ML Kit GenAI, AICore, MediaPipe LLM Inference, LiteRT, LiteRT-LM, EmbeddingGemma), browse the current official documentation.
- **Decision Records**: Record the exact API version, target hardware requirements, constraints, and API surface in an Architectural Decision Record (`docs/decisions/ADR-xxx.md`).

---

## 9. Testing & Architectural Decision Records
- **Unit Testing**: Every new capability, parser, engine, and business logic flow must ship with thorough unit tests.
- **ADR Requirement**: Any non-trivial architectural or design decision (library choices, chunking strategies, schema designs, concurrency patterns) requires a documented ADR in `docs/decisions/`.

---

## 10. Commit & VCS Hygiene
- **Conventional Commits**: Adhere to Conventional Commits format (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- **Diff Quality**: Keep pull requests and diffs small, focused, and easily reviewable.

---

## 11. Definition of Done (DoD)
A change is considered complete only when:
1. The project builds successfully.
2. `./gradlew check` passes completely.
3. Code hygiene tools pass without warnings/errors (`detekt`, `ktlint`).
4. Unit tests are added/updated and passing.
5. All public APIs have complete KDoc and binary compatibility checks pass.
6. Relevant ADRs in `docs/decisions/` are created or updated.
