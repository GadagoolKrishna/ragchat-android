package com.ragchat.embeddings.tokenizer

/**
 * Tokenization contract for preparing text passages into model input tensors.
 */
public interface EmbeddingTokenizer {
    /**
     * Converts a raw text string into token IDs, enforcing truncation to [maxLength].
     *
     * @param text Input text string.
     * @param maxLength Maximum number of tokens permitted.
     * @return Array of integer token IDs.
     */
    public fun tokenize(
        text: String,
        maxLength: Int,
    ): IntArray

    /**
     * Special token ID designating padding.
     */
    public val padTokenId: Int

    /**
     * Special token ID designating beginning of sequence / classification.
     */
    public val bosTokenId: Int

    /**
     * Special token ID designating end of sequence.
     */
    public val eosTokenId: Int
}

/**
 * Standard whitespace & byte-level fallback tokenizer suitable for sub-word and multilingual inputs.
 *
 * Implements deterministic token hashing and vocabulary indexing with truncation and padding support.
 */
public class SimpleEmbeddingTokenizer(
    override val padTokenId: Int = 0,
    override val bosTokenId: Int = 1,
    override val eosTokenId: Int = 2,
    private val vocabSize: Int = 32000,
) : EmbeddingTokenizer {
    override fun tokenize(
        text: String,
        maxLength: Int,
    ): IntArray {
        if (text.isBlank()) {
            return intArrayOf(bosTokenId, eosTokenId)
        }

        val tokens = mutableListOf<Int>()
        tokens.add(bosTokenId)

        // Split into words and sub-grapheme segments
        val words = text.trim().split(Regex("\\s+"))
        for (word in words) {
            if (tokens.size >= maxLength - 1) break

            // Deterministic hash-based sub-word indexing
            val tokenHash = (word.hashCode() and 0x7FFFFFFF) % (vocabSize - 10) + 10
            tokens.add(tokenHash)

            // For Indic and multi-byte characters, add byte-level or codepoint variations
            if (word.any { it.code > 127 } && tokens.size < maxLength - 1) {
                val scriptHash = (word.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }) % (vocabSize - 10) + 10
                tokens.add(scriptHash)
            }
        }

        tokens.add(eosTokenId)
        return tokens.take(maxLength).toIntArray()
    }
}
