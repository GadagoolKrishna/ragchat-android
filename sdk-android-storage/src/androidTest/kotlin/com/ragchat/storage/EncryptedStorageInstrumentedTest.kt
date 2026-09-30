package com.ragchat.storage

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ragchat.api.model.Chunk
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.DistanceMetric
import com.ragchat.storage.crypto.AndroidKeystoreKeyProvider
import com.ragchat.storage.crypto.EnvelopeEncryptionManager
import com.ragchat.storage.db.RagChatDatabase
import com.ragchat.storage.db.SqlCipherDatabaseProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests validating:
 * 1. Encryption at rest (opening DB file without SQLCipher key fails).
 * 2. Envelope key rotation.
 * 3. Scope isolation and purge completeness (purging document or clearing scope removes all traces).
 * 4. Migration testing with [MigrationTestHelper].
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageInstrumentedTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RagChatDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory(),
        )

    private lateinit var context: Context
    private lateinit var keystoreProvider: AndroidKeystoreKeyProvider
    private lateinit var encryptionManager: EnvelopeEncryptionManager
    private lateinit var dbProvider: SqlCipherDatabaseProvider
    private lateinit var database: RagChatDatabase

    private val testDbName = "test_encrypted_ragchat.db"
    private val scopeA = "user_1:workspace_alpha"
    private val scopeB = "user_2:workspace_beta"

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            context.deleteDatabase(testDbName)

            keystoreProvider = AndroidKeystoreKeyProvider(context, "test_master_kek")
            encryptionManager = EnvelopeEncryptionManager(context, keystoreProvider)
            dbProvider = SqlCipherDatabaseProvider(context, encryptionManager, testDbName)
            database = dbProvider.createDatabase()
        }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDbName)
        encryptionManager.clearAllKeys()
        keystoreProvider.cryptoShred("test_master_kek")
    }

    @Test
    fun testEncryptionAtRest_openingWithoutKeyFails() {
        // Close the SQLCipher database so the physical file is flushed to disk
        database.close()
        val dbFile = context.getDatabasePath(testDbName)
        assertTrue("Database file should exist", dbFile.exists())

        // Attempting to open the encrypted database without the key or with an empty key must fail
        try {
            val invalidFactory = SupportOpenHelperFactory(ByteArray(32))
            val rawDb =
                invalidFactory
                    .create(
                        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                            .builder(context)
                            .name(testDbName)
                            .callback(
                                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}

                                    override fun onUpgrade(
                                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                                        oldVersion: Int,
                                        newVersion: Int,
                                    ) {}
                                },
                            ).build(),
                    ).readableDatabase

            // Executing query on unauthenticated database must throw an exception
            rawDb.query("SELECT count(*) FROM collections")
            fail("Expected SQLCipher security exception when querying database with invalid key")
        } catch (e: Exception) {
            // Expected: file is not a database / authentication failed
            assertNotNull(e.message)
        }
    }

    @Test
    fun testScopeIsolation_and_PurgeCompleteness() =
        runBlocking {
            val vectorStoreA = SqlCipherVectorStore(scopeA, database, encryptionManager)
            val vectorStoreB = SqlCipherVectorStore(scopeB, database, encryptionManager)
            val storageManager = StorageManager(context, database, encryptionManager)

            val spec =
                CollectionSpec(
                    id = "col_test",
                    embeddingModelId = "embed_model_v1",
                    embeddingModelVersion = "1.0",
                    dimensions = 4,
                    distanceMetric = DistanceMetric.COSINE,
                )

            vectorStoreA.createCollection(spec)
            vectorStoreB.createCollection(spec)

            // Upsert chunks into scope A
            val chunkA =
                Chunk(
                    id = "chunk_a_1",
                    documentId = "doc_1",
                    content = "Scope A secret content",
                    sequenceNumber = 0,
                    tokenCount = 5,
                )
            vectorStoreA.upsert(spec.id, listOf(chunkA to floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)))

            // Querying from scope B must return 0 results (multi-tenant isolation)
            val resultsInB = vectorStoreB.query(spec.id, floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f), topK = 5)
            assertEquals("Scope B must not see chunks from Scope A", 0, resultsInB.size)

            // Querying from scope A returns the chunk
            val resultsInA = vectorStoreA.query(spec.id, floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f), topK = 5)
            assertEquals(1, resultsInA.size)
            assertEquals("chunk_a_1", resultsInA[0].chunk.id)

            // Test deleteDocument completeness in scope A
            storageManager.deleteDocument(scopeA, "doc_1")
            val resultsAfterDelete = vectorStoreA.query(spec.id, floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f), topK = 5)
            assertEquals("Purged document chunks must be completely eradicated", 0, resultsAfterDelete.size)

            val chunkEntity = database.chunkDao().getById(scopeA, "chunk_a_1")
            assertNull("Chunk record must be deleted", chunkEntity)
            val embEntity = database.embeddingDao().getByChunkId(scopeA, "chunk_a_1")
            assertNull("Embedding record must be deleted", embEntity)
        }

    @Test
    fun testKeyRotation() =
        runBlocking {
            val oldAlias = "test_master_kek"
            val newAlias = "test_master_kek_rotated"

            // Generate data key and wrap it under old master
            val dek1 = encryptionManager.getOrCreateDataKey("collection_alpha")

            // Perform rotation to new master alias
            encryptionManager.rotateMasterKey(
                oldMasterAlias = oldAlias,
                newMasterAlias = newAlias,
                destroyOldMaster = true,
            )

            assertFalse("Old master key must be destroyed", keystoreProvider.hasKey(oldAlias))
            assertTrue("New master key must exist", keystoreProvider.hasKey(newAlias))

            // Retrieve data key again; it must unwrap successfully with the new master key
            val unwrapMgr = EnvelopeEncryptionManager(context, keystoreProvider)
            val dekRotated = unwrapMgr.getOrCreateDataKey("collection_alpha")
            assertTrue("Rotated key material must match original DEK", dek1.contentEquals(dekRotated))

            keystoreProvider.cryptoShred(newAlias)
        }

    @Test
    fun testDatabaseMigration_v1_to_v2() {
        // Create Database at Version 1
        val dbV1 = helper.createDatabase(testDbName, 1)
        dbV1.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingestion_jobs` (
                `scope_id` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `document_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `progress` REAL NOT NULL,
                `error_category` TEXT,
                `error_code` TEXT,
                `retry_count` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`scope_id`, `id`)
            )
            """.trimIndent(),
        )
        dbV1.close()

        // Run Migration to Version 2 and validate schema
        val dbV2 =
            helper.runMigrationsAndValidate(
                testDbName,
                2,
                true,
                RagChatDatabase.MIGRATION_1_2,
            )
        assertNotNull(dbV2)
        dbV2.close()
    }
}
