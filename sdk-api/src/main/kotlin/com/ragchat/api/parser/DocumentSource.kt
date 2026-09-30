package com.ragchat.api.parser

import java.io.InputStream

/**
 * Encapsulates the binary or stream source of a document to be parsed.
 *
 * Designed to avoid direct Android URI/Context dependencies in the pure JVM layer.
 */
public interface DocumentSource : AutoCloseable {
    /**
     * MIME type of the document stream.
     */
    public val mimeType: String

    /**
     * Estimated size in bytes if known, or -1.
     */
    public val sizeBytes: Long

    /**
     * Opens a new [InputStream] to read the document content.
     */
    public fun openStream(): InputStream
}
