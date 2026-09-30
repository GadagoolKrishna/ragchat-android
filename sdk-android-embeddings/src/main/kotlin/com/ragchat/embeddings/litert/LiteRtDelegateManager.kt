package com.ragchat.embeddings.litert

import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * Manages runtime hardware acceleration delegates (GPU, NNAPI, CPU fallback) for LiteRT inference.
 *
 * Evaluates hardware capabilities, initializes acceleration delegates, and falls back to
 * multithreaded CPU execution gracefully without crashing if hardware delegates are unsupported.
 */
public class LiteRtDelegateManager(
    private val logger: RagChatLogger? = null,
) : AutoCloseable {
    private val activeDelegates = mutableListOf<Delegate>()

    /**
     * Delegate tier selected for runtime execution.
     */
    public enum class DelegateTier {
        GPU,
        NNAPI,
        CPU,
    }

    /**
     * Builds [Interpreter.Options] configured with the best available hardware accelerator.
     *
     * @param preferredTier Optional preference override.
     * @param threadCount Number of worker threads if falling back to CPU.
     * @return Configured [Interpreter.Options] and the resolved [DelegateTier].
     */
    public fun createOptions(
        preferredTier: DelegateTier? = null,
        threadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
    ): Pair<Interpreter.Options, DelegateTier> {
        val options = Interpreter.Options()
        var resolvedTier = DelegateTier.CPU

        if (preferredTier != DelegateTier.CPU) {
            resolvedTier = tryInitializeAcceleration(options, preferredTier)
        }

        if (resolvedTier == DelegateTier.CPU) {
            options.setNumThreads(threadCount)
            logger?.log(LogLevel.INFO, "LiteRtDelegateManager", "Using multithreaded CPU execution with $threadCount threads.")
        }

        return Pair(options, resolvedTier)
    }

    private fun tryInitializeAcceleration(
        options: Interpreter.Options,
        preferredTier: DelegateTier?,
    ): DelegateTier {
        var tier = DelegateTier.CPU
        if (preferredTier == null || preferredTier == DelegateTier.GPU) {
            tier = tryInitGpu(options)
        }
        if (tier == DelegateTier.CPU && (preferredTier == null || preferredTier == DelegateTier.NNAPI)) {
            tier = tryInitNnapi(options)
        }
        return tier
    }

    @Suppress("TooGenericExceptionCaught")
    private fun tryInitGpu(options: Interpreter.Options): DelegateTier =
        try {
            val gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
            activeDelegates.add(gpuDelegate)
            logger?.log(LogLevel.INFO, "LiteRtDelegateManager", "Initialized GPU acceleration delegate.")
            DelegateTier.GPU
        } catch (e: Throwable) {
            logger?.log(LogLevel.WARN, "LiteRtDelegateManager", "GPU init failed: ${e.javaClass.simpleName}")
            DelegateTier.CPU
        }

    @Suppress("TooGenericExceptionCaught")
    private fun tryInitNnapi(options: Interpreter.Options): DelegateTier =
        try {
            options.setUseNNAPI(true)
            logger?.log(LogLevel.INFO, "LiteRtDelegateManager", "Initialized NNAPI acceleration.")
            DelegateTier.NNAPI
        } catch (e: Throwable) {
            logger?.log(LogLevel.WARN, "LiteRtDelegateManager", "NNAPI init failed: ${e.javaClass.simpleName}")
            options.setUseNNAPI(false)
            DelegateTier.CPU
        }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        for (delegate in activeDelegates) {
            try {
                delegate.close()
            } catch (_: Throwable) {
                // Ignore cleanup failures
            }
        }
        activeDelegates.clear()
    }
}
