package com.ragchat.api.crypto

/**
 * Service Provider Interface (SPI) for providing encryption keys or passphrases to secure on-device stores.
 */
public interface KeyProvider {
    /**
     * Retrieves the encryption passphrase or key bytes associated with [alias].
     *
     * @param alias Identifier for the target keystore entry or database.
     * @return Cryptographic key material as a byte array.
     */
    public suspend fun getKeyMaterial(alias: String): ByteArray
}
