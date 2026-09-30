package com.ragchat.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Collection record isolating chunk indices per tenant scope.
 */
@Entity(
    tableName = "collections",
    primaryKeys = ["scope_id", "id"],
    indices = [
        Index(value = ["scope_id", "id"], unique = true),
    ],
)
public data class CollectionEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "name") public val name: String,
    @ColumnInfo(name = "embedding_model_id") public val embeddingModelId: String,
    @ColumnInfo(name = "embedding_version") public val embeddingVersion: String,
    @ColumnInfo(name = "dimensions") public val dimensions: Int,
    @ColumnInfo(name = "distance_metric") public val distanceMetric: String,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)

/**
 * Ingested document metadata.
 */
@Entity(
    tableName = "documents",
    primaryKeys = ["scope_id", "id"],
    indices = [
        Index(value = ["scope_id", "collection_id"]),
    ],
)
public data class DocumentEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "collection_id") public val collectionId: String,
    @ColumnInfo(name = "name") public val name: String,
    @ColumnInfo(name = "mime_type") public val mimeType: String,
    @ColumnInfo(name = "size_bytes") public val sizeBytes: Long,
    @ColumnInfo(name = "checksum") public val checksum: String,
    @ColumnInfo(name = "metadata_json") public val metadataJson: String,
    @ColumnInfo(name = "raw_encrypted_path") public val rawEncryptedPath: String?,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)

/**
 * Text chunk record associated with a document and collection.
 */
@Entity(
    tableName = "chunks",
    primaryKeys = ["scope_id", "id"],
    indices = [
        Index(value = ["scope_id", "collection_id"]),
        Index(value = ["scope_id", "document_id"]),
    ],
)
public data class ChunkEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "document_id") public val documentId: String,
    @ColumnInfo(name = "collection_id") public val collectionId: String,
    @ColumnInfo(name = "sequence_number") public val sequenceNumber: Int,
    @ColumnInfo(name = "text") public val text: String,
    @ColumnInfo(name = "token_count") public val tokenCount: Int,
    @ColumnInfo(name = "metadata_json") public val metadataJson: String,
)

/**
 * Dense vector embeddings stored as serialized float byte arrays.
 */
@Entity(
    tableName = "embeddings",
    primaryKeys = ["scope_id", "chunk_id"],
    indices = [
        Index(value = ["scope_id", "collection_id"]),
    ],
)
public data class EmbeddingEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "chunk_id") public val chunkId: String,
    @ColumnInfo(name = "collection_id") public val collectionId: String,
    @ColumnInfo(name = "dimensions") public val dimensions: Int,
    @ColumnInfo(name = "vector_blob") public val vectorBlob: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingEntity
        if (scopeId != other.scopeId) return false
        if (chunkId != other.chunkId) return false
        if (collectionId != other.collectionId) return false
        if (dimensions != other.dimensions) return false
        if (!vectorBlob.contentEquals(other.vectorBlob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = scopeId.hashCode()
        result = 31 * result + chunkId.hashCode()
        result = 31 * result + collectionId.hashCode()
        result = 31 * result + dimensions
        result = 31 * result + vectorBlob.contentHashCode()
        return result
    }
}

/**
 * Document ingestion job execution state.
 */
@Entity(
    tableName = "ingestion_jobs",
    primaryKeys = ["scope_id", "id"],
    indices = [
        Index(value = ["scope_id", "document_id"]),
    ],
)
public data class IngestionJobEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "document_id") public val documentId: String,
    @ColumnInfo(name = "status") public val status: String,
    @ColumnInfo(name = "progress") public val progress: Float,
    @ColumnInfo(name = "error_category") public val errorCategory: String?,
    @ColumnInfo(name = "error_code") public val errorCode: String?,
    @ColumnInfo(name = "retry_count") public val retryCount: Int,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)

/**
 * Zero-PII audit trail record.
 */
@Entity(
    tableName = "audit_log",
    primaryKeys = ["scope_id", "id"],
)
public data class AuditLogEntity(
    @ColumnInfo(name = "scope_id") public val scopeId: String,
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "event_type") public val eventType: String,
    @ColumnInfo(name = "action") public val action: String,
    @ColumnInfo(name = "status") public val status: String,
    @ColumnInfo(name = "timestamp") public val timestamp: Long,
)

/**
 * Schema metadata and key-value configuration.
 */
@Entity(tableName = "schema_meta")
public data class SchemaMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") public val key: String,
    @ColumnInfo(name = "value") public val value: String,
)
