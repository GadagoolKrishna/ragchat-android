package com.ragchat.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.ragchat.storage.db.entity.AuditLogEntity
import com.ragchat.storage.db.entity.ChunkEntity
import com.ragchat.storage.db.entity.CollectionEntity
import com.ragchat.storage.db.entity.DocumentEntity
import com.ragchat.storage.db.entity.EmbeddingEntity
import com.ragchat.storage.db.entity.IngestionJobEntity
import com.ragchat.storage.db.entity.SchemaMetaEntity

/**
 * Data Access Object for collections.
 */
@Dao
public interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(collection: CollectionEntity)

    @Query("SELECT * FROM collections WHERE scope_id = :scopeId AND id = :id LIMIT 1")
    public suspend fun getById(
        scopeId: String,
        id: String,
    ): CollectionEntity?

    @Query("SELECT * FROM collections WHERE scope_id = :scopeId")
    public suspend fun getAll(scopeId: String): List<CollectionEntity>

    @Query("DELETE FROM collections WHERE scope_id = :scopeId AND id = :id")
    public suspend fun deleteById(
        scopeId: String,
        id: String,
    )

    @Query("DELETE FROM collections WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * Data Access Object for documents.
 */
@Dao
public interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE scope_id = :scopeId AND id = :id LIMIT 1")
    public suspend fun getById(
        scopeId: String,
        id: String,
    ): DocumentEntity?

    @Query("SELECT * FROM documents WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun getByCollection(
        scopeId: String,
        collectionId: String,
    ): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE scope_id = :scopeId")
    public suspend fun getAllInScope(scopeId: String): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE scope_id = :scopeId AND id = :id")
    public suspend fun deleteById(
        scopeId: String,
        id: String,
    )

    @Query("DELETE FROM documents WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun deleteByCollection(
        scopeId: String,
        collectionId: String,
    )

    @Query("DELETE FROM documents WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * Data Access Object for chunks.
 */
@Dao
public interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE scope_id = :scopeId AND id = :id LIMIT 1")
    public suspend fun getById(
        scopeId: String,
        id: String,
    ): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun getByCollection(
        scopeId: String,
        collectionId: String,
    ): List<ChunkEntity>

    @Query(
        """
        SELECT * FROM chunks
        WHERE scope_id = :scopeId AND document_id = :documentId
        ORDER BY sequence_number ASC
        """,
    )
    public suspend fun getByDocument(
        scopeId: String,
        documentId: String,
    ): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun countChunks(
        scopeId: String,
        collectionId: String,
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT document_id)
        FROM chunks
        WHERE scope_id = :scopeId AND collection_id = :collectionId
        """,
    )
    public suspend fun countDocuments(
        scopeId: String,
        collectionId: String,
    ): Long

    @Query("DELETE FROM chunks WHERE scope_id = :scopeId AND document_id = :documentId")
    public suspend fun deleteByDocument(
        scopeId: String,
        documentId: String,
    )

    @Query("DELETE FROM chunks WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun deleteByCollection(
        scopeId: String,
        collectionId: String,
    )

    @Query("DELETE FROM chunks WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * FTS5 query result item.
 */
public data class FtsSearchResult(
    public val chunkId: String,
    public val rank: Double,
)

/**
 * Data Access Object for FTS5 full-text queries using Room RawQuery.
 */
@Dao
public interface FtsDao {
    @RawQuery
    public suspend fun searchRaw(query: SupportSQLiteQuery): List<FtsSearchResult>

    public suspend fun search(
        scopeId: String,
        collectionId: String,
        queryText: String,
        topK: Int,
    ): List<FtsSearchResult> {
        val sql =
            """
            SELECT chunk_id AS chunkId, bm25(chunk_fts) AS rank
            FROM chunk_fts
            WHERE chunk_fts MATCH ? AND scope_id = ? AND collection_id = ?
            ORDER BY rank
            LIMIT ?
            """.trimIndent()
        val query = SimpleSQLiteQuery(sql, arrayOf(queryText, scopeId, collectionId, topK))
        return searchRaw(query)
    }

    public fun deleteByChunkId(
        db: SupportSQLiteDatabase,
        scopeId: String,
        chunkId: String,
    ) {
        db.execSQL("DELETE FROM chunk_fts WHERE scope_id = ? AND chunk_id = ?", arrayOf(scopeId, chunkId))
    }

    public fun deleteByCollection(
        db: SupportSQLiteDatabase,
        scopeId: String,
        collectionId: String,
    ) {
        db.execSQL("DELETE FROM chunk_fts WHERE scope_id = ? AND collection_id = ?", arrayOf(scopeId, collectionId))
    }

    public fun deleteAllInScope(
        db: SupportSQLiteDatabase,
        scopeId: String,
    ) {
        db.execSQL("DELETE FROM chunk_fts WHERE scope_id = ?", arrayOf(scopeId))
    }
}

/**
 * Data Access Object for dense embeddings.
 */
@Dao
public interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(embeddings: List<EmbeddingEntity>)

    @Query("SELECT * FROM embeddings WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun getAllInCollection(
        scopeId: String,
        collectionId: String,
    ): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE scope_id = :scopeId AND chunk_id = :chunkId LIMIT 1")
    public suspend fun getByChunkId(
        scopeId: String,
        chunkId: String,
    ): EmbeddingEntity?

    @Query("DELETE FROM embeddings WHERE scope_id = :scopeId AND chunk_id = :chunkId")
    public suspend fun deleteByChunkId(
        scopeId: String,
        chunkId: String,
    )

    @Query("DELETE FROM embeddings WHERE scope_id = :scopeId AND collection_id = :collectionId")
    public suspend fun deleteByCollection(
        scopeId: String,
        collectionId: String,
    )

    @Query("DELETE FROM embeddings WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * Data Access Object for ingestion jobs.
 */
@Dao
public interface IngestionJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(job: IngestionJobEntity)

    @Query("SELECT * FROM ingestion_jobs WHERE scope_id = :scopeId AND id = :id LIMIT 1")
    public suspend fun getById(
        scopeId: String,
        id: String,
    ): IngestionJobEntity?

    @Query("SELECT * FROM ingestion_jobs WHERE scope_id = :scopeId AND document_id = :documentId")
    public suspend fun getByDocument(
        scopeId: String,
        documentId: String,
    ): List<IngestionJobEntity>

    @Query("DELETE FROM ingestion_jobs WHERE scope_id = :scopeId AND id = :id")
    public suspend fun deleteById(
        scopeId: String,
        id: String,
    )

    @Query("DELETE FROM ingestion_jobs WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * Data Access Object for audit logging.
 */
@Dao
public interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE scope_id = :scopeId ORDER BY timestamp DESC")
    public suspend fun getAllInScope(scopeId: String): List<AuditLogEntity>

    @Query("DELETE FROM audit_log WHERE scope_id = :scopeId")
    public suspend fun deleteAllInScope(scopeId: String)
}

/**
 * Data Access Object for schema metadata.
 */
@Dao
public interface SchemaMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun set(meta: SchemaMetaEntity)

    @Query("SELECT value FROM schema_meta WHERE `key` = :key LIMIT 1")
    public suspend fun get(key: String): String?
}
