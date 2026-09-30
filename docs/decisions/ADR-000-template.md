# ADR-000: [Short Title of Architectural Decision]

- **Status**: [Proposed | Accepted | Superseded | Deprecated]
- **Date**: YYYY-MM-DD
- **Author(s)**: [Author Name(s)]
- **PR / Issue**: [#000](https://github.com/org/repo/pull/000)
- **Supersedes**: [ADR-xxx] (if applicable)
- **Superseded By**: [ADR-xxx] (if applicable)

---

## Context and Problem Statement
Describe the context, technical problem, and business/runtime requirements motivating this decision. Include relevant constraints (e.g., Min SDK 26, pure Kotlin/JVM restrictions, on-device compute limits, APK size, memory thresholds).

---

## External Documentation & API Surface Verification
*(Required for Google AI APIs such as ML Kit GenAI, AICore, MediaPipe LLM Inference, LiteRT, LiteRT-LM, EmbeddingGemma)*

- **API / Library**: [e.g., MediaPipe LLM Inference / LiteRT / AICore]
- **Exact Version / Artifact**: `com.google.mediapipe:tasks-genai:x.y.z`
- **Documentation URL**: [Link to official documentation referenced]
- **Date Verified**: YYYY-MM-DD
- **API Surface Used**: List specific public classes/interfaces and methods introduced into the SDK.
- **Hardware / Device Constraints**: e.g., NPU/GPU requirements, RAM requirements, supported OS versions.

---

## Decision Drivers
- [Driver 1, e.g., On-device privacy and offline availability]
- [Driver 2, e.g., Performance / latency on mobile chipsets]
- [Driver 3, e.g., Strict separation of pure Kotlin/JVM modules vs Android adapters]
- [Driver 4, e.g., Memory and binary footprint impact]

---

## Considered Options
1. **Option 1**: [Description]
2. **Option 2**: [Description]
3. **Option 3**: [Description]

---

## Decision Outcome
Chosen option: **[Option X]**, because [concise rationale explaining trade-offs and alignment with drivers].

### Positive Consequences
- [Advantage 1]
- [Advantage 2]

### Negative Consequences / Trade-offs
- [Disadvantage / mitigation 1]
- [Disadvantage / mitigation 2]

---

## Pros and Cons of the Options

### Option 1: [Title]
- **Good**: [Argument]
- **Bad**: [Argument]

### Option 2: [Title]
- **Good**: [Argument]
- **Bad**: [Argument]

---

## Security, Privacy & Governance Impact
- **PII / Redaction Considerations**: [Verify zero prompt/document/PII logging]
- **Storage & Encryption**: [Specify encrypted storage handling]
- **Auth & Key Management**: [Confirm host-supplied AuthProvider without hardcoded keys]

---

## Compliance with Definition of Done (DoD)
- [ ] Unit tests planned/implemented covering key branches.
- [ ] Interface (SPI) isolated in `:sdk-api` or pure Kotlin module where applicable.
- [ ] Pure Kotlin module constraints verified (zero `android.*` imports if in core/retrieval).
- [ ] Public API documented with KDoc and checked via `binary-compatibility-validator`.
- [ ] Static analysis clean (`detekt`, `ktlint`).
