package com.ragchat.storage.util

import com.ragchat.api.model.DistanceMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class QuantizedVectorMathTest {
    @Test
    fun testQuantizationRoundtripBlob() {
        val original = floatArrayOf(0.12f, -0.45f, 0.98f, -0.01f, 0.55f)
        val qv = QuantizedVectorMath.quantize(original)
        val blob = QuantizedVectorMath.toBlob(qv)
        val deserialized = QuantizedVectorMath.fromBlob(blob, original.size)

        assertEquals(qv.scale, deserialized.scale, 0.0001f)
        assertEquals(qv.norm, deserialized.norm, 0.0001f)
        for (i in original.indices) {
            assertEquals(qv.quantized[i], deserialized.quantized[i])
        }
    }

    @Test
    fun testCosineSimilarityHighCorrelationWithFloat() {
        val vecA = FloatArray(384) { (it % 10).toFloat() / 10f }
        val vecB = FloatArray(384) { ((it + 2) % 10).toFloat() / 10f }

        // Compute exact float cosine
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in 0 until 384) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        val exactCosine = dot / (sqrt(normA) * sqrt(normB))

        val qvA = QuantizedVectorMath.quantize(vecA)
        val qvB = QuantizedVectorMath.quantize(vecB)
        val approxCosine = QuantizedVectorMath.computeQuantizedSimilarity(qvA, qvB, DistanceMetric.COSINE)

        // Int8 quantization error is typically well under 0.02
        assertEquals(exactCosine, approxCosine, 0.02f)
    }

    @Test
    fun testHnswIndexSearchGraph() {
        val index = HnswIndex(dimensions = 16, metric = DistanceMetric.COSINE, randomSeed = 42L)
        val target = FloatArray(16) { 1.0f }
        val targetQv = QuantizedVectorMath.quantize(target)
        index.insert("target", targetQv)

        for (i in 1..20) {
            val other = FloatArray(16) { idx -> if (idx == (i % 15) + 1) 1.0f else -1.0f }
            index.insert("other_$i", QuantizedVectorMath.quantize(other))
        }

        val results = index.search(targetQv, topK = 5)
        assertTrue(results.isNotEmpty())
        assertEquals("target", results.first().first)
        assertTrue(results.first().second > 0.99f)
    }
}
