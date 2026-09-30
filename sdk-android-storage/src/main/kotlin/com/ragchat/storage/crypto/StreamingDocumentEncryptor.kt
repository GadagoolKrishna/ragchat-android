package com.ragchat.storage.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles streaming encryption and decryption for raw document copies using AES-256-GCM.
 *
 * Guarantees zero plaintext temporary file writes by directly wrapping input/output streams
 * with standard cryptographic streams. The 12-byte initialization vector (IV) is prepended
 * to the destination stream upon encryption and extracted first upon decryption.
 */
public class StreamingDocumentEncryptor {
    private val secureRandom = SecureRandom()

    /**
     * Streams and encrypts plaintext from [source] into [destination] using [aesKey].
     * Prepends the 12-byte IV to the output.
     *
     * @param source Raw unencrypted input stream.
     * @param destination Target stream receiving encrypted ciphertext.
     * @param aesKey 256-bit AES key.
     * @return Number of raw plaintext bytes written before encryption.
     */
    public fun encryptStream(
        source: InputStream,
        destination: OutputStream,
        aesKey: ByteArray,
    ): Long {
        require(aesKey.size == KEY_SIZE_BYTES) { "AES key must be 32 bytes (256-bit)" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        destination.write(iv)

        val secretKey = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        var totalBytesRead = 0L
        CipherOutputStream(destination, cipher).use { cipherOut ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (source.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
            }
            cipherOut.flush()
        }
        return totalBytesRead
    }

    /**
     * Decrypts an encrypted stream from [source] into [destination] using [aesKey].
     * Reads the initial 12-byte IV from the input stream.
     *
     * @param source Encrypted input stream (prefixed with 12-byte IV).
     * @param destination Target stream receiving decrypted plaintext.
     * @param aesKey 256-bit AES key.
     * @return Number of plaintext bytes written.
     */
    public fun decryptStream(
        source: InputStream,
        destination: OutputStream,
        aesKey: ByteArray,
    ): Long {
        require(aesKey.size == KEY_SIZE_BYTES) { "AES key must be 32 bytes (256-bit)" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ivBytesRead = source.read(iv)
        require(ivBytesRead == GCM_IV_LENGTH_BYTES) { "Corrupt ciphertext stream: missing IV" }

        val secretKey = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        var totalBytesWritten = 0L
        CipherInputStream(source, cipher).use { cipherIn ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                destination.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
            }
            destination.flush()
        }
        return totalBytesWritten
    }

    public companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BYTES = 32
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val BUFFER_SIZE = 8192
    }
}
