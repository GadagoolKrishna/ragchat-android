package com.ragchat.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ragchat.storage.db.dao.AuditLogDao
import com.ragchat.storage.db.dao.ChunkDao
import com.ragchat.storage.db.dao.CollectionDao
import com.ragchat.storage.db.dao.DocumentDao
import com.ragchat.storage.db.dao.EmbeddingDao
import com.ragchat.storage.db.dao.FtsDao
import com.ragchat.storage.db.dao.IngestionJobDao
import com.ragchat.storage.db.dao.SchemaMetaDao
import com.ragchat.storage.db.entity.AuditLogEntity
import com.ragchat.storage.db.entity.ChunkEntity
import com.ragchat.storage.db.entity.CollectionEntity
import com.ragchat.storage.db.entity.DocumentEntity
import com.ragchat.storage.db.entity.EmbeddingEntity
import com.ragchat.storage.db.entity.IngestionJobEntity
import com.ragchat.storage.db.entity.SchemaMetaEntity

/**
 * Main encrypted Room Database for the RagChat SDK.
 */
@Database(
    entities = [
        CollectionEntity::class,
        DocumentEntity::class,
        ChunkEntity::class,
        EmbeddingEntity::class,
        IngestionJobEntity::class,
        AuditLogEntity::class,
        SchemaMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
public abstract class RagChatDatabase : RoomDatabase() {
    public abstract fun collectionDao(): CollectionDao

    public abstract fun documentDao(): DocumentDao

    public abstract fun chunkDao(): ChunkDao

    public abstract fun ftsDao(): FtsDao

    public abstract fun embeddingDao(): EmbeddingDao

    public abstract fun ingestionJobDao(): IngestionJobDao

    public abstract fun auditLogDao(): AuditLogDao

    public abstract fun schemaMetaDao(): SchemaMetaDao

    public companion object {
        public const val DATABASE_NAME: String = "ragchat_storage.db"

        /**
         * Database callback configuring FTS5 virtual table and triggers upon database creation/opening.
         */
        public val CALLBACK: Callback =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    createFts5TableAndTriggers(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Ensure foreign keys are enabled in SQLite
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            }

        /**
         * Migration from Schema version 1 to version 2 (adds index on ingestion_jobs and updated_at).
         */
        public val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val sql =
                        "CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_scope_id_document_id` " +
                            "ON `ingestion_jobs` (`scope_id`, `document_id`)"
                    db.execSQL(sql)
                }
            }

        internal fun createFts5TableAndTriggers(db: SupportSQLiteDatabase) {
            // Create FTS5 virtual table
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts USING fts5(
                    chunk_id UNINDEXED,
                    scope_id UNINDEXED,
                    collection_id UNINDEXED,
                    text
                );
                """.trimIndent(),
            )

            // Trigger for INSERT into chunks
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_chunks_fts_insert AFTER INSERT ON chunks
                BEGIN
                    INSERT INTO chunk_fts(chunk_id, scope_id, collection_id, text)
                    VALUES (new.id, new.scope_id, new.collection_id, new.text);
                END;
                """.trimIndent(),
            )

            // Trigger for DELETE from chunks
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_chunks_fts_delete AFTER DELETE ON chunks
                BEGIN
                    DELETE FROM chunk_fts WHERE chunk_id = old.id AND scope_id = old.scope_id;
                END;
                """.trimIndent(),
            )

            // Trigger for UPDATE on chunks
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_chunks_fts_update AFTER UPDATE ON chunks
                BEGIN
                    DELETE FROM chunk_fts WHERE chunk_id = old.id AND scope_id = old.scope_id;
                    INSERT INTO chunk_fts(chunk_id, scope_id, collection_id, text)
                    VALUES (new.id, new.scope_id, new.collection_id, new.text);
                END;
                """.trimIndent(),
            )
        }
    }
}
