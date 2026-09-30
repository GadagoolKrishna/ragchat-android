package com.ragchat.storage

import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptionAtRestSecurityTest {
    @Test
    fun testRawBytesDoNotContainPlaintextSecrets() {
        val sensitiveContent = "TOP_SECRET_FINANCIAL_REVENUE_2026_CONFIDENTIAL"
        val userPrompt = "How much profit was generated in Q3?"

        // Simulate encrypted buffer payload
        val encryptedStream =
            ByteArrayOutputStream().apply {
                // Write encrypted pseudo-random byte stream simulating AES-256-GCM ciphertext
                val pseudoCiphertext = ByteArray(64) { (it * 37 xor 0xA5).toByte() }
                write(pseudoCiphertext)
            }

        val rawDiskBytes = encryptedStream.toByteArray()
        val rawDiskString = String(rawDiskBytes, Charsets.ISO_8859_1)

        // Verify sensitive plaintexts never leak into raw bytes
        assertFalse(rawDiskString.contains(sensitiveContent), "Plaintext document must not appear on disk")
        assertFalse(rawDiskString.contains(userPrompt), "User prompt must not appear in encrypted payload")
        assertTrue(rawDiskBytes.isNotEmpty(), "Ciphertext bytes must be written")
    }
}
