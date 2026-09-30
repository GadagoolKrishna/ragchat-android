package com.ragchat.eval.security

import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue

class ParserFuzzingTest {
    private fun safeParseTextStream(
        bytes: ByteArray,
        maxBytes: Int = 1_000_000,
    ): Result<String> =
        runCatching {
            if (bytes.size > maxBytes) {
                error("Payload exceeded maximum allowed byte size ($maxBytes bytes)")
            }
            val stream = ByteArrayInputStream(bytes)
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    @Test
    fun testCorruptedAndMalformedUtf8Streams() {
        val malformedBytes =
            byteArrayOf(
                0xC0.toByte(),
                0xAF.toByte(), // Overlong UTF-8
                0xED.toByte(),
                0xA0.toByte(),
                0x80.toByte(), // Surrogate half
                0xFE.toByte(),
                0xFF.toByte(), // Invalid bytes
                0x00.toByte(),
                0x01.toByte(), // Null bytes and control chars
            )

        val result = safeParseTextStream(malformedBytes)
        assertTrue(result.isSuccess, "Malformed UTF-8 should decode gracefully without crashing JVM")
    }

    @Test
    fun testOversizedPayloadRejection() {
        val oversized = ByteArray(2_000_000) { 0x41.toByte() } // 2MB stream
        val result = safeParseTextStream(oversized, maxBytes = 500_000)
        assertTrue(result.isFailure, "Oversized payloads must be rejected defensively")
    }

    @Test
    fun testZeroByteStream() {
        val emptyBytes = ByteArray(0)
        val result = safeParseTextStream(emptyBytes)
        assertTrue(result.isSuccess, "Empty stream should return empty string without error")
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }
}
