# Creating a Custom LLM Provider

The RagChat SDK enables custom LLM engines (e.g. llama.cpp, ExecuTorch, proprietary gateway) by implementing the `LlmProvider` SPI from `:sdk-api`.

---

## 1. Implement `LlmProvider`

```kotlin
package com.mycompany.ragchat.providers

import com.ragchat.api.llm.*
import com.ragchat.api.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MyCustomLlmProvider : LlmProvider {
    override val id: String = "my-custom-llm"

    override val capabilities: LlmCapabilities = LlmCapabilities(
        contextWindow = 8192,
        maxOutputTokens = 2048,
        streaming = true,
        toolCalling = false,
        multimodal = false,
        locality = Locality.LOCAL,
        dataResidency = "device",
    )

    override suspend fun availability(): Availability {
        return Availability.AVAILABLE
    }

    override fun generate(request: LlmRequest): Flow<LlmEvent> = flow {
        // Stream output tokens
        emit(LlmEvent.ContentDelta("Hello from custom provider!"))
        emit(LlmEvent.Done(totalTokens = 6, latencyMs = 120))
    }
}
```

---

## 2. Register via the Builder DSL

```kotlin
val rag = RagChat.builder(context) {
    llm {
        local(MyCustomLlmProvider())
        routing = ModelRoutingMode.LOCAL_ONLY
    }
}.build()
```
