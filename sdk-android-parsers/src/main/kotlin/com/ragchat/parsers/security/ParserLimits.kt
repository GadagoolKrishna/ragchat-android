package com.ragchat.parsers.security

import com.ragchat.api.error.SdkError

/**
 * Defensive thresholds protecting parser runtimes from Denial of Service (DoS),
 * memory exhaustion, zip bombs, and path traversal vulnerabilities.
 *
 * @property maxFileSizeBytes Upper bound on total raw document file size in bytes (default 50 MB).
 * @property maxPages Upper bound on total readable pages in multi-page documents (default 500).
 * @property maxUncompressedSizeBytes Upper bound on cumulative extracted uncompressed stream bytes (default 100 MB).
 * @property maxExpansionRatio Maximum allowable ratio between uncompressed and compressed bytes (default 100x).
 * @property maxEntries Maximum number of zip/archive entries processed (default 10,000).
 */
public data class ParserLimits(
    public val maxFileSizeBytes: Long = 50L * 1024L * 1024L,
    public val maxPages: Int = 500,
    public val maxUncompressedSizeBytes: Long = 100L * 1024L * 1024L,
    public val maxExpansionRatio: Double = 100.0,
    public val maxEntries: Int = 10_000,
) {
    /**
     * Validates that the input source size does not exceed the allowed threshold.
     *
     * @throws SdkError.ValidationError if source size exceeds [maxFileSizeBytes].
     */
    public fun checkFileSize(sizeBytes: Long) {
        if (sizeBytes > 0 && sizeBytes > maxFileSizeBytes) {
            throw SdkError.ValidationError(
                field = "fileSize",
                details = "File size ($sizeBytes bytes) exceeds security limit of $maxFileSizeBytes bytes",
            )
        }
    }

    /**
     * Verifies that the processed page count does not exceed [maxPages].
     *
     * @throws SdkError.ValidationError if page count exceeds [maxPages].
     */
    public fun checkPageLimit(pageIndex: Int) {
        if (pageIndex > maxPages) {
            throw SdkError.ValidationError(
                field = "pageCount",
                details = "Document exceeds maximum allowable page limit of $maxPages pages",
            )
        }
    }

    /**
     * Sanitizes zip entry path against directory traversal (zip-slip) attacks.
     *
     * @param entryName The relative zip entry name.
     * @throws SdkError.ValidationError if path traversal is detected.
     */
    public fun validateZipEntryPath(entryName: String) {
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw SdkError.ValidationError(
                field = "zipEntryPath",
                details = "Illegal path traversal detected in archive entry: $entryName",
            )
        }
    }
}
