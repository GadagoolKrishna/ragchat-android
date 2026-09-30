# Creating a Custom Document Parser

Support custom document formats (e.g. EPUB, PPTX, RTF) via the `DocumentParser` SPI from `:sdk-api`.

---

## 1. Implement `DocumentParser`

```kotlin
package com.mycompany.ragchat.providers

import com.ragchat.api.parser.*
import com.ragchat.api.model.*
import java.io.InputStream

class CsvDocumentParser : DocumentParser {
    override val supportedMimeTypes: Set<String> = setOf("text/csv")

    override suspend fun parse(stream: InputStream, metadata: Map<String, String>): ParsedDocument {
        val lines = stream.bufferedReader().readLines()
        val elements = lines.map { ParsedElement.Text(it) }
        return ParsedDocument(
            elements = elements,
            metadata = metadata + ("rowCount" to lines.size.toString()),
        )
    }
}
```

---

## 2. Register via the Builder DSL

```kotlin
val rag = RagChat.builder(context) {
    parsers(CsvDocumentParser())
}.build()
```
