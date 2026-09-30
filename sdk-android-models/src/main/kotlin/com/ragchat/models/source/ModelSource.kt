package com.ragchat.models.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ragchat.api.error.SdkError
import com.ragchat.models.catalog.ModelEntry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Service Provider Interface for retrieving or resolving model weights from a distribution medium.
 */
public interface ModelSource {
    /**
     * Unique identifier for the model delivery mechanism.
     */
    public val sourceName: String

    /**
     * Checks if this source can provide the given [entry].
     */
    public fun supports(entry: ModelEntry): Boolean

    /**
     * Retrieves or resolves the model file into [stagingDir].
     *
     * @param entry Target model catalog record.
     * @param stagingDir Directory where staged files can be assembled.
     * @param onProgress Callback receiving current byte count and total byte count.
     * @return Downloaded or resolved [File].
     */
    public suspend fun fetch(
        entry: ModelEntry,
        stagingDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): File
}

/**
 * HTTP/HTTPS model source with HTTP Range header resume support.
 *
 * @property context Android context for network constraint checks.
 * @property okHttpClient Configured HTTP client.
 * @property wifiOnly Whether downloads are strictly prohibited over metered cellular connections.
 */
public class HttpModelSource(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val wifiOnly: Boolean = false,
) : ModelSource {
    override val sourceName: String = "HTTP"

    override fun supports(entry: ModelEntry): Boolean = !entry.downloadUrl.isNullOrBlank()

    override suspend fun fetch(
        entry: ModelEntry,
        stagingDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): File {
        val downloadUrl =
            entry.downloadUrl
                ?: throw SdkError.ValidationError("downloadUrl", "Missing download URL for ${entry.id}")

        checkWifiConstraint()

        if (!stagingDir.exists()) stagingDir.mkdirs()

        val targetFile = File(stagingDir, "${entry.id}-${entry.version}.part")
        val existingBytes = prepareTargetFile(targetFile, entry.sizeBytes)

        val request = buildDownloadRequest(downloadUrl, existingBytes)
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw SdkError.ModelUnavailableError(entry.id, "HTTP download failed with status ${response.code}")
        }

        val isPartial = response.code == 206
        val responseBody =
            response.body
                ?: throw SdkError.ModelUnavailableError(entry.id, "Empty response body from $downloadUrl")

        val append = isPartial && existingBytes > 0L
        writeStreamWithProgress(responseBody, targetFile, append, entry.sizeBytes, onProgress)

        return targetFile
    }

    private fun checkWifiConstraint() {
        if (wifiOnly && isNetworkMetered()) {
            throw SdkError.PolicyViolationError("WIFI_ONLY", "NETWORK")
        }
    }

    private fun prepareTargetFile(
        targetFile: File,
        expectedSize: Long,
    ): Long {
        var existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        if (existingBytes > expectedSize) {
            targetFile.delete()
            existingBytes = 0L
        }
        return existingBytes
    }

    private fun buildDownloadRequest(
        url: String,
        existingBytes: Long,
    ): Request {
        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        return requestBuilder.build()
    }

    private fun writeStreamWithProgress(
        body: ResponseBody,
        targetFile: File,
        append: Boolean,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val existingBytes = if (append) targetFile.length() else 0L
        var totalDownloaded = existingBytes
        body.byteStream().use { input ->
            val raf = RandomAccessFile(targetFile, "rw")
            if (append) {
                raf.seek(existingBytes)
            } else {
                raf.setLength(0)
            }

            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                checkWifiConstraint()
                raf.write(buffer, 0, read)
                totalDownloaded += read
                onProgress(totalDownloaded, totalBytes)
            }
            raf.close()
        }
    }

    private fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
    }
}

/**
 * Model source for Google Play Asset Delivery / Play for On-device AI.
 *
 * @property context Android context for asset pack discovery.
 */
public class PlayAssetPackSource(
    private val context: Context,
) : ModelSource {
    override val sourceName: String = "PlayAssetPack"

    override fun supports(entry: ModelEntry): Boolean = !entry.assetPackName.isNullOrBlank()

    override suspend fun fetch(
        entry: ModelEntry,
        stagingDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): File {
        val packName =
            entry.assetPackName
                ?: throw SdkError.ValidationError("assetPackName", "Asset pack name missing for ${entry.id}")

        // Look for model inside app-private asset pack directory or staged pack files
        val assetPackDir = File(context.filesDir, "asset_packs/$packName")
        val candidate = File(assetPackDir, "${entry.id}.bin")

        if (candidate.exists()) {
            onProgress(candidate.length(), candidate.length())
            return candidate
        }

        throw SdkError.ModelUnavailableError(entry.id, "Asset pack $packName not yet resident on device")
    }
}

/**
 * Model source for models pre-bundled in the APK assets directory.
 *
 * @property context Android context providing access to [AssetManager].
 */
public class BundledAssetSource(
    private val context: Context,
) : ModelSource {
    override val sourceName: String = "BundledAsset"

    override fun supports(entry: ModelEntry): Boolean =
        try {
            val list = context.assets.list("models") ?: emptyArray()
            list.contains("${entry.id}.bin") || list.contains("${entry.id}.litertlm")
        } catch (_: Exception) {
            false
        }

    override suspend fun fetch(
        entry: ModelEntry,
        stagingDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): File {
        val assetName =
            if (context.assets.list("models")?.contains("${entry.id}.bin") == true) {
                "models/${entry.id}.bin"
            } else {
                "models/${entry.id}.litertlm"
            }

        if (!stagingDir.exists()) stagingDir.mkdirs()
        val destFile = File(stagingDir, "${entry.id}-${entry.version}.bin")

        context.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var copied = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    onProgress(copied, entry.sizeBytes)
                }
            }
        }

        return destFile
    }
}
