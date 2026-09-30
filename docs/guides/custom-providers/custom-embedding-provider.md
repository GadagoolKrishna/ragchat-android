# Creating a Custom Embedding Provider

Implement custom text embedding models by adhering to the `EmbeddingProvider` SPI from `:sdk-api`.

---

## 1. Implement `EmbeddingProvider`

```kotlin
package com.mycompany.ragchat.providers

import com.ragchat.api.embedding.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MyCustomEmbeddingProvider : EmbeddingProvider {
    override val modelId: String = "custom-embed-v1"
    override val version: String = "1.0.0"
    override val dimensions: Int = 384
    override val maxInputTokens: Int = 512
    override val normalize: Boolean = true

    override suspend fun embedQuery(text: String): FloatArray = withContext(Dispatchers.Default) {
        generateVector(text)
    }

    override suspend fun embedPassages(passages: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        passages.map { generateVector(it) }
    }

    private fun generateVector(text: String): FloatArray {
        // Model inference logic
        return FloatArray(dimensions) { 0.05f }
    }
}
```

---

## 2. Register via the Builder DSL

```kotlin
val rag = RagChat.builder(context) {
    embeddings {
        custom(MyCustomEmbeddingProvider())
    }
}.build()
```
