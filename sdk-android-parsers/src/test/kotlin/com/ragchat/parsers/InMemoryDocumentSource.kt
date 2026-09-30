package com.ragchat.parsers

import com.ragchat.api.parser.DocumentSource
import java.io.ByteArrayInputStream
import java.io.InputStream

class InMemoryDocumentSource(
    private val bytes: ByteArray,
    override val mimeType: String,
) : DocumentSource {
    override val sizeBytes: Long = bytes.size.toLong()

    override fun openStream(): InputStream = ByteArrayInputStream(bytes)

    override fun close() {
        // No-op for in-memory stream
    }
}
