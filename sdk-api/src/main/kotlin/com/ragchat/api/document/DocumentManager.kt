package com.ragchat.api.document

import com.ragchat.api.model.Document
import com.ragchat.api.model.IngestionProgress
import com.ragchat.api.model.OverallIngestionProgress
import com.ragchat.api.parser.DocumentSource
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream

/**
 * Public document catalog and background ingestion manager.
 */
public interface DocumentManager {
    /**
     * Ingests a document from an abstract [DocumentSource].
     *
     * @param source Stream-based document source.
     * @param collectionId Target collection identifier.
     * @param metadata Optional document metadata attributes.
     * @return Hot [Flow] of ingestion progress events.
     */
    public suspend fun add(
        source: DocumentSource,
        collectionId: String,
        metadata: Map<String, String> = emptyMap(),
    ): Flow<IngestionProgress>

    /**
     * Ingests a local [File].
     *
     * @param file Target file on local storage.
     * @param mimeType MIME type of the file.
     * @param collectionId Target collection identifier.
     * @param metadata Optional document metadata attributes.
     * @return Hot [Flow] of ingestion progress events.
     */
    public suspend fun add(
        file: File,
        mimeType: String,
        collectionId: String,
        metadata: Map<String, String> = emptyMap(),
    ): Flow<IngestionProgress>

    /**
     * Ingests from a raw [InputStream].
     *
     * @param stream Input stream containing document bytes.
     * @param name File name or identifier.
     * @param mimeType Document MIME type.
     * @param collectionId Target collection identifier.
     * @param metadata Optional document metadata attributes.
     * @return Hot [Flow] of ingestion progress events.
     */
    public suspend fun add(
        stream: InputStream,
        name: String,
        mimeType: String,
        collectionId: String,
        metadata: Map<String, String> = emptyMap(),
    ): Flow<IngestionProgress>

    /**
     * Lists all registered documents in the current scope or collection.
     *
     * @param collectionId Optional filter for a specific collection.
     * @return List of indexed [Document] records.
     */
    public suspend fun list(collectionId: String? = null): List<Document>

    /**
     * Retrieves current ingestion progress or state for a given document.
     *
     * @param documentId Unique identifier of the document.
     * @return Latest [IngestionProgress] or null if unknown.
     */
    public suspend fun status(documentId: String): IngestionProgress?

    /**
     * Purges a document, its chunks, dense vectors, FTS entries, and raw stored copies.
     *
     * @param documentId Document identifier to purge.
     */
    public suspend fun remove(documentId: String)

    /**
     * Re-indexes all existing documents in a collection.
     *
     * @param collectionId Target collection identifier.
     * @return [Flow] tracking overall batch re-indexing progress.
     */
    public suspend fun reindexAll(collectionId: String): Flow<OverallIngestionProgress>
}
