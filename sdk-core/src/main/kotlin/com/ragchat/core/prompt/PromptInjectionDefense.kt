package com.ragchat.core.prompt

import java.util.UUID

/**
 * Defenses against prompt-injection and instruction-hijacking embedded in retrieved document chunks.
 *
 * Untrusted context chunks are wrapped in dynamic delimiters and sanitized to neutralize instruction-like patterns
 * and simulated tool invocations.
 */
public object PromptInjectionDefense {
    private val INJECTION_PATTERNS =
        listOf(
            Regex("(?i)\\bignore\\s+(all\\s+)?previous\\s+instructions?\\b"),
            Regex("(?i)\\bdisregard\\s+(all\\s+)?(prior|system|previous)\\s+(instructions?|prompts?)\\b"),
            Regex("(?i)\\bsystem\\s+override\\b"),
            Regex("(?i)\\byou\\s+are\\s+now\\s+in\\s+(developer|dan|jailbreak)\\s+mode\\b"),
            Regex("(?i)\\bdo\\s+anything\\s+now\\b"),
            Regex("(?i)\\bact\\s+as\\s+(an?\\s+)?unrestricted\\b"),
            Regex("(?i)\\bprint\\s+(the\\s+)?(system\\s+prompt|secret\\s+key|api\\s+key)\\b"),
            Regex("(?i)\\brepeat\\s+the\\s+words?\\s+above\\b"),
        )

    private val TOOL_CALL_PATTERNS =
        listOf(
            Regex("(?i)<\\s*tool_call\\s*>.*?</\\s*tool_call\\s*>", RegexOption.DOT_MATCHES_ALL),
            Regex("(?i)<\\s*tool_request\\s*>.*?</\\s*tool_request\\s*>", RegexOption.DOT_MATCHES_ALL),
            Regex("(?i)<\\s*function_call\\s*>.*?</\\s*function_call\\s*>", RegexOption.DOT_MATCHES_ALL),
            Regex("(?i)\\{\\s*\"call\"\\s*:\\s*\".*?\"\\s*,.*?\\}", RegexOption.DOT_MATCHES_ALL),
        )

    /**
     * Sanitizes raw chunk text by neutralizing instruction-like attack vectors and stripping tool call patterns.
     *
     * @param content Untrusted text from document chunk.
     * @return Sanitized text safe for inclusion in grounding context.
     */
    public fun sanitizeChunk(content: String): String {
        var sanitized = content
        // 1. Strip fake tool invocation markup
        for (pattern in TOOL_CALL_PATTERNS) {
            sanitized = sanitized.replace(pattern, "[UNTRUSTED_CONTENT_FILTERED]")
        }
        // 2. Neutralize injection commands
        for (pattern in INJECTION_PATTERNS) {
            sanitized = sanitized.replace(pattern, "[COMMAND_NEUTRALIZED]")
        }
        return sanitized
    }

    /**
     * Wraps sanitized chunk text into an isolated untrusted data boundary.
     *
     * @param chunkId Identifier of the chunk.
     * @param sanitizedContent Cleaned chunk text.
     * @param boundaryToken Nonce-based boundary token preventing delimiter breakout.
     * @return Delimited context block.
     */
    public fun wrapChunk(
        chunkId: String,
        sanitizedContent: String,
        boundaryToken: String = UUID.randomUUID().toString().take(8),
    ): String =
        buildString {
            append("<<<RETRIEVED_CHUNK id=\"$chunkId\" nonce=\"$boundaryToken\">>>\n")
            append(sanitizedContent)
            append("\n<<<END_RETRIEVED_CHUNK nonce=\"$boundaryToken\">>>")
        }
}
