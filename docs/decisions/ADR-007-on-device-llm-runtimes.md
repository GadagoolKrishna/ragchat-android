# ADR-007: On-Device (Gemini Nano & LiteRT-LM) and Enterprise Cloud LLM Runtimes

## Status
Accepted

## Date
2026-09-30

## Context
RagChat Android SDK requires a pluggable Large Language Model (LLM) abstraction (`LlmProvider` SPI in `:sdk-api`) capable of executing on-device foundation models (offline, zero-latency, private) as well as connecting to enterprise-managed cloud model endpoints.

The requirements encompass:
1. **On-Device Foundation Model (Gemini Nano)**:
   - System-managed foundation model via Android AICore / ML Kit GenAI.
   - Dynamic availability detection (device compatibility, model download state, foreground requirements, AICore system quota limits).
   - Clean fallback signals when local generation is constrained or unsupported.
2. **On-Device Open Weights Model (Gemma via LiteRT-LM)**:
   - Open-weights inference for Gemma (Gemma 2, Gemma 3/3n) loaded from application-managed storage (`ModelManager`).
   - Transition evaluation between legacy MediaPipe LLM Inference (`tasks-genai`) vs. modern LiteRT-LM (`com.google.ai.edge.litert`).
   - Hardware acceleration hierarchy (GPU via OpenCL/Vulkan with fallback to multithreaded CPU).
   - Dynamic memory management: proactive unloading during system `onTrimMemory` events.
3. **Pluggable Architecture for Extra Mobile Runtimes**:
   - Provide an extensible SPI stub (`ExtraRuntimeProvider`) detailing integration points for external runtimes like `llama.cpp` (GGUF), `ONNX Runtime GenAI`, and `ExecuTorch`.
4. **Enterprise Cloud Endpoints**:
   - Google Generative AI (Gemini Developer API) and Google Cloud Vertex AI (`streamGenerateContent`).
   - OpenAI-compatible endpoints (OpenAI GPT-4o, Azure OpenAI, LiteLLM, vLLM, self-hosted gateways).
   - Anthropic Claude Messages API (`/v1/messages`).
   - Resilient networking: TLS 1.2+, optional certificate pinning, mTLS support, Server-Sent Events (SSE) streaming, configurable timeouts, request/response payload size caps, dynamic token retrieval from `AuthProvider` (never hardcoded, never stored in persistent memory), and strict zero-PII redaction in logs.

---

## Evaluation & Decision

### 1. Gemini Nano: AICore System Service & ML Kit GenAI
- **Architecture**: In modern Android (Pixel 8/9+, Samsung S24+, and recent flagship devices), Gemini Nano is managed by **Android AICore** (`com.google.android.aicore`), a system APK that receives background updates via Google Play System Updates.
- **Client Access**: The SDK accesses Gemini Nano via the ML Kit GenAI client layer.
- **Availability State Machine**:
  - `AVAILABLE`: AICore service is active, device hardware (NPU/TPU) is certified, and the model weights are resident on disk.
  - `DOWNLOAD_REQUIRED`: AICore is present, but model weights must be downloaded via Google Play services.
  - `HARDWARE_UNSUPPORTED`: Device lacks certified NPU/accelerator or runs an unsupported Android OS version (requires Android 14+ / API 34+ for full hardware bindings).
  - `UNAVAILABLE`: Device bootloader is unlocked, quota exceeded, or app is backgrounded when foreground execution is enforced by policy.
- **Fallback Signals**: When `availability()` returns non-available or runtime quota is exceeded, the orchestrator receives an explicit signal enabling instant fallback to an alternate local model (Gemma) or an authorized cloud endpoint.

### 2. Gemma Runtime: LiteRT-LM vs. MediaPipe LLM Inference
- **Evaluation**:
  - *MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai`)*: Introduced early for Gemma experimentation, but Google AI Edge officially transitioned this library into maintenance mode in late 2024 / 2025.
  - *LiteRT-LM (`com.google.ai.edge.litert` / `org.tensorflow:tensorflow-lite`)*: Google's primary C++/Kotlin runtime under the rebranded LiteRT suite for on-device generative AI. Provides stateful KV-cache management, speculative decoding, LoRA adapters, and direct GPU/NPU acceleration.
- **Decision**: Adopt **LiteRT-LM** as the standard runtime for Gemma on Android.
- **Model Storage**: Model weights are packaged as `.litertlm` / `.task` files stored in app-private storage (`context.noBackupFilesDir/models/`) and verified with SHA-256 before instantiation.
- **Hardware Backend**:
  - GPU backend is prioritized for high token generation rates (OpenCL / Vulkan).
  - Multithreaded CPU backend serves as fallback if GPU context creation fails (e.g., in emulators or low-tier devices).
- **Lifecycle & Memory**: Implements `LocalLlmMemoryManager` hooked into Android's `ComponentCallbacks2.onTrimMemory`. When `TRIM_MEMORY_RUNNING_CRITICAL` or `TRIM_MEMORY_COMPLETE` occurs, active sessions are safely torn down to prevent system OOM crashes.

### 3. Extra Runtimes Extensibility (SPI)
- The SDK exposes `ExtraRuntimeProvider`, an abstract extension of `LlmProvider` allowing host applications to plug in custom engines:
  - `llama.cpp` JNI bindings for quantized GGUF models.
  - `ONNX Runtime GenAI` for multi-vendor DirectML/QNN accelerated graphs.
  - `ExecuTorch` for PyTorch-native edge models.

### 4. Cloud Networking & Enterprise Security
- **HTTP Engine**: Square OkHttp (`4.12.0`) with `okhttp-sse` for streaming Server-Sent Events.
- **Security & Privacy**:
  - TLS 1.2+ minimum, with support for enterprise Certificate Pinning (`CertificatePinner`) and mutual TLS (`SSLSocketFactory` + custom `KeyManager`).
  - Auth tokens are fetched strictly on-demand per request from the host-supplied `AuthProvider` and are never logged, cached, or written to disk.
  - Payloads are strictly sanitized: prompts, user messages, generated tokens, and embeddings are completely redacted from logs. Only sanitized metadata (latency, status codes, token count estimates) is emitted through `RagChatLogger`.
  - Request and response caps prevent denial-of-service via oversized streaming frames.

---

## Consequences

### Positive
- Unified, consistent streaming API (`Flow<LlmEvent>`) across local hardware and diverse cloud providers.
- Strict isolation of sensitive data: zero PII in logs, zero hardcoded secrets, and app-private model storage.
- Safe degraded operation: automatic hardware fallback (GPU -> CPU) and runtime unloading on low memory.
- Standardized contract test suite guarantees behavior parity across all providers.

### Neutral / Trade-offs
- On-device local models require significant storage space (Gemma 2B/3B typically requires 1.5 GB - 2.5 GB depending on INT4/INT8 quantization).
- Gemini Nano execution depends on Google Play Services and device OEM hardware qualification.
