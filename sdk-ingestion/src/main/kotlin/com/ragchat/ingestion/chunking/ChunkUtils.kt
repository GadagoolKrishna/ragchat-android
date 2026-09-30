package com.ragchat.ingestion.chunking

import java.security.MessageDigest

internal object ChunkUtils {
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        val hexString = StringBuilder(hash.size * 2)
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
