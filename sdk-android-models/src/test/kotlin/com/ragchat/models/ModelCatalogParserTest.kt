package com.ragchat.models

import com.ragchat.models.catalog.ModelCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogParserTest {
    @Test
    fun testParseCatalogJson() {
        val json =
            """
            {
              "version": 1,
              "publishedEpochMs": 1700000000000,
              "entries": [
                {
                  "id": "gemma-2b-it",
                  "version": "1.0.0",
                  "sizeBytes": 1500000,
                  "sha256": "abc123sha256",
                  "signature": "sigBase64==",
                  "license": "Gemma License",
                  "minRamBytes": 4294967296,
                  "minOsVersion": 28,
                  "supportedAbis": ["arm64-v8a"],
                  "downloadUrl": "https://example.com/models/gemma.bin"
                }
              ]
            }
            """.trimIndent()

        val catalog = ModelCatalogParser.parseCatalog(json)
        assertEquals(1, catalog.version)
        assertEquals(1, catalog.entries.size)

        val entry = catalog.entries[0]
        assertEquals("gemma-2b-it", entry.id)
        assertEquals("1.0.0", entry.version)
        assertEquals(1500000L, entry.sizeBytes)
        assertEquals("abc123sha256", entry.sha256)
        assertEquals("https://example.com/models/gemma.bin", entry.downloadUrl)
    }

    @Test
    fun testFilterCompatibleModels() {
        val json =
            """
            {
              "version": 1,
              "entries": [
                {
                  "id": "model-high-ram",
                  "version": "1.0",
                  "sizeBytes": 1000,
                  "sha256": "hash1",
                  "minRamBytes": 8589934592,
                  "minOsVersion": 26,
                  "supportedAbis": ["arm64-v8a"]
                },
                {
                  "id": "model-low-ram",
                  "version": "1.0",
                  "sizeBytes": 1000,
                  "sha256": "hash2",
                  "minRamBytes": 2147483648,
                  "minOsVersion": 26,
                  "supportedAbis": ["arm64-v8a"]
                },
                {
                  "id": "model-x86-only",
                  "version": "1.0",
                  "sizeBytes": 1000,
                  "sha256": "hash3",
                  "minRamBytes": 2147483648,
                  "minOsVersion": 26,
                  "supportedAbis": ["x86_64"]
                }
              ]
            }
            """.trimIndent()

        val catalog = ModelCatalogParser.parseCatalog(json)

        // Device has 4GB RAM, OS 30, arm64-v8a
        val filtered =
            ModelCatalogParser.filterCompatibleModels(
                catalog = catalog,
                totalRamBytes = 4294967296L,
                currentOsVersion = 30,
                currentAbis = listOf("arm64-v8a"),
            )

        assertEquals(1, filtered.size)
        assertEquals("model-low-ram", filtered[0].id)
    }
}
