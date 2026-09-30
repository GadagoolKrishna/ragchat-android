package com.ragchat.ingestion.tokenizer

/**
 * Interface estimating the number of tokens required to represent a given text string.
 */
public interface TokenEstimator {
    /**
     * Estimates the token count for [text].
     */
    public fun estimateTokens(text: String): Int
}

/**
 * Token estimator using character-to-token ratio heuristic with Indic script compensation.
 *
 * For Latin text, standard rule of thumb is ~4 characters per token.
 * For complex Brahmic/Indic scripts (Devanagari/Hindi, Kannada), grapheme clusters encode more
 * information per character, yielding ~1.5 to 2.5 characters per token.
 *
 * @param defaultRatio Default char-to-token ratio for Latin / general scripts (default 4.0).
 * @param indicRatio Char-to-token ratio for Indic Unicode blocks (default 2.0).
 */
public class CharacterRatioTokenEstimator(
    private val defaultRatio: Float = 4.0f,
    private val indicRatio: Float = 2.0f,
) : TokenEstimator {
    override fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0

        var indicChars = 0
        var otherChars = 0

        for (ch in text) {
            val code = ch.code
            // Devanagari: 0x0900 - 0x097F, Kannada: 0x0C80 - 0x0CFF
            if (code in 0x0900..0x097F || code in 0x0C80..0x0CFF) {
                indicChars++
            } else {
                otherChars++
            }
        }

        val estimated = (indicChars / indicRatio) + (otherChars / defaultRatio)
        return estimated.toInt().coerceAtLeast(1)
    }
}

/**
 * Token estimator based on whitespace word boundaries.
 *
 * Typically 1 word ~= 1.33 tokens.
 */
public class WordBoundaryTokenEstimator(
    private val wordsToTokensRatio: Float = 1.33f,
) : TokenEstimator {
    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val wordCount = text.trim().split(WHITESPACE_REGEX).size
        return (wordCount * wordsToTokensRatio).toInt().coerceAtLeast(1)
    }

    private companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
