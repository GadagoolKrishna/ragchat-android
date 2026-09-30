package com.ragchat.storage.crypto

import android.content.Context
import android.util.Base64
import com.ragchat.api.crypto.KeyProvider
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * Manages Envelope Encryption for per-collection and database Data Encryption Keys (DEKs).
 *
 * Uses a Key Encryption Key (KEK) provided by [keystoreProvider] or a host-supplied delegate [KeyProvider]
 * to encrypt ephemeral, securely generated 256-bit DEKs. Encrypted DEKs are persisted in app-private
 * secure storage.
 */
public class EnvelopeEncryptionManager(
    private val context: Context,
    private val keystoreProvider: AndroidKeystoreKeyProvider,
    private val hostKeyProvider: KeyProvider? = null,
) {
    private val secureRandom = SecureRandom()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves an existing Data Encryption Key (DEK) for the given [keyIdentifier], or generates
     * a new one and persists its encrypted ciphertext.
     *
     * @param keyIdentifier Unique identifier for the DEK (e.g., collection ID or database name).
     * @return 256-bit raw key material suitable for AES-GCM or SQLCipher.
     */
    public suspend fun getOrCreateDataKey(keyIdentifier: String): ByteArray {
        val encryptedBase64 = prefs.getString(keyIdentifier, null)
        if (encryptedBase64 != null) {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            return unwrapDataKey(encryptedBytes, keyIdentifier)
        }

        // Generate a cryptographically secure 256-bit DEK
        val newDek = ByteArray(DEK_SIZE_BYTES).apply { secureRandom.nextBytes(this) }
        val wrappedDek = wrapDataKey(newDek, keyIdentifier)
        val wrappedBase64 = Base64.encodeToString(wrappedDek, Base64.NO_WRAP)

        prefs.edit().putString(keyIdentifier, wrappedBase64).apply()
        return newDek
    }

    /**
     * Irrevocably destroys the DEK for [keyIdentifier], rendering any data encrypted with it
     * completely unrecoverable (crypto-shred).
     */
    public fun cryptoShredDataKey(keyIdentifier: String) {
        prefs.edit().remove(keyIdentifier).apply()
    }

    /**
     * Clears all wrapped keys stored in private preferences.
     */
    public fun clearAllKeys() {
        prefs.edit().clear().apply()
    }

    /**
     * Rotates the master key: generates [newMasterAlias], re-wraps all persisted DEKs
     * under the new master key, and securely destroys the old master key if requested.
     */
    public suspend fun rotateMasterKey(
        oldMasterAlias: String,
        newMasterAlias: String,
        destroyOldMaster: Boolean = true,
    ) {
        keystoreProvider.generateNewKey(newMasterAlias)
        val allKeys = prefs.all

        for ((keyIdentifier, value) in allKeys) {
            if (value is String) {
                val encryptedBytes = Base64.decode(value, Base64.NO_WRAP)
                // Decrypt with old master key
                val rawDek = keystoreProvider.decrypt(encryptedBytes, oldMasterAlias)
                // Re-encrypt with new master key
                val reWrappedBytes = keystoreProvider.encrypt(rawDek, newMasterAlias)
                prefs
                    .edit()
                    .putString(
                        keyIdentifier,
                        Base64.encodeToString(reWrappedBytes, Base64.NO_WRAP),
                    ).apply()
            }
        }

        if (destroyOldMaster) {
            keystoreProvider.cryptoShred(oldMasterAlias)
        }
    }

    private suspend fun wrapDataKey(
        dek: ByteArray,
        identifier: String,
    ): ByteArray =
        if (hostKeyProvider != null) {
            val hostKey = hostKeyProvider.getKeyMaterial(identifier)
            // Use host key as AES-256 key to wrap DEK
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(hostKey.copyOf(32), "AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            cipher.iv + cipher.doFinal(dek)
        } else {
            keystoreProvider.encrypt(dek)
        }

    private suspend fun unwrapDataKey(
        wrapped: ByteArray,
        identifier: String,
    ): ByteArray =
        if (hostKeyProvider != null) {
            val hostKey = hostKeyProvider.getKeyMaterial(identifier)
            val iv = wrapped.copyOfRange(0, 12)
            val ciphertext = wrapped.copyOfRange(12, wrapped.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(hostKey.copyOf(32), "AES")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } else {
            keystoreProvider.decrypt(wrapped)
        }

    public companion object {
        private const val PREFS_NAME = "ragchat_secure_prefs"
        public const val DATABASE_KEY_ALIAS: String = "ragchat_database_dek"
        private const val DEK_SIZE_BYTES = 32
    }
}
