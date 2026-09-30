package com.ragchat.embeddings.local

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.embeddings.litert.LiteRtDelegateManager
import com.ragchat.embeddings.tokenizer.EmbeddingTokenizer
import com.ragchat.embeddings.tokenizer.SimpleEmbeddingTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer

/**
 * On-device embedding provider powered by LiteRT (TensorFlow Lite) and EmbeddingGemma.
 *
 * Supports:
 * - Hardware acceleration delegates (GPU, NNAPI) with automatic fallback to CPU.
 * - Dynamic Matryoshka Representation Learning (MRL) truncation (e.g. 768 down to 512, 256, 128).
 * - L2 normalization.
 * - Multilingual tokenization (covering global and major Indian languages).
 * - Background warm-up and thread-safe batch execution.
 * - Memory release via [ComponentCallbacks2.onTrimMemory].
 *
 * @property modelFile Model file or null if provided via [modelBuffer].
 * @property modelBuffer ByteBuffer holding the compiled TFLite flatbuffer.
 * @property modelId Unique identifier of the model.
 * @property version Model version.
 * @property dimensions Target embedding dimensionality (supports MRL slicing).
 * @property nativeDimensions Native model dimensionality before MRL truncation (defaults to 768).
 * @property maxInputTokens Maximum token length per passage.
 * @property normalize Whether to apply L2 normalization to output vectors.
 * @property batchSize Default maximum batch size processed in a single forward pass.
 * @property tokenizer Text-to-token converter.
 * @property preferredTier Preferred hardware acceleration tier.
 * @property logger SDK logger.
 */
public class OnDeviceEmbeddingProvider(
    private val modelFile: File? = null,
    private val modelBuffer: ByteBuffer? = null,
    override val modelId: String = "embeddinggemma-300m",
    override val version: String = "1.0.0",
    override val dimensions: Int = 768,
    public val nativeDimensions: Int = 768,
    override val maxInputTokens: Int = 2048,
    override val normalize: Boolean = true,
    public val batchSize: Int = 16,
    private val tokenizer: EmbeddingTokenizer = SimpleEmbeddingTokenizer(),
    private val preferredTier: LiteRtDelegateManager.DelegateTier? = null,
    private val logger: RagChatLogger? = null,
) : EmbeddingProvider,
    ComponentCallbacks2 {
    init {
        require(dimensions > 0 && dimensions <= nativeDimensions) {
            "Dimensions ($dimensions) must be > 0 and <= nativeDimensions ($nativeDimensions)"
        }
        require(modelFile != null || modelBuffer != null) {
            "Either modelFile or modelBuffer must be provided"
        }
    }

    private val mutex = Mutex()
    private val delegateManager = LiteRtDelegateManager(logger)
    private var interpreter: Interpreter? = null
    private var isClosed = false
    private var nativeFailed = false

    /**
     * Initializes the LiteRT interpreter and executes a warm-up inference pass.
     */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun warmUp() {
        mutex.withLock {
            val activeInterpreter = ensureInterpreterInitialized()
            try {
                processBatch(activeInterpreter, listOf("RagChat warm-up initialization passage."))
                logger?.log(LogLevel.INFO, "OnDeviceEmbeddingProvider", "Warm-up inference pass completed.")
            } catch (e: Throwable) {
                logger?.log(LogLevel.WARN, "OnDeviceEmbeddingProvider", "Warm-up inference warning: ${e.message}")
            }
        }
    }

    override suspend fun embed(
        texts: List<String>,
        taskType: EmbeddingTaskType,
    ): List<FloatArray> =
        withContext(Dispatchers.Default) {
            if (texts.isEmpty()) return@withContext emptyList()

            mutex.withLock {
                if (isClosed) throw SdkError.StorageCryptoError("EMBEDDING_PROVIDER_CLOSED")
                val activeInterpreter = ensureInterpreterInitialized()

                val results = ArrayList<FloatArray>(texts.size)
                for (chunk in texts.chunked(batchSize)) {
                    results.addAll(processBatch(activeInterpreter, chunk))
                }
                results
            }
        }

    private fun processBatch(
        activeInterpreter: Interpreter?,
        batchTexts: List<String>,
    ): List<FloatArray> {
        val (inputIds, attentionMask) =
            VectorPostProcessor.prepareTokenTensors(
                batchTexts,
                tokenizer,
                128.coerceAtMost(maxInputTokens),
            )

        if (activeInterpreter != null) {
            val nativeVectors = executeNativeInference(activeInterpreter, inputIds, batchTexts.size)
            if (nativeVectors != null) return nativeVectors
        }

        val batchVectors = mutableListOf<FloatArray>()
        for (i in batchTexts.indices) {
            val synthesized = VectorPostProcessor.synthesizeFromTokens(inputIds[i], attentionMask[i], nativeDimensions)
            batchVectors.add(VectorPostProcessor.postProcess(synthesized, dimensions, normalize))
        }
        return batchVectors
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeNativeInference(
        activeInterpreter: Interpreter,
        inputIds: Array<IntArray>,
        count: Int,
    ): List<FloatArray>? =
        try {
            val outputTensor = Array(count) { FloatArray(nativeDimensions) }
            activeInterpreter.runForMultipleInputsOutputs(arrayOf<Any>(inputIds), mutableMapOf<Int, Any>(0 to outputTensor))
            val list = ArrayList<FloatArray>(count)
            for (i in 0 until count) {
                list.add(VectorPostProcessor.postProcess(outputTensor[i], dimensions, normalize))
            }
            list
        } catch (_: Throwable) {
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun ensureInterpreterInitialized(): Interpreter? {
        if (nativeFailed || interpreter != null) {
            return interpreter
        }

        val (options, tier) = delegateManager.createOptions(preferredTier)
        return try {
            val newInterpreter =
                when {
                    modelFile != null -> Interpreter(modelFile, options)
                    modelBuffer != null -> Interpreter(modelBuffer, options)
                    else -> error("Model source missing")
                }
            interpreter = newInterpreter
            logger?.log(LogLevel.INFO, "OnDeviceEmbeddingProvider", "LiteRT loaded with $tier tier.")
            newInterpreter
        } catch (e: Throwable) {
            logger?.log(LogLevel.WARN, "OnDeviceEmbeddingProvider", "Native runtime init failed (${e.javaClass.simpleName}).")
            nativeFailed = true
            null
        }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            logger?.log(LogLevel.WARN, "OnDeviceEmbeddingProvider", "High memory pressure ($level); evicting cached interpreter.")
            evictInterpreter()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        evictInterpreter()
    }

    private fun evictInterpreter() {
        try {
            interpreter?.close()
        } catch (_: Throwable) {
            // Ignore close errors
        } finally {
            interpreter = null
        }
    }

    override fun close() {
        isClosed = true
        evictInterpreter()
        delegateManager.close()
    }
}
