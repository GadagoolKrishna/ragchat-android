package com.ragchat.storage.db

import android.content.Context
import androidx.room.Room
import com.ragchat.storage.crypto.EnvelopeEncryptionManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Factory and lifecycle manager for the encrypted [RagChatDatabase] powered by SQLCipher.
 */
public class SqlCipherDatabaseProvider(
    private val context: Context,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val databaseName: String = RagChatDatabase.DATABASE_NAME,
) {
    init {
        // Initialize native SQLCipher binaries
        System.loadLibrary("sqlcipher")
    }

    /**
     * Builds and initializes an encrypted instance of [RagChatDatabase].
     *
     * @param memoryOnly When true, instantiates an in-memory encrypted database (for testing).
     */
    public suspend fun createDatabase(memoryOnly: Boolean = false): RagChatDatabase {
        val passphrase =
            envelopeEncryptionManager.getOrCreateDataKey(
                EnvelopeEncryptionManager.DATABASE_KEY_ALIAS,
            )
        val supportFactory = SupportOpenHelperFactory(passphrase)

        val builder =
            if (memoryOnly) {
                Room.inMemoryDatabaseBuilder(context, RagChatDatabase::class.java)
            } else {
                Room.databaseBuilder(context, RagChatDatabase::class.java, databaseName)
            }

        return builder
            .openHelperFactory(supportFactory)
            .addCallback(RagChatDatabase.CALLBACK)
            .addMigrations(RagChatDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
