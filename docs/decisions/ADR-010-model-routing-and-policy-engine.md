# ADR-010: Dynamic Model Routing, Declarative Policy Engine, and Circuit Breakers

## Context
RagChat Android SDK orchestrates retrieval-augmented generation across both on-device (local) LLMs (such as Gemini Nano, Gemma via LiteRT) and cloud LLMs (Gemini API, Vertex AI, Anthropic, OpenAI-compatible enterprise gateways).

Enterprise environments impose stringent compliance, privacy, cost, and reliability constraints:
1. **Data Exfiltration Prevention**: Data or retrieved chunks marked with sensitivity labels like `CONFIDENTIAL` or `RESTRICTED` must never be transmitted off-device to cloud providers.
2. **Workplace & Network Restrictions**: Enterprises require workspace isolation (e.g., HR or finance workspaces restricted to local processing) and network governance (blocking cellular roaming cloud traffic).
3. **Budget & Quota Controls**: Cloud inference incur API costs; daily token quotas must be enforced.
4. **Device Resource Awareness**: On-device LLM inference is resource-intensive. If battery is low or thermal throttling is severe, inference should adapt or offload to cloud (if policy permits).
5. **Reliability & Circuit Breaking**: If an LLM provider fails repeatedly (e.g. rate limits, timeout, crash), the SDK must trip a circuit breaker and seamlessly route traffic to allowed fallback providers.
6. **Auditability**: Every routing decision must record structured rationale (reason codes, constraints evaluated) in an immutable compliance audit sink without logging PII or raw prompts.

## Decision
We implement a decoupled, two-tier governance and routing architecture:
1. **`:sdk-governance` (Pure Kotlin/JVM)**: Declarative Policy Engine supporting hot-reloadable rules specified via JSON or a type-safe Kotlin DSL.
   - Enforces fail-closed semantics: any evaluation failure or ambiguous state rejects unauthorized actions (`PolicyDecision.Denied`).
   - Evaluates:
     - Confidential chunk data residency (`CONFIDENTIAL` chunks never leave device).
     - Workspace cloud restrictions (`disallowCloudForWorkspace`).
     - Roaming network barriers (`blockCloudWhenRoaming`).
     - Allowed provider whitelist and geographic cloud region constraints.
     - Daily cloud token limits tracked via `TokenUsageTracker`.
2. **`:sdk-core` (Pure Kotlin/JVM)**: `ModelRouter` and `CircuitBreaker`.
   - Gathers multi-dimensional inputs:
     - Requested `ModelRoutingMode` (`LOCAL_ONLY`, `CLOUD_ONLY`, `LOCAL_FIRST`, `CLOUD_FIRST`, `AUTO`).
     - Provider availability & capabilities (context window, max tokens).
     - `DeviceContext`: device tier (`LOW_END`, `MID_RANGE`, `HIGH_END`), battery state, thermal status, network connectivity, roaming.
     - `QueryComplexityHeuristic`: text length, reasoning indicator keywords ("compare", "synthesize", "explain why"), conversation depth.
     - Required context length (prompt + retrieved chunks + history).
     - Chunk sensitivity labels.
     - Policy engine permission checks.
     - Circuit breaker state for candidate providers.
   - Computes a deterministic `RoutingDecision` containing:
     - `selectedProvider: LlmProvider`
     - `reasonCodes: List<RoutingReasonCode>`
     - `isFallback: Boolean`
   - Logs decision telemetry to `AuditSink` (action: `"MODEL_ROUTED"`).
   - In `DefaultChatManager`, implements fallback chains with circuit breakers: if local inference fails at runtime, checks if policy permits cloud fallback, records reason `FALLBACK_ALLOWED`, and falls back to cloud.
   - Exposes execution locality directly on `Answer.locality` (`ON_DEVICE` vs `CLOUD`).

## Consequences
- **Security & Privacy**: Zero risk of data leakage; property tests assert that confidential labeled chunks cannot select cloud providers under any condition.
- **Resilience**: The system gracefully handles device thermal throttling, weak network connectivity, and provider outages via circuit breaker fallbacks.
- **Zero-PII Compliance**: Audit logging records reason codes, model IDs, and device metrics, but strictly redacts prompts and raw chunk contents.
- **Portability**: Pure Kotlin modules `:sdk-governance` and `:sdk-core` contain zero `android.*` dependencies.
