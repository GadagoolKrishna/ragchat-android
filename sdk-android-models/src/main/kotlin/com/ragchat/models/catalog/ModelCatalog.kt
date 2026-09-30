package com.ragchat.models.catalog

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadata record for a downloadable on-device model.
 *
 * @property id Unique model identifier (e.g. "gemma-2b-it", "embeddinggemma-300m").
 * @property version Semantic version string (e.g. "1.0.0").
 * @property sizeBytes Size of the compiled model binary in bytes.
 * @property sha256 Expected SHA-256 hex digest of the model file.
 * @property signature Base64-encoded ECDSA signature over canonical string `id:version:sizeBytes:sha256`.
 * @property license Model license name or terms of use URL.
 * @property minRamBytes Minimum system RAM required in bytes.
 * @property minOsVersion Minimum Android OS SDK level (API level) required.
 * @property supportedAbis Target CPU architectures (e.g. "arm64-v8a", "x86_64").
 * @property downloadUrl Optional remote URL for HTTP range downloading.
 * @property assetPackName Optional Google Play Asset Pack or On-Device AI package name.
 */
public data class ModelEntry(
    val id: String,
    val version: String,
    val sizeBytes: Long,
    val sha256: String,
    val signature: String,
    val license: String,
    val minRamBytes: Long,
    val minOsVersion: Int,
    val supportedAbis: List<String>,
    val downloadUrl: String? = null,
    val assetPackName: String? = null,
) {
    /**
     * Produces the canonical string representation used for cryptographic signature verification.
     */
    public fun canonicalSignaturePayload(): ByteArray = "$id:$version:$sizeBytes:$sha256".toByteArray(Charsets.UTF_8)
}

/**
 * Catalog containing available models and version specifications.
 *
 * @property version Schema version of the catalog.
 * @property publishedEpochMs Timestamp in milliseconds when the catalog was published.
 * @property entries List of available model entries.
 */
public data class ModelCatalog(
    val version: Int,
    val publishedEpochMs: Long,
    val entries: List<ModelEntry>,
)

/**
 * Parser and validator for signed Model Catalog JSON structures.
 */
public object ModelCatalogParser {
    /**
     * Parses a JSON string into a [ModelCatalog].
     *
     * @param jsonText Raw JSON string.
     * @return Parsed [ModelCatalog].
     * @throws IllegalArgumentException if required fields are missing or malformed.
     */
    public fun parseCatalog(jsonText: String): ModelCatalog {
        val root = JSONObject(jsonText)
        val catalogVersion = root.optInt("version", 1)
        val publishedEpochMs = root.optLong("publishedEpochMs", System.currentTimeMillis())
        val entriesArray = root.getJSONArray("entries")

        val entries = mutableListOf<ModelEntry>()
        for (i in 0 until entriesArray.length()) {
            val item = entriesArray.getJSONObject(i)
            val abisJson = item.optJSONArray("supportedAbis") ?: JSONArray()
            val abis = mutableListOf<String>()
            for (j in 0 until abisJson.length()) {
                abis.add(abisJson.getString(j))
            }

            entries.add(
                ModelEntry(
                    id = item.getString("id"),
                    version = item.getString("version"),
                    sizeBytes = item.getLong("sizeBytes"),
                    sha256 = item.getString("sha256"),
                    signature = item.optString("signature", ""),
                    license = item.optString("license", "Unknown"),
                    minRamBytes = item.optLong("minRamBytes", 0L),
                    minOsVersion = item.optInt("minOsVersion", Build.VERSION_CODES.O),
                    supportedAbis = abis,
                    downloadUrl = item.optString("downloadUrl").takeIf { !it.isNullOrBlank() },
                    assetPackName = item.optString("assetPackName").takeIf { !it.isNullOrBlank() },
                ),
            )
        }

        return ModelCatalog(
            version = catalogVersion,
            publishedEpochMs = publishedEpochMs,
            entries = entries,
        )
    }

    /**
     * Filters entries in [catalog] against the current device's OS level, RAM, and CPU ABIs.
     *
     * @param catalog Input catalog.
     * @param totalRamBytes Total RAM of the device in bytes.
     * @param currentOsVersion Current Android SDK level.
     * @param currentAbis Supported ABIs of the device.
     * @return List of compatible [ModelEntry] instances.
     */
    public fun filterCompatibleModels(
        catalog: ModelCatalog,
        totalRamBytes: Long,
        currentOsVersion: Int = Build.VERSION.SDK_INT,
        currentAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): List<ModelEntry> =
        catalog.entries.filter { entry ->
            val osCompatible = currentOsVersion >= entry.minOsVersion
            val ramCompatible = totalRamBytes >= entry.minRamBytes
            val abiCompatible = entry.supportedAbis.isEmpty() || entry.supportedAbis.any { currentAbis.contains(it) }
            osCompatible && ramCompatible && abiCompatible
        }
}
