# Creating a Custom Vector Store

Implement high-scale persistent vector search engines (e.g. ObjectBox, Realm, Milvus) via the `VectorStore` SPI.

---

## 1. Implement `VectorStore`

```kotlin
package com.mycompany.ragchat.providers

import com.ragchat.api.storage.*
import com.ragchat.api.model.*
import kotlinx.coroutines.flow.Flow

class MyCustomVectorStore : VectorStore {
    override suspend fun createCollection(spec: CollectionSpec): Collection {
        // Initialize collection schema
        return Collection(spec.id, spec, System.currentTimeMillis(), 0)
    }

    override suspend fun getCollection(id: String): Collection? = null
    override suspend fun deleteCollection(id: String) {}

    override suspend fun insertChunks(collectionId: String, chunks: List<Chunk>, embeddings: List<FloatArray>) {
        // Persist dense embeddings and inverted full-text index
    }

    override suspend fun search(collectionId: String, queryVector: FloatArray, topK: Int, filter: Map<String, String>?): List<SearchResult> {
        // Cosine distance calculation
        return emptyList()
    }

    override suspend fun searchHybrid(collectionId: String, queryVector: FloatArray, queryText: String, topK: Int, denseWeight: Float): List<SearchResult> {
        // Reciprocal Rank Fusion of dense and BM25 results
        return emptyList()
    }

    override suspend fun deleteChunksByDocument(collectionId: String, documentId: String) {}
    override suspend fun getChunkCount(collectionId: String): Long = 0L
}
```
