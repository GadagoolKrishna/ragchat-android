package com.ragchat.embeddings

import android.content.ComponentCallbacks2
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.embeddings.lifecycle.EmbeddingMemoryManager
import com.ragchat.embeddings.litert.LiteRtDelegateManager
import com.ragchat.embeddings.local.OnDeviceEmbeddingProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class LatencyBenchmarkAndMemoryTest {
    private fun createMinimalTfLiteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x18)
        buffer.put("TFL3".toByteArray())
        buffer.position(0)
        return buffer
    }

    @Test
    fun testLatencyBenchmarkAcrossTiers() =
        runBlocking {
            val tiers =
                listOf(
                    LiteRtDelegateManager.DelegateTier.CPU to 2, // Low tier: 2 threads
                    LiteRtDelegateManager.DelegateTier.CPU to 4, // Mid tier: 4 threads
                    LiteRtDelegateManager.DelegateTier.GPU to 4, // High tier: GPU
                )

            val batch =
                listOf(
                    "Quick latency verification passage one.",
                    "Quick latency verification passage two.",
                    "Quick latency verification passage three.",
                    "Quick latency verification passage four.",
                )

            println("=== LiteRT Embedding Latency Benchmark ===")
            for ((tier, threads) in tiers) {
                val provider =
                    OnDeviceEmbeddingProvider(
                        modelBuffer = createMinimalTfLiteBuffer(),
                        dimensions = 384,
                        nativeDimensions = 768,
                        preferredTier = tier,
                    )

                // Warm-up pass
                provider.warmUp()

                val start = System.nanoTime()
                val result = provider.embed(batch, EmbeddingTaskType.RETRIEVAL_DOCUMENT)
                val durationMs = (System.nanoTime() - start) / 1_000_000.0

                assertEquals(batch.size, result.size)
                val formatted = String.format(Locale.ROOT, "%.2f", durationMs)
                println("Tier: $tier (threads=$threads), Batch: ${batch.size}, Latency: $formatted ms")

                assertTrue("Batch embedding should complete in reasonable timeframe (< 1000 ms)", durationMs < 1000.0)
                provider.close()
            }
        }

    @Test
    fun testOnTrimMemoryRelease() =
        runBlocking {
            val provider =
                OnDeviceEmbeddingProvider(
                    modelBuffer = createMinimalTfLiteBuffer(),
                    dimensions = 256,
                    nativeDimensions = 768,
                )

            // Initialize interpreter by embedding
            val res1 = provider.embed(listOf("Memory test passage"), EmbeddingTaskType.RETRIEVAL_DOCUMENT)
            assertEquals(1, res1.size)

            val memoryManager = EmbeddingMemoryManager()
            memoryManager.registerListener(provider)

            // Trigger critical trim memory
            @Suppress("DEPRECATION")
            memoryManager.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

            // Verify provider re-initializes gracefully after eviction
            val res2 = provider.embed(listOf("Post-eviction memory test passage"), EmbeddingTaskType.RETRIEVAL_DOCUMENT)
            assertEquals(1, res2.size)

            provider.close()
        }
}
