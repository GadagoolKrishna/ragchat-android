package com.ragchat.embeddings

import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.embeddings.local.OnDeviceEmbeddingProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class MultilingualAndDeterminismTest {
    private fun createMinimalTfLiteBuffer(): ByteBuffer {
        // Construct a minimal valid 16-byte aligned flatbuffer stub
        val buffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x18) // Root table offset
        buffer.put("TFL3".toByteArray()) // File identifier
        buffer.position(0)
        return buffer
    }

    private fun cosineSimilarity(
        v1: FloatArray,
        v2: FloatArray,
    ): Float {
        var dot = 0.0f
        var n1 = 0.0f
        var n2 = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        return dot / (sqrt(n1) * sqrt(n2))
    }

    @Test
    fun testDeterminism() =
        runBlocking {
            val provider =
                OnDeviceEmbeddingProvider(
                    modelBuffer = createMinimalTfLiteBuffer(),
                    dimensions = 256,
                    nativeDimensions = 768,
                )

            val input = "Enterprise on-device RAG architecture with privacy preservation."
            val pass1 = provider.embed(listOf(input), EmbeddingTaskType.RETRIEVAL_DOCUMENT).first()
            val pass2 = provider.embed(listOf(input), EmbeddingTaskType.RETRIEVAL_DOCUMENT).first()

            assertEquals(256, pass1.size)
            assertEquals(256, pass2.size)

            for (i in pass1.indices) {
                assertEquals("Component $i must be identical", pass1[i], pass2[i], 1e-6f)
            }
        }

    @Test
    fun testMatryoshkaTruncationAndL2Norm() =
        runBlocking {
            // Test 128, 256, and 512 dimensions from native 768
            for (dims in listOf(128, 256, 512, 768)) {
                val provider =
                    OnDeviceEmbeddingProvider(
                        modelBuffer = createMinimalTfLiteBuffer(),
                        dimensions = dims,
                        nativeDimensions = 768,
                        normalize = true,
                    )

                val vector = provider.embed(listOf("Matryoshka slicing verification"), EmbeddingTaskType.RETRIEVAL_QUERY).first()
                assertEquals(dims, vector.size)

                var normSq = 0.0
                for (v in vector) {
                    normSq += (v * v).toDouble()
                }
                val norm = sqrt(normSq)
                assertEquals("Vector L2 norm must be 1.0", 1.0, norm, 1e-4)
            }
        }

    @Test
    fun testMultilingualSemanticPairSimilarity() =
        runBlocking {
            val provider =
                OnDeviceEmbeddingProvider(
                    modelBuffer = createMinimalTfLiteBuffer(),
                    dimensions = 512,
                    nativeDimensions = 768,
                )

            // Test pairs: English, Hindi (Devanagari), Kannada
            val english1 = "Artificial intelligence and machine learning technology."
            val english2 = "Machine learning and artificial intelligence systems."
            val dissimilar = "Cooking delicious food with vegetables and spices."

            val hindi1 = "कृत्रिम बुद्धिमत्ता और मशीन लर्निंग तकनीक।"
            val hindi2 = "मशीन लर्निंग और कृत्रिम बुद्धिमत्ता प्रणाली।"

            val kannada1 = "ಕೃತಕ ಬುದ್ಧಿಮತ್ತೆ ಮತ್ತು ಯಂತ್ರ ಕಲಿಕೆ ತಂತ್ರಜ್ಞಾನ."

            val texts = listOf(english1, english2, dissimilar, hindi1, hindi2, kannada1)
            val vectors = provider.embed(texts, EmbeddingTaskType.RETRIEVAL_DOCUMENT)

            val vEng1 = vectors[0]
            val vEng2 = vectors[1]
            val vDissimilar = vectors[2]
            val vHin1 = vectors[3]
            val vHin2 = vectors[4]

            val simEng = cosineSimilarity(vEng1, vEng2)
            val simDissimilar = cosineSimilarity(vEng1, vDissimilar)
            val simHindi = cosineSimilarity(vHin1, vHin2)

            val msg = "Semantically similar English texts should have higher similarity ($simEng vs $simDissimilar)"
            assertTrue(msg, simEng > simDissimilar)
            assertTrue("Semantically similar Hindi texts should have high similarity: $simHindi", simHindi > 0.5f)
        }
}
