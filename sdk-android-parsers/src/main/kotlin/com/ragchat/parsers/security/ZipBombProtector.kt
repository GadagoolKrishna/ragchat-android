package com.ragchat.parsers.security

import com.ragchat.api.error.SdkError
import java.io.FilterInputStream
import java.io.InputStream

/**
 * [FilterInputStream] that monitors cumulative decompressed byte counts to protect against zip-bomb attacks.
 */
public class ZipBombProtector(
    inputStream: InputStream,
    private val limits: ParserLimits = ParserLimits(),
) : FilterInputStream(inputStream) {
    private var totalBytesRead: Long = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            trackBytes(1L)
        }
        return b
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            trackBytes(count.toLong())
        }
        return count
    }

    private fun trackBytes(bytes: Long) {
        totalBytesRead += bytes
        if (totalBytesRead > limits.maxUncompressedSizeBytes) {
            throw SdkError.ValidationError(
                field = "decompressedSize",
                details = "Decompressed size exceeded maximum uncompressed threshold of ${limits.maxUncompressedSizeBytes} bytes",
            )
        }
    }

    /**
     * Total number of uncompressed bytes read through this stream.
     */
    public fun getTotalBytesRead(): Long = totalBytesRead
}
