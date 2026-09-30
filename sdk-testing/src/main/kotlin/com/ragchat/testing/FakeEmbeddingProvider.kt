package com.ragchat.testing

import com.ragchat.api.embedding.EmbeddingProvider
import com.ragchat.api.embedding.EmbeddingTaskType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Test fake implementation of [EmbeddingProvider] generating deterministic embeddings.
 */
public class FakeEmbeddingProvider(
    override val modelId: String = "fake-embedding-model",
    override val version: String = "1.0.0",
    override val dimensions: Int = 16,
    override val maxInputTokens: Int = 512,
    override val normalize: Boolean = true,
) : EmbeddingProvider {
    private val isClosed = AtomicBoolean(false)

    override suspend fun embed(
        texts: List<String>,
        taskType: EmbeddingTaskType,
    ): List<FloatArray> {
        check(!isClosed.get()) { "FakeEmbeddingProvider is closed" }
        return texts.map { text ->
            val hash = text.hashCode()
            val vec =
                FloatArray(dimensions) { i ->
                    ((hash * (i + 1)) % 100).toFloat() / 100f
                }
            if (normalize) normalizeVector(vec) else vec
        }
    }

    private fun normalizeVector(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm == 0.0f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    override fun close() {
        isClosed.set(true)
    }
}
