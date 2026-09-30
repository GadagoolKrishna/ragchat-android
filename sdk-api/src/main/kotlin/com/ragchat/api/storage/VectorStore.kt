package com.ragchat.api.storage

import com.ragchat.api.model.Chunk
import com.ragchat.api.model.Collection
import com.ragchat.api.model.CollectionSpec
import com.ragchat.api.model.CollectionStats
import kotlinx.coroutines.flow.Flow

/**
 * Service Provider Interface (SPI) for encrypted on-device or remote vector index storage.
 */
public interface VectorStore : AutoCloseable {
    /**
     * Initializes a new collection with the given specification.
     *
     * @param spec Dimensions, embedding model, and metric configuration.
     * @return Created or existing [Collection].
     */
    public suspend fun createCollection(spec: CollectionSpec): Collection

    /**
     * Inserts or replaces a batch of chunks and their corresponding dense vectors.
     *
     * @param collectionId Target collection identifier.
     * @param chunks Pairs of [Chunk] and its dense float vector.
     */
    public suspend fun upsert(
        collectionId: String,
        chunks: List<Pair<Chunk, FloatArray>>,
    )

    /**
     * Removes all chunks belonging to a document from the collection.
     *
     * @param collectionId Target collection identifier.
     * @param documentId Identifier of the document to purge.
     */
    public suspend fun deleteByDocument(
        collectionId: String,
        documentId: String,
    )

    /**
     * Performs a dense nearest-neighbor search.
     *
     * @param collectionId Target collection identifier.
     * @param vector Query embedding vector.
     * @param topK Maximum number of nearest neighbors to retrieve.
     * @param filter Key-value metadata criteria that matching chunks must satisfy.
     * @return Ranked list of [SearchResult] items.
     */
    public suspend fun query(
        collectionId: String,
        vector: FloatArray,
        topK: Int,
        filter: Map<String, String>? = null,
    ): List<SearchResult>

    /**
     * Performs hybrid search combining full-text search (BM25/FTS) and dense vector retrieval.
     *
     * @param collectionId Target collection identifier.
     * @param queryText Raw text search query for keyword matching.
     * @param vector Dense query embedding vector.
     * @param topK Maximum number of results to retrieve.
     * @param filter Optional metadata filter.
     * @return Ranked list of [SearchResult] items.
     */
    public suspend fun hybridFtsQuery(
        collectionId: String,
        queryText: String,
        vector: FloatArray,
        topK: Int,
        filter: Map<String, String>? = null,
    ): List<SearchResult>

    /**
     * Retrieves runtime storage statistics for a collection.
     *
     * @param collectionId Target collection identifier.
     * @return [CollectionStats] containing chunk count, document count, and size.
     */
    public suspend fun stats(collectionId: String): CollectionStats

    /**
     * Streams all chunk and vector pairs for backup, migration, or re-indexing.
     *
     * @param collectionId Target collection identifier.
     * @return [Flow] of indexed chunks with their vectors.
     */
    public suspend fun export(collectionId: String): Flow<Pair<Chunk, FloatArray>>

    /**
     * Irrevocably destroys the collection and crypto-shreds all associated encryption keys.
     *
     * @param collectionId Target collection identifier.
     */
    public suspend fun cryptoShred(collectionId: String)

    /**
     * Closes the vector store connection.
     */
    override fun close()
}
