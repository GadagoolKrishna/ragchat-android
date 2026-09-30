package com.ragchat.storage.util

import com.ragchat.api.model.DistanceMetric
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Representation of an int8 scalar-quantized dense vector.
 *
 * @property quantized Int8 quantized byte values.
 * @property scale Scaling factor to restore approximate float values.
 * @property norm Float Euclidean norm of the original vector.
 */
public data class QuantizedVector(
    val quantized: ByteArray,
    val scale: Float,
    val norm: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as QuantizedVector
        if (!quantized.contentEquals(other.quantized)) return false
        if (scale != other.scale) return false
        if (norm != other.norm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = quantized.contentHashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + norm.hashCode()
        return result
    }
}

/**
 * High-performance vector quantization and approximate similarity calculation utilities.
 */
public object QuantizedVectorMath {
    /**
     * Quantizes a float vector into an int8 representation using symmetric scalar quantization.
     */
    public fun quantize(vector: FloatArray): QuantizedVector {
        var maxAbs = 0f
        var sumSquares = 0f
        for (v in vector) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        val scale = if (maxAbs > 0f) maxAbs / 127f else 1f
        val invScale = if (scale > 0f) 1f / scale else 1f

        val quantized = ByteArray(vector.size)
        for (i in vector.indices) {
            val q = (vector[i] * invScale).roundToInt()
            quantized[i] = max(-127, minOf(127, q)).toByte()
        }

        return QuantizedVector(quantized = quantized, scale = scale, norm = norm)
    }

    /**
     * Serializes a [QuantizedVector] into a byte array for database storage.
     * Format: [scale: Float (4B)] + [norm: Float (4B)] + [quantized: ByteArray (NB)].
     */
    public fun toBlob(qv: QuantizedVector): ByteArray {
        val buffer = ByteBuffer.allocate(8 + qv.quantized.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(qv.scale)
        buffer.putFloat(qv.norm)
        buffer.put(qv.quantized)
        return buffer.array()
    }

    /**
     * Deserializes a [QuantizedVector] from its storage blob format.
     */
    public fun fromBlob(
        bytes: ByteArray,
        dimensions: Int,
    ): QuantizedVector {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val scale = buffer.getFloat()
        val norm = buffer.getFloat()
        val quantized = ByteArray(dimensions)
        buffer.get(quantized)
        return QuantizedVector(quantized = quantized, scale = scale, norm = norm)
    }

    /**
     * Calculates similarity between a pre-quantized candidate vector and a quantized query.
     */
    public fun computeQuantizedSimilarity(
        query: QuantizedVector,
        candidate: QuantizedVector,
        metric: DistanceMetric,
    ): Float {
        var intDot = 0
        val len = minOf(query.quantized.size, candidate.quantized.size)
        val q1 = query.quantized
        val q2 = candidate.quantized

        for (i in 0 until len) {
            intDot += q1[i] * q2[i]
        }

        val dot = intDot.toFloat() * query.scale * candidate.scale

        return when (metric) {
            DistanceMetric.DOT_PRODUCT -> dot
            DistanceMetric.COSINE -> {
                val denom = query.norm * candidate.norm
                if (denom > 0f) dot / denom else 0f
            }
            DistanceMetric.EUCLIDEAN -> {
                val distanceSquared = query.norm * query.norm + candidate.norm * candidate.norm - (2f * dot)
                val dist = sqrt(max(0f, distanceSquared))
                1f / (1f + dist)
            }
        }
    }
}
