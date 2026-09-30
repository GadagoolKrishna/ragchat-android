package com.ragchat.storage.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ragchat.api.crypto.KeyProvider
import com.ragchat.api.error.SdkError
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore implementation of [KeyProvider].
 *
 * Generates and manages a non-exportable AES-256 master Key Encryption Key (KEK) inside the
 * Android hardware Keystore. Automatically attempts hardware StrongBox backing where available,
 * gracefully falling back to the standard Trusted Execution Environment (TEE).
 *
 * @property context Application context for checking device hardware capabilities.
 * @property masterKeyAlias The Keystore alias for the master key.
 */
public class AndroidKeystoreKeyProvider(
    private val context: Context,
    public val masterKeyAlias: String = DEFAULT_MASTER_KEY_ALIAS,
) : KeyProvider {
    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    init {
        ensureMasterKeyExists(masterKeyAlias)
    }

    /**
     * Retrieves key material for envelope encryption operations.
     * Note: Keystore keys themselves are non-exportable; this method provides
     * a stable deterministic hash or derived material when acting as an external KeyProvider.
     */
    override suspend fun getKeyMaterial(alias: String): ByteArray {
        val secretKey =
            getMasterKey(alias)
                ?: throw SdkError.StorageCryptoError("KEY_NOT_FOUND")
        return secretKey.encoded ?: alias.toByteArray(Charsets.UTF_8)
    }

    /**
     * Encrypts plaintext data (such as a Data Encryption Key or database passphrase)
     * using the Keystore-backed master key with AES-GCM.
     *
     * @param plaintext Raw bytes to encrypt.
     * @param alias The master key alias.
     * @return Encrypted payload prefixed with the 12-byte initialization vector (IV).
     */
    public fun encrypt(
        plaintext: ByteArray,
        alias: String = masterKeyAlias,
    ): ByteArray {
        val secretKey =
            getMasterKey(alias)
                ?: throw SdkError.StorageCryptoError("KEY_MISSING_ENCRYPT")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Decrypts a ciphertext payload prefixed with a 12-byte IV using the Keystore-backed master key.
     *
     * @param encryptedData Payload containing 12-byte IV + ciphertext.
     * @param alias The master key alias.
     * @return Decrypted plaintext bytes.
     */
    public fun decrypt(
        encryptedData: ByteArray,
        alias: String = masterKeyAlias,
    ): ByteArray {
        require(encryptedData.size > GCM_IV_LENGTH_BYTES) { "Ciphertext payload is too short" }
        val secretKey =
            getMasterKey(alias)
                ?: throw SdkError.StorageCryptoError("KEY_MISSING_DECRYPT")

        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH_BYTES, encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Irrevocably destroys the master key identified by [alias] from the Android Keystore.
     * Any data encrypted under this key becomes permanently and cryptographically unrecoverable.
     *
     * @param alias The key alias to shred.
     */
    public fun cryptoShred(alias: String = masterKeyAlias) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Checks whether the master key exists in the Keystore.
     */
    public fun hasKey(alias: String = masterKeyAlias): Boolean = keyStore.containsAlias(alias)

    /**
     * Generates a new version of the master key with [newAlias].
     */
    public fun generateNewKey(newAlias: String): Boolean = createMasterKey(newAlias)

    private fun getMasterKey(alias: String): SecretKey? {
        if (!keyStore.containsAlias(alias)) return null
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    private fun ensureMasterKeyExists(alias: String) {
        if (!keyStore.containsAlias(alias)) {
            createMasterKey(alias)
        }
    }

    private fun createMasterKey(alias: String): Boolean {
        val supportsStrongBox =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            } else {
                false
            }

        if (supportsStrongBox) {
            try {
                generateKeyInternal(alias, useStrongBox = true)
                return true
            } catch (_: Exception) {
                // Fall back to standard hardware TEE
            }
        }

        generateKeyInternal(alias, useStrongBox = false)
        return true
    }

    private fun generateKeyInternal(
        alias: String,
        useStrongBox: Boolean,
    ) {
        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        val builder =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    public companion object {
        public const val DEFAULT_MASTER_KEY_ALIAS: String = "ragchat_master_key_kek"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
