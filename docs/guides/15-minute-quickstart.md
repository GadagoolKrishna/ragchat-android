# 15-Minute Quick Start Guide: RagChat Android SDK

Get up and running with enterprise on-device RAG in under 15 minutes.

---

## 1. Add Dependencies

Add the RagChat SDK aggregator and Jetpack Compose UI to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.ragchat:sdk:0.1.0")
    implementation("com.ragchat:sdk-ui-compose:0.1.0")
    
    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
}
```

---

## 2. Initialize RagChat (10-Line Integration)

Initialize the SDK in your `Application` class or main entrypoint using the secure-by-default fluent DSL builder:

```kotlin
// Secure by default: AES-256-GCM envelope encryption, SQLCipher DB, and zero-PII logging
val ragChat = RagChat.builder(context) {
    llm {
        local() // Binds Gemini Nano on supported devices
        routing = ModelRoutingMode.LOCAL_FIRST
    }
    embeddings { onDevice() } // 768-dim LiteRT EmbeddingGemma
    storage { encrypted() } // Android Keystore hardware-backed encryption
}.build()
```

---

## 3. Ingest Your First Document

Add documents asynchronously from a local file, asset, or raw input stream:

```kotlin
lifecycleScope.launch {
    val pdfFile = File(context.filesDir, "security_policy.pdf")
    
    RagChat.documents.add(
        file = pdfFile,
        mimeType = "application/pdf",
        collectionId = "enterprise_docs"
    ).collect { progress ->
        println("Document ${progress.documentId} stage: ${progress.stage} (${(progress.progress * 100).toInt()}%)")
    }
}
```

---

## 4. Add the Chat UI to Jetpack Compose

Embed the complete `ChatView` into your Compose screen:

```kotlin
@Composable
fun MainChatScreen() {
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(chatManagerProvider = { RagChat.chat })
    )

    RagChatTheme {
        ChatView(
            viewModel = chatViewModel,
            onManageDocsClick = { /* Navigate to DocumentManagerScreen */ }
        )
    }
}
```

---

## 5. What's Next?
- Configure cloud fallback using `cloud(GeminiApi(authProvider))`
- Implement custom document parsers for proprietary formats
- Review our [Security Whitepaper](../security-whitepaper.md) for compliance details
