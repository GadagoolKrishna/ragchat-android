package com.ragchat.sdk

import android.content.Context
import com.ragchat.api.RagChatApi
import com.ragchat.api.chat.ChatManager
import com.ragchat.api.config.RagChatConfig
import com.ragchat.api.document.DocumentManager
import com.ragchat.core.chat.DefaultChatManager
import com.ragchat.sdk.document.DefaultDocumentManager
import com.ragchat.storage.RoomIngestionCheckpointStore
import com.ragchat.storage.StorageManager
import com.ragchat.storage.crypto.AndroidKeystoreKeyProvider
import com.ragchat.storage.crypto.EnvelopeEncryptionManager
import com.ragchat.storage.db.RagChatDatabase
import com.ragchat.storage.db.SqlCipherDatabaseProvider

/**
 * Main facade entry point for the RagChat Android SDK.
 */
public object RagChat : RagChatApi {
    override val version: String = "0.1.0"

    private var activeConfig: RagChatConfig? = null
    private var documentManagerInstance: DocumentManager? = null
    private var chatManagerInstance: ChatManager? = null

    /**
     * Document management and ingestion engine.
     */
    override val documents: DocumentManager
        get() =
            checkNotNull(documentManagerInstance) {
                "RagChat SDK is not initialized. Call RagChat.initialize(context, config) first."
            }

    /**
     * Conversational chat and retrieval-augmented generation engine.
     */
    override val chat: ChatManager
        get() =
            checkNotNull(chatManagerInstance) {
                "RagChat SDK is not initialized. Call RagChat.initialize(context, config) first."
            }

    /**
     * Initializes the RagChat SDK facade.
     */
    public suspend fun initialize(
        context: Context,
        config: RagChatConfig,
        database: RagChatDatabase? = null,
    ) {
        activeConfig = config
        val keystoreProvider = AndroidKeystoreKeyProvider(context)
        val enc = EnvelopeEncryptionManager(context, keystoreProvider, config.authProvider as? com.ragchat.api.crypto.KeyProvider)
        val db = database ?: SqlCipherDatabaseProvider(context, enc).createDatabase()
        val storage = StorageManager(context, db, enc)
        val checkpointStore = RoomIngestionCheckpointStore(db)
        documentManagerInstance = DefaultDocumentManager(context, config, storage, checkpointStore)
        chatManagerInstance = DefaultChatManager(config)
    }
}
