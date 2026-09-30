package com.ragchat.storage.util

import com.ragchat.api.model.Chunk
import com.ragchat.api.model.DistanceMetric
import com.ragchat.api.storage.SearchResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

internal object VectorMath {
    fun computeSimilarity(
        a: FloatArray,
        b: FloatArray,
        metric: DistanceMetric,
    ): Float =
        when (metric) {
            DistanceMetric.COSINE -> cosineSimilarity(a, b)
            DistanceMetric.DOT_PRODUCT -> dotProduct(a, b)
            DistanceMetric.EUCLIDEAN -> euclideanDistanceScore(a, b)
        }

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun dotProduct(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var dot = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot
    }

    private fun euclideanDistanceScore(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var sumSquares = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = a[i] - b[i]
            sumSquares += diff * diff
        }
        val distance = sqrt(sumSquares)
        return 1f / (1f + distance)
    }

    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    fun byteArrayToFloatArray(
        bytes: ByteArray,
        dimensions: Int,
    ): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(dimensions)
        for (i in 0 until dimensions) {
            if (buffer.hasRemaining()) {
                floats[i] = buffer.getFloat()
            }
        }
        return floats
    }

    fun reciprocalRankFusion(
        vectorResults: List<SearchResult>,
        ftsResults: List<SearchResult>,
        topK: Int,
        k: Int = 60,
    ): List<SearchResult> {
        val scores = mutableMapOf<String, Float>()
        val chunkMap = mutableMapOf<String, Chunk>()

        vectorResults.forEachIndexed { rank, result ->
            val chunkId = result.chunk.id
            chunkMap[chunkId] = result.chunk
            val rrfScore = 1f / (k + rank + 1)
            scores[chunkId] = (scores[chunkId] ?: 0f) + rrfScore
        }

        ftsResults.forEachIndexed { rank, result ->
            val chunkId = result.chunk.id
            chunkMap[chunkId] = result.chunk
            val rrfScore = 1f / (k + rank + 1)
            scores[chunkId] = (scores[chunkId] ?: 0f) + rrfScore
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapNotNull { (chunkId, score) ->
                chunkMap[chunkId]?.let { SearchResult(chunk = it, score = score) }
            }
    }
}

internal object StorageJsonUtils {
    fun mapToJson(map: Map<String, String>): String =
        map.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${k.replace("\"", "\\\"")}\":\"${v.replace("\"", "\\\"")}\""
        }

    fun jsonToMap(json: String): Map<String, String> {
        val trimmed = json.trim().removeSurrounding("{", "}")
        if (trimmed.isEmpty()) return emptyMap()

        val map = mutableMapOf<String, String>()
        val pairs = trimmed.split(",")
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts[1].trim().removeSurrounding("\"")
                map[key] = value
            }
        }
        return map
    }

    fun sanitizeFtsQuery(query: String): String {
        val sanitized = query.replace("\"", "").replace("'", "").trim()
        return if (sanitized.isEmpty()) "\"\"" else "\"$sanitized\"*"
    }

    fun matchesFilter(
        metadata: Map<String, String>,
        filter: Map<String, String>,
    ): Boolean {
        for ((key, value) in filter) {
            if (metadata[key] != value) return false
        }
        return true
    }
}
