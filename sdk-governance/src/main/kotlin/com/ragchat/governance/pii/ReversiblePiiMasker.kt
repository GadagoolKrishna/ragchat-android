package com.ragchat.governance.pii

import com.ragchat.api.governance.PiiMaskResult
import com.ragchat.api.governance.PiiRedactor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reversible de-identification transformer that replaces identified PII spans with deterministic,
 * structured placeholders (e.g., `<PII:EMAIL:1>`, `<PII:AADHAAR:1>`) and supports local unmasking.
 */
public class ReversiblePiiMasker(
    private val detector: DefaultPiiDetector = DefaultPiiDetector(),
) : PiiRedactor {
    /**
     * One-way redaction implementing [PiiRedactor].
     */
    override fun redact(text: String): String {
        val result = kotlinx.coroutines.runBlocking { mask(text) }
        return result.maskedText
    }

    /**
     * Reversibly masks [text], returning the sanitized text and a surrogate token lookup table.
     */
    public suspend fun mask(text: String): PiiMaskResult {
        val entities = detector.detect(text)
        if (entities.isEmpty()) {
            return PiiMaskResult(maskedText = text, tokenMap = emptyMap(), detectedEntities = emptyList())
        }

        val tokenMap = mutableMapOf<String, String>()
        val counter = AtomicInteger(1)
        val sb = StringBuilder()
        var lastIdx = 0

        for (e in entities) {
            if (e.startOffset < lastIdx) continue // skip overlapping entities
            sb.append(text, lastIdx, e.startOffset)
            val token = "<PII:${e.type.name}:${counter.getAndIncrement()}>"
            sb.append(token)
            tokenMap[token] = e.value
            lastIdx = e.endOffset
        }
        sb.append(text, lastIdx, text.length)

        return PiiMaskResult(
            maskedText = sb.toString(),
            tokenMap = tokenMap,
            detectedEntities = entities,
        )
    }

    /**
     * Restores original PII entities in [text] using [tokenMap].
     */
    public fun unmask(
        text: String,
        tokenMap: Map<String, String>,
    ): String {
        if (tokenMap.isEmpty()) return text
        var restored = text
        for ((token, original) in tokenMap) {
            restored = restored.replace(token, original)
        }
        return restored
    }
}
