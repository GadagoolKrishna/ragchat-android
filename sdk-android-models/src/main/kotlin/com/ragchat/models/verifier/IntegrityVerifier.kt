package com.ragchat.models.verifier

import com.ragchat.api.error.SdkError
import com.ragchat.models.catalog.ModelEntry
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Validates cryptographic integrity of downloaded and staged model binaries.
 *
 * Enforces two levels of verification:
 * 1. SHA-256 checksum matching the catalog digest.
 * 2. ECDSA signature verification against a pinned public key.
 */
public class IntegrityVerifier(
    private val pinnedPublicKey: PublicKey? = null,
) {
    public companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

        /**
         * Helper to parse an X.509 encoded public key (DER or Base64-encoded string).
         */
        public fun parsePublicKey(
            base64DerKey: String,
            algorithm: String = "EC",
        ): PublicKey {
            val keyBytes = Base64.getDecoder().decode(base64DerKey.trim())
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(algorithm)
            return keyFactory.generatePublic(keySpec)
        }
    }

    /**
     * Verifies that [file] matches [entry.sha256] and has a valid signature.
     *
     * @param file The model binary to verify.
     * @param entry Catalog metadata record.
     * @throws SdkError.ValidationError if SHA-256 does not match or signature is invalid.
     */
    public fun verify(
        file: File,
        entry: ModelEntry,
    ) {
        if (!file.exists() || !file.isFile) {
            throw SdkError.ValidationError("file", "Model binary does not exist at ${file.absolutePath}")
        }
        verifyDigestAndSignature(file, entry)
    }

    private fun verifyDigestAndSignature(
        file: File,
        entry: ModelEntry,
    ) {
        val actualSha256 = computeSha256(file)
        if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
            throw SdkError.ValidationError("sha256", "Corrupted model: computed $actualSha256")
        }

        if (pinnedPublicKey != null) {
            val valid = entry.signature.isNotBlank() && verifySignature(entry, pinnedPublicKey)
            if (!valid) {
                throw SdkError.ValidationError("signature", "Signature check failed for ${entry.id}")
            }
        }
    }

    /**
     * Computes the SHA-256 hex string of [file] in a streaming fashion.
     */
    public fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val hexString = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private fun verifySignature(
        entry: ModelEntry,
        publicKey: PublicKey,
    ): Boolean =
        try {
            val sigBytes = Base64.getDecoder().decode(entry.signature.trim())
            val ecdsa = Signature.getInstance("SHA256withECDSA")
            ecdsa.initVerify(publicKey)
            ecdsa.update(entry.canonicalSignaturePayload())
            ecdsa.verify(sigBytes)
        } catch (_: Exception) {
            false
        }
}
