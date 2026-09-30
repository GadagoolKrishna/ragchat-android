package com.ragchat.storage.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class StreamingDocumentEncryptorTest {
    private val encryptor = StreamingDocumentEncryptor()
    private val secureRandom = SecureRandom()

    @Test
    fun testStreamingEncryptionAndDecryptionRoundTrip() {
        val testData = "Enterprise confidential document for on-device RAG retrieval.".toByteArray(Charsets.UTF_8)
        val key = ByteArray(32).apply { secureRandom.nextBytes(this) }

        val encryptedOut = ByteArrayOutputStream()
        val bytesEncrypted = encryptor.encryptStream(ByteArrayInputStream(testData), encryptedOut, key)
        assertEquals(testData.size.toLong(), bytesEncrypted)

        val ciphertextWithIv = encryptedOut.toByteArray()
        // Ensure ciphertext does not match plaintext
        assertFalse(testData.contentEquals(ciphertextWithIv))

        val decryptedOut = ByteArrayOutputStream()
        val bytesDecrypted = encryptor.decryptStream(ByteArrayInputStream(ciphertextWithIv), decryptedOut, key)
        assertEquals(testData.size.toLong(), bytesDecrypted)

        assertArrayEquals(testData, decryptedOut.toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidKeySizeThrows() {
        val testData = "Test".toByteArray()
        val invalidKey = ByteArray(16) // Needs 32 bytes for AES-256
        encryptor.encryptStream(ByteArrayInputStream(testData), ByteArrayOutputStream(), invalidKey)
    }
}
