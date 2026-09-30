package com.ragchat.embeddings.local

import kotlin.math.sqrt

/**
 * Utility helper for tensor preparation, vector slicing (Matryoshka Representation Learning),
 * and L2 normalization.
 */
internal object VectorPostProcessor {
    fun prepareTokenTensors(
        texts: List<String>,
        tokenizer: com.ragchat.embeddings.tokenizer.EmbeddingTokenizer,
        seqLength: Int,
    ): Pair<Array<IntArray>, Array<FloatArray>> {
        val count = texts.size
        val inputIds = Array(count) { IntArray(seqLength) }
        val attentionMask = Array(count) { FloatArray(seqLength) }

        for (i in 0 until count) {
            populateRow(texts[i], tokenizer, seqLength, inputIds[i], attentionMask[i])
        }
        return Pair(inputIds, attentionMask)
    }

    private fun populateRow(
        text: String,
        tokenizer: com.ragchat.embeddings.tokenizer.EmbeddingTokenizer,
        seqLength: Int,
        idsRow: IntArray,
        maskRow: FloatArray,
    ) {
        val tokens = tokenizer.tokenize(text, seqLength)
        for (j in 0 until seqLength) {
            val token = if (j < tokens.size) tokens[j] else tokenizer.padTokenId
            idsRow[j] = token
            maskRow[j] = if (token != tokenizer.padTokenId) 1.0f else 0.0f
        }
    }

    fun postProcess(
        rawVector: FloatArray,
        dimensions: Int,
        normalize: Boolean,
    ): FloatArray {
        val sliced = if (dimensions == rawVector.size) rawVector.copyOf() else rawVector.copyOf(dimensions)
        if (!normalize) return sliced

        var sumSquares = 0.0
        for (v in sliced) {
            sumSquares += (v * v).toDouble()
        }

        val norm = sqrt(sumSquares).toFloat()
        if (norm > 1e-12f) {
            for (i in sliced.indices) {
                sliced[i] /= norm
            }
        }
        return sliced
    }

    fun synthesizeFromTokens(
        tokens: IntArray,
        mask: FloatArray,
        nativeDimensions: Int,
    ): FloatArray {
        val raw = FloatArray(nativeDimensions)
        var validTokens = 0
        for (idx in tokens.indices) {
            if (mask[idx] <= 0f) continue
            validTokens++
            val token = tokens[idx]
            var h = token.toLong() * 0x45d9f3bL
            for (d in 0 until nativeDimensions) {
                h = (h xor (h shr 16)) * 0x45d9f3bL
                h = (h xor (h shr 16))
                val floatVal = ((h and 0xFFFFL).toFloat() / 32768.0f) - 1.0f
                raw[d] += floatVal
            }
        }

        if (validTokens > 0) {
            for (d in 0 until nativeDimensions) {
                raw[d] /= validTokens.toFloat()
            }
        }
        return raw
    }
}
