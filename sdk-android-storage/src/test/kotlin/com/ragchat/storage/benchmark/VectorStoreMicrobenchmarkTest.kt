package com.ragchat.storage.benchmark

import com.ragchat.api.model.DistanceMetric
import com.ragchat.storage.util.QuantizedVector
import com.ragchat.storage.util.QuantizedVectorMath
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Microbenchmark for insert throughput, query latency across 10k, 50k, and 100k chunks x 384 dims,
 * and Recall@10 versus brute-force float32 ground truth.
 */
class VectorStoreMicrobenchmarkTest {
    private val dimensions = 384
    private val random = Random(42)

    private fun generateRandomVector(dims: Int): FloatArray {
        var sumSq = 0.0
        val v =
            FloatArray(dims) {
                val f = random.nextFloat() * 2f - 1f
                sumSq += f * f
                f
            }
        val norm = kotlin.math.sqrt(sumSq).toFloat()
        for (i in 0 until dims) {
            v[i] /= norm
        }
        return v
    }

    private fun bruteForceTopK(
        query: FloatArray,
        dataset: List<Pair<String, FloatArray>>,
        topK: Int,
    ): List<String> {
        val scored =
            dataset.map { (id, vec) ->
                var dot = 0f
                for (i in query.indices) {
                    dot += query[i] * vec[i]
                }
                id to dot
            }
        return scored.sortedByDescending { it.second }.take(topK).map { it.first }
    }

    @Test
    fun benchmarkQuantizationAndScanScales() {
        println("==========================================================")
        println("RAGCHAT ON-DEVICE VECTOR RETRIEVAL BENCHMARK")
        println("Dimensions: $dimensions (e.g. text-embedding-3-small, EmbeddingGemma)")
        println("==========================================================")

        for (count in listOf(10_000, 50_000, 100_000)) {
            runEvaluationForScale(count)
        }
    }

    private fun runEvaluationForScale(count: Int) {
        println("\n--- Evaluating Dataset Scale: $count chunks ---")
        val rawVectors = ArrayList<Pair<String, FloatArray>>(count)
        for (i in 0 until count) {
            rawVectors.add("doc_$i" to generateRandomVector(dimensions))
        }

        val quantizedList = ArrayList<Pair<String, QuantizedVector>>(count)
        val insertTimeMs =
            measureTimeMillis {
                for (item in rawVectors) {
                    quantizedList.add(item.first to QuantizedVectorMath.quantize(item.second))
                }
            }
        val throughput = (count.toDouble() / (insertTimeMs / 1000.0)).toInt()
        println("Quantization + Ingestion Time: ${insertTimeMs}ms ($throughput vectors/sec)")

        val queryVec = generateRandomVector(dimensions)
        val queryQv = QuantizedVectorMath.quantize(queryVec)
        evaluateQueryLatencyAndRecall(count, rawVectors, quantizedList, queryVec, queryQv)
    }

    private fun evaluateQueryLatencyAndRecall(
        count: Int,
        rawVectors: List<Pair<String, FloatArray>>,
        quantizedList: List<Pair<String, QuantizedVector>>,
        queryVec: FloatArray,
        queryQv: QuantizedVector,
    ) {
        val iterations = 5
        var totalScanNanos = 0L
        for (i in 0 until iterations) {
            val nanos =
                measureNanoTime {
                    var bestSim = -Float.MAX_VALUE
                    for (item in quantizedList) {
                        val sim = QuantizedVectorMath.computeQuantizedSimilarity(queryQv, item.second, DistanceMetric.COSINE)
                        if (sim > bestSim) {
                            bestSim = sim
                        }
                    }
                }
            totalScanNanos += nanos
        }
        val avgScanMs = (totalScanNanos / iterations) / 1_000_000.0
        println("Int8 Flat Scan Query Latency: ${String.format(Locale.US, "%.2f", avgScanMs)} ms")
        assertTrue("Scan latency must be under 100ms for 100k chunks", avgScanMs < 100.0)

        verifyRecallAndMemory(count, rawVectors, quantizedList, queryVec, queryQv)
    }

    private fun verifyRecallAndMemory(
        count: Int,
        rawVectors: List<Pair<String, FloatArray>>,
        quantizedList: List<Pair<String, QuantizedVector>>,
        queryVec: FloatArray,
        queryQv: QuantizedVector,
    ) {
        val groundTruthTop10 = bruteForceTopK(queryVec, rawVectors, topK = 10).toSet()
        val candidatePool =
            quantizedList
                .map { it.first to QuantizedVectorMath.computeQuantizedSimilarity(queryQv, it.second, DistanceMetric.COSINE) }
                .sortedByDescending { it.second }
                .take(20)

        val rawMap = rawVectors.toMap()
        val rerankedTop10 =
            candidatePool
                .map { (id, _) ->
                    val rawVec = rawMap[id]!!
                    var dot = 0f
                    for (i in queryVec.indices) {
                        dot += queryVec[i] * rawVec[i]
                    }
                    id to dot
                }.sortedByDescending { it.second }
                .take(10)
                .map { it.first }
                .toSet()

        val matches = rerankedTop10.intersect(groundTruthTop10).size
        val recallAt10 = matches.toDouble() / 10.0
        println("Recall@10: ${String.format(Locale.US, "%.2f", recallAt10 * 100)}%")
        assertTrue("Recall@10 must be >= 0.95", recallAt10 >= 0.95)

        val memoryBytesPerVector = dimensions + 8
        val totalMemoryMb = (count.toLong() * memoryBytesPerVector) / (1024.0 * 1024.0)
        val floatMemoryMb = (count.toLong() * dimensions * 4) / (1024.0 * 1024.0)
        val memFormatted = String.format(Locale.US, "%.2f", totalMemoryMb)
        val floatMemFormatted = String.format(Locale.US, "%.2f", floatMemoryMb)
        println("Memory: $memFormatted MB (vs $floatMemFormatted MB Float32)")
    }
}
