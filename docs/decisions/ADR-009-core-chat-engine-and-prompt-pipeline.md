# ADR-009: Core Chat Engine, Prompt Pipeline, and Injection Defenses

## Status
Accepted

## Date
2026-09-30

## Context
The RagChat Android SDK requires an enterprise-grade, privacy-first, capability-aware chat orchestration engine (`:sdk-core`). The engine must coordinate:
1. Conversation history retrieval and query rewrite for anaphora resolution.
2. Compliance and policy evaluation before any retrieval or inference begins.
3. Hybrid dense-vector and FTS retrieval with access-control enforcement and confidence gating.
4. Capability-aware context assembly and model-family-specific prompt building.
5. Strict prompt-injection defense mechanisms neutralizing hostile instructions within retrieved chunks.
6. Execution routing across local (Gemini Nano, Gemma) and cloud (Gemini, Claude, OpenAI) LLMs.
7. Streaming generation with cancellation and structured output schema validation with auto-retry.
8. Post-processing citation mapping, PII redaction, and grounding enforcement ("I don't know" when evidence is insufficient).

All orchestration logic must be implemented in pure Kotlin/JVM (`:sdk-core` and `:sdk-api`), maintaining **zero `android.*` imports**, adhering to explicit API mode strict, and strictly avoiding any logging or leakage of raw prompts, chunk texts, or PII.

---

## Decision

### 1. SPI Additions in `:sdk-api`
To maintain the pluggable architecture and clean modular boundaries, the following public contracts are introduced in `:sdk-api`:
- **`ChatManager`**: Public interface for conversational queries:
  - `fun ask(query: String, options: ChatOptions = ChatOptions()): Flow<ChatEvent>`
  - `suspend fun clearHistory(workspaceId: String, sessionId: String)`
  - `suspend fun getHistory(workspaceId: String, sessionId: String): List<ChatMessage>`
- **`ChatOptions`**:
  - `workspaceId: String`, `sessionId: String`
  - `collectionId: String`
  - `topK: Int?`
  - `confidenceThreshold: Float?`
  - `temperature: Float?`
  - `maxTokens: Int?`
  - `jsonSchema: String?` (Structured output mode)
  - `maxStructuredRetries: Int` (Default 2)
  - `routingMode: ModelRoutingMode?`
  - `aclFilter: Map<String, String>?`
- **`ChatEvent`**: Sealed interface representing streaming stages and tokens:
  - `QueryRewritten(val original: String, val rewritten: String)`
  - `RetrievalComplete(val chunkCount: Int)`
  - `Token(val text: String)`
  - `Citation(val citation: com.ragchat.api.model.Citation)`
  - `Metadata(val promptTokens: Int, val candidateTokens: Int, val finishReason: String?)`
  - `Done(val answer: com.ragchat.api.model.Answer)`
  - `Error(val error: com.ragchat.api.error.SdkError)`
- **`ChatHistoryStore`**: SPI for persisting conversation messages per `(workspaceId, sessionId)`:
  - `suspend fun getMessages(workspaceId: String, sessionId: String): List<ChatMessage>`
  - `suspend fun addMessage(workspaceId: String, sessionId: String, message: ChatMessage)`
  - `suspend fun clearHistory(workspaceId: String, sessionId: String)`

### 2. Multi-turn Memory & Rolling Summarization
- Scoped strictly by `(workspaceId, sessionId)`.
- When conversation history exceeds the model's memory budget (e.g. 50% of context window or 1,500 tokens), older messages are summarized using the configured `LlmProvider` and stored as a rolling summary prefix in the chat history.
- An in-memory concurrent implementation (`InMemoryChatHistoryStore`) is provided in `:sdk-core`, with pluggability for encrypted database storage in `:sdk-android-storage`.

### 3. Capability-Aware Prompt Construction & Model Families
- `PromptBuilder` formats inputs based on the target model family:
  - **Gemini (`GEMINI`)**: Structured user/model turns, separate system instruction parameter.
  - **Gemma (`GEMMA`)**: Special `<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n`.
  - **Claude (`CLAUDE`)**: Strict XML tagged document context `<documents><document id="...">...</documents>`.
  - **OpenAI (`OPENAI`)**: System role with context, standard assistant/user alternating turns.
- Capability awareness dynamically calculates available token budget for context:
  $$\text{Budget}_{\text{context}} = \text{ContextWindow} - \text{MaxOutputTokens} - \text{HistoryTokens} - \text{SystemTokens} - \text{SafetyMargin}$$
- If context window is tight (<4k tokens like Gemini Nano / Gemma 2B), neighbor expansion window is reduced to 0 and context chunks are packed aggressively.

### 4. Prompt-Injection Defenses & Untrusted Data Isolation
Retrieved documents are untrusted by definition and must never be able to hijack the model instruction stream or invoke tools.
- **Untrusted Context Isolation**: All retrieved chunks are wrapped within isolated delimiters with random session boundaries (e.g., `<<<UNTRUSTED_RETRIEVED_CONTEXT_BOUNDARY_${nonce}>>>` or XML tags).
- **Instruction Neutralization**: Active filtering and neutralizing of prompt injection vectors:
  - Neutralizes common hijacking phrases: "Ignore previous instructions", "Disregard system prompts", "System override", "You are now in developer mode", etc.
  - Neutralizes tool-invocation markers (e.g., `<tool_call>`, `function_call`, `json_rpc`) from chunk content so LLM parsing engines cannot mistake retrieved text for tool requests.
- **Strict Grounding Directive**: System instructions explicitly mandate:
  1. Answer only using evidence inside the untrusted context delimiters.
  2. For every assertion, append `[chunk_id]`.
  3. If evidence is insufficient, reply with a standardized fallback: "I do not have enough information to answer this question based on the provided documents."

### 5. Post-Processing & Citation Builder
- Post-processing extracts all citations `[chunk_id]` from generated text.
- Maps each citation back to the source chunk's metadata: `documentId`, `chunkId`, `pageNumber`, `score`, and text snippet.
- Runs `PiiRedactor` on generated output to guarantee that any PII that may have been generated is masked.

### 6. Structured Output Mode (JSON Schema) & Retry Loop
- When `jsonSchema` is specified in `ChatOptions`:
  - Prompt instructions require valid JSON matching the schema with zero markdown wrapper codeblocks.
  - The generated output is parsed and validated against the JSON schema.
  - If validation fails, the engine retries up to `maxStructuredRetries` times, passing the validation error back to the model for correction.

---

## Consequences

### Positive
- Strict isolation of untrusted documents prevents prompt injection and tool hijacking.
- Capability-aware budgeting prevents out-of-memory or context-window overflow on on-device LLMs.
- Zero `android.*` dependencies enables high-speed local JVM testing of complex conversational flows.
- Guarantees zero PII logging throughout the entire execution pipeline.

### Negative / Trade-offs
- Instruction neutralization and delimiter wrapping introduces minor token overhead (~50 tokens per prompt).
- Rolling summarization requires an additional LLM generation call when message history grows large.
