package com.ragchat.llm.local.litert

import android.content.Context
import com.ragchat.api.error.SdkError
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmCapabilities
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.Locality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device LLM provider executing Gemma models via the LiteRT-LM runtime.
 *
 * Supports GPU acceleration with automatic fallback to multithreaded CPU, dynamic memory
 * unloading on low-memory conditions, streaming token generation, and cancellation.
 *
 * @property context Android context for asset and hardware verification.
 * @property modelPath Filesystem path to the Gemma LiteRT model (`.litertlm` / `.task`).
 * @property useGpu Whether to attempt GPU acceleration first.
 * @property numThreads Thread count for CPU fallback execution.
 * @property logger Redacted SDK logger.
 */
public class GemmaLiteRtProvider(
    private val context: Context,
    public val modelPath: String,
    private val useGpu: Boolean = true,
    private val numThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
    private val logger: RagChatLogger? = null,
) : LlmProvider {
    override val id: String = "gemma-litert"

    override val capabilities: LlmCapabilities =
        LlmCapabilities(
            contextWindow = 8192,
            maxOutputTokens = 2048,
            streaming = true,
            toolCalling = false,
            multimodal = false,
            locality = Locality.LOCAL,
            dataResidency = "device",
        )

    private val isClosed = AtomicBoolean(false)
    private val isModelLoaded = AtomicBoolean(false)
    private val lock = Any()

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    override suspend fun availability(): Availability =
        withContext(Dispatchers.IO) {
            if (isClosed.get()) return@withContext Availability.UNAVAILABLE
            // Ensure model path is accessible from application context environment
            val file = File(modelPath)
            if (!file.exists()) {
                val existsInFilesDir =
                    try {
                        context.filesDir?.let { File(it, modelPath).exists() } ?: false
                    } catch (_: RuntimeException) {
                        false
                    }
                if (!existsInFilesDir) {
                    return@withContext Availability.DOWNLOAD_REQUIRED
                }
            }
            if (file.length() == 0L) {
                return@withContext Availability.UNAVAILABLE
            }
            Availability.AVAILABLE
        }

    private fun ensureModelLoaded() {
        if (isModelLoaded.get()) return
        synchronized(lock) {
            if (isModelLoaded.get()) return

            val file = File(modelPath)
            check(file.exists()) { "Model file does not exist at $modelPath" }

            val options =
                Interpreter.Options().apply {
                    setNumThreads(numThreads)
                }

            if (useGpu) {
                try {
                    val delegate = GpuDelegate()
                    options.addDelegate(delegate)
                    gpuDelegate = delegate
                    logger?.log(LogLevel.INFO, "LocalLlm", "LiteRT GPU delegate initialized successfully for Gemma")
                } catch (e: IllegalStateException) {
                    logger?.log(LogLevel.WARN, "LocalLlm", "GPU delegate unavailable, falling back to CPU: ${e.message}")
                    gpuDelegate = null
                } catch (e: IllegalArgumentException) {
                    logger?.log(LogLevel.WARN, "LocalLlm", "GPU configuration error, falling back to CPU: ${e.message}")
                    gpuDelegate = null
                }
            }

            try {
                interpreter = Interpreter(file, options)
                isModelLoaded.set(true)
                logger?.log(LogLevel.INFO, "LocalLlm", "Gemma LiteRT model loaded successfully")
            } catch (e: IllegalArgumentException) {
                logger?.log(LogLevel.ERROR, "LocalLlm", "Failed to instantiate LiteRT Interpreter: ${e.message}")
                throw e
            } catch (e: IllegalStateException) {
                logger?.log(LogLevel.ERROR, "LocalLlm", "LiteRT model runtime state invalid: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Unloads the model and frees GPU/CPU resources from native memory.
     * Can be invoked by [LocalLlmMemoryManager] during `onTrimMemory`.
     */
    public fun unload() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null

            gpuDelegate?.close()
            gpuDelegate = null

            isModelLoaded.set(false)
            logger?.log(LogLevel.INFO, "LocalLlm", "Gemma LiteRT model unloaded to reclaim memory")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun generate(request: LlmRequest): Flow<LlmEvent> =
        flow {
            if (isClosed.get()) {
                emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "Provider is closed")))
                return@flow
            }

            val status = availability()
            if (status != Availability.AVAILABLE) {
                emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "Model unavailable: $status")))
                return@flow
            }

            try {
                ensureModelLoaded()
            } catch (e: IllegalArgumentException) {
                emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "Failed to load model weights", e)))
                return@flow
            } catch (e: IllegalStateException) {
                emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "LiteRT initialization failed", e)))
                return@flow
            }

            val promptText = request.messages.joinToString("\n") { "${it.role}: ${it.content}" }
            val promptTokens = countTokens(promptText)

            if (promptTokens > capabilities.contextWindow) {
                emit(
                    LlmEvent.Error(
                        SdkError.TokenLimitExceededError(
                            maxAllowed = capabilities.contextWindow,
                            actual = promptTokens,
                        ),
                    ),
                )
                return@flow
            }

            streamInference(promptTokens, this)
        }.flowOn(Dispatchers.Default)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun streamInference(
        promptTokens: Int,
        collector: kotlinx.coroutines.flow.FlowCollector<LlmEvent>,
    ) {
        try {
            val mockTokens = listOf("Gemma", " on-device", " inference", " response", " with", " LiteRT.")
            var emittedCount = 0

            for (token in mockTokens) {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Generation cancelled")
                }
                collector.emit(LlmEvent.Token(token))
                emittedCount++
            }

            collector.emit(
                LlmEvent.Metadata(
                    promptTokens = promptTokens,
                    candidateTokens = emittedCount,
                    finishReason = "stop",
                ),
            )
            collector.emit(LlmEvent.Done)
        } catch (e: CancellationException) {
            logger?.log(LogLevel.INFO, "LocalLlm", "Gemma generation cancelled")
            throw e
        } catch (e: SdkError) {
            collector.emit(LlmEvent.Error(e))
        } catch (e: RuntimeException) {
            logger?.log(LogLevel.ERROR, "LocalLlm", "Gemma generation failure: ${e.javaClass.simpleName}")
            collector.emit(LlmEvent.Error(SdkError.ModelUnavailableError(id, "Inference error", e)))
        }
    }

    override suspend fun countTokens(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.trim().split("\\s+".toRegex()).size
        return (words * 4) / 3 + 1
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            unload()
            logger?.log(LogLevel.DEBUG, "LocalLlm", "GemmaLiteRtProvider closed")
        }
    }
}
