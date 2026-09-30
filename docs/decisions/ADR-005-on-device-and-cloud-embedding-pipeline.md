# ADR-005: On-Device (LiteRT / EmbeddingGemma) and Cloud Embedding Pipeline

## Status
Accepted

## Date
2026-09-30

## Context
RagChat Android SDK requires high-performance text embedding capabilities to generate dense vectors for retrieval and indexing. As an enterprise-grade on-device-first SDK, the embedding architecture must satisfy several strict requirements:
1. **On-Device Independence & Privacy**: Must run locally without internet connectivity or cloud telemetry when operating in offline/private mode.
2. **Multilingual Coverage**: Must support cross-lingual semantic search with first-class support for global languages and major Indian languages (Hindi, Bengali, Tamil, Telugu, Marathi, Kannada, etc.).
3. **Hardware Acceleration & Delegate Fallback**: Must leverage Android hardware accelerators (GPU via OpenCL/Vulkan or NNAPI/NPU) while seamlessly falling back to multithreaded CPU if delegates are unsupported on low-tier or emulator devices.
4. **Matryoshka Representation Learning (MRL)**: Must support dynamic dimensionality truncation (e.g. 768 down to 512, 256, or 128 dimensions) to optimize memory footprint and search latency in the vector database with negligible loss in retrieval precision.
5. **Memory Management**: Must support proactive warm-up, interpreter reuse, and memory eviction during system low-memory conditions (`ComponentCallbacks2.onTrimMemory`).
6. **Cloud Embedding Support**: Must support remote cloud providers (Gemini Developer API, Google Cloud Vertex AI `text-embedding-004`) when configured, backed by `AuthProvider` credentials and strictly bounded by governance policies (`PolicyProvider` blocking requests when data residency is `LOCAL_ONLY`).
7. **Model Governance & Upgrades**: Must maintain a strict `EmbeddingRegistry` to prevent mixing incompatible embedding models within a single collection and provide an automated re-embedding job API for upgrading collection vectors.

## Evaluation & Decision

### 1. On-Device Model: EmbeddingGemma (LiteRT)
- **Architecture**: `embeddinggemma-300m` developed by Google DeepMind (308 million parameters total: ~100M transformer layers and ~200M embedding table).
- **Footprint**: Under 200 MB memory footprint using INT8/INT4 Quantization-Aware Training (QAT).
- **Context Window**: 2,048 tokens.
- **Language Coverage**: Trained across 100+ languages including major Indian languages (Hindi, Bengali, Tamil, Telugu, Marathi, Kannada).
- **Dimensionality**: 768 native dimensions. Built natively with Matryoshka Representation Learning (MRL), allowing prefix truncation to 512, 256, or 128 dimensions with automatic re-normalization.
- **Licensing**: Permissive commercial use under the Google **Gemma Terms of Use** and Gemma Prohibited Use Policy.
- **Engine Runtime**: LiteRT (`org.tensorflow:tensorflow-lite:2.16.1` / `com.google.ai.edge.litert`).

### 2. Hardware Acceleration & Delegate Hierarchy
The SDK uses `LiteRtDelegateManager` to evaluate hardware capabilities at runtime:
1. **GPU Delegate**: Evaluated first using `GpuDelegate`. If initialization succeeds, the model offloads heavy matrix operations to the mobile GPU.
2. **NNAPI Delegate**: Evaluated if GPU is unavailable or fails, targeting hardware DSPs and NPUs.
3. **Multithreaded CPU Fallback**: If hardware delegates throw `IllegalArgumentException` or `UnsatisfiedLinkError` (e.g. in test suites, emulators, or low-tier devices), the provider automatically falls back to standard CPU execution configured with an optimal thread count based on `Runtime.getRuntime().availableProcessors()`.

### 3. Vector Post-Processing Pipeline
Raw logits output by the model pass through:
1. **Mean Pooling**: Aggregating token embeddings across non-padded sequence tokens with attention mask weighting.
2. **Matryoshka Truncation**: Slicing the prefix $[0 \dots D-1]$ where $D \in \{128, 256, 512, 768\}$.
3. **L2 Normalization**:
   $$\mathbf{v}_{\text{norm}} = \frac{\mathbf{v}}{\|\mathbf{v}\|_2} = \frac{\mathbf{v}}{\sqrt{\sum_{i=1}^D v_i^2}}$$
   Ensures dot product queries are mathematically equivalent to cosine similarity.

### 4. Cloud Embedding Provider & Governance
- Endpoints: Gemini Developer API (`gemini-embedding-001`) and Google Cloud Vertex AI (`text-embedding-004`).
- **Policy Enforcement**: Before issuing any HTTP request, `CloudEmbeddingProvider` evaluates the active `PolicyProvider`. If the tenant scope or data is classified as `LOCAL_ONLY` or policy denies exfiltration, the request is blocked and throws `SdkError.GovernancePolicyError`.
- **Zero API Keys**: All authentication headers (`Bearer ...`) are retrieved on-demand from the host-supplied `AuthProvider`.

### 5. Lifecycle & Memory Protection
- **Warm-Up**: Executes a synthetic inference pass during initialization to compile OpenCL kernels and avoid UI frame drops during the first user interaction.
- **Trim Memory**: Registers an `EmbeddingMemoryManager` listening to Android `onTrimMemory`. When memory pressure reaches `TRIM_MEMORY_RUNNING_CRITICAL` or `TRIM_MEMORY_COMPLETE`, the cached interpreter and scratch buffers are released.

## Consequences

### Positive
- High-accuracy semantic search with native Indian language and global multilingual support.
- Flexible vector dimensions (128 to 768) enabling host applications to trade off vector search speed and storage size against recall.
- Seamless fallback ensures stability on 100% of Android devices regardless of chipset or GPU drivers.
- Complete privacy protection through policy-gated cloud calls and zero hardcoded keys.

### Negative / Trade-Offs
- On-device model file (~150-200 MB) requires either initial download via `:sdk-android-models` or asset bundling.
- GPU delegate initialization can take 50-150 ms on older chipsets, necessitating background warm-up.
