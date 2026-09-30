package com.ragchat.llm.local.runtime

import com.ragchat.api.llm.LlmProvider

/**
 * Service Provider Interface (SPI) extension for plugging in third-party on-device inference runtimes.
 *
 * Supported target engines:
 * - **llama.cpp**: High-performance C++ inference executing GGUF-quantized models (Q4_K_M, Q8_0)
 *   via Android NDK / JNI bindings.
 * - **ONNX Runtime GenAI**: Cross-platform inference engine supporting Qualcomm QNN NPU, DirectML,
 *   and Android NNAPI execution providers.
 * - **ExecuTorch**: Meta's lightweight PyTorch-native edge runtime supporting mobile GPUs and NPUs.
 *
 * Implementations should handle runtime library loading (e.g. `System.loadLibrary`), memory budget
 * enforcement, context caching, and thread pool configuration.
 */
public interface ExtraRuntimeProvider : LlmProvider {
    /**
     * Identifies the underlying runtime engine (e.g. "llama.cpp", "onnxruntime-genai", "executorch").
     */
    public val runtimeName: String

    /**
     * Absolute filesystem path to the compiled model asset or directory.
     */
    public val modelPath: String

    /**
     * Thread count allocated to CPU execution kernels.
     */
    public val numThreads: Int

    /**
     * Safely unloads model weights and scratch memory buffers from native heap.
     */
    public fun unload()
}
