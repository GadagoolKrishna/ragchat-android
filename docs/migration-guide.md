# RagChat Android SDK Migration Guide

### Migrating from v0.1.0-alpha to v0.1.0 Stable

#### 1. Builder DSL Introduction
The manual `RagChat.initialize(context, config, database)` pattern is now streamlined into `RagChat.builder(context) { ... }`:

*Before:*
```kotlin
val config = RagChatConfigBuilder().apply { ... }.build()
RagChat.initialize(context, config)
```

*After:*
```kotlin
val rag = RagChat.builder(context) {
    llm {
        local()
        routing = ModelRoutingMode.LOCAL_FIRST
    }
    embeddings { onDevice() }
    storage { encrypted() }
}.build()
```

#### 2. VectorStore Schema Migration
Collections now support hybrid dense + BM25 keyword indexing. Existing collections will automatically upgrade their schema during initial database opening with zero data loss.
