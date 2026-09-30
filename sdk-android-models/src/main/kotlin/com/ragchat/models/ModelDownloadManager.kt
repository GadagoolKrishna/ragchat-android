package com.ragchat.models

import android.content.Context
import com.ragchat.api.error.SdkError
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.models.catalog.ModelEntry
import com.ragchat.models.profiler.DeviceProfile
import com.ragchat.models.profiler.DeviceProfiler
import com.ragchat.models.source.BundledAssetSource
import com.ragchat.models.source.HttpModelSource
import com.ragchat.models.source.ModelSource
import com.ragchat.models.source.PlayAssetPackSource
import com.ragchat.models.storage.ModelStorageManager
import com.ragchat.models.verifier.IntegrityVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.PublicKey

/**
 * Progression stages during model acquisition and staging.
 */
public enum class ModelStage {
    /**
     * Download or retrieval job is queued.
     */
    QUEUED,

    /**
     * Actively downloading over network or extracting from asset pack.
     */
    DOWNLOADING,

    /**
     * Computing checksum and validating ECDSA signature against pinned public key.
     */
    VERIFYING,

    /**
     * Staging directory promotion and atomic installation.
     */
    INSTALLING,

    /**
     * Model is installed and ready for execution.
     */
    READY,

    /**
     * Model acquisition failed.
     */
    FAILED,
}

/**
 * Progress snapshot emitted during model acquisition.
 *
 * @property modelId Model identifier.
 * @property version Model version.
 * @property bytesDownloaded Current count of bytes processed.
 * @property totalBytes Total expected byte length.
 * @property stage Current acquisition stage.
 * @property error Optional error if [stage] is [ModelStage.FAILED].
 */
public data class ModelDownloadProgress(
    val modelId: String,
    val version: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val stage: ModelStage,
    val error: Throwable? = null,
)

/**
 * On-device model manager orchestrating catalog inspection, resumable downloading,
 * cryptographic verification, atomic installation, rollback, and garbage collection.
 *
 * @property context Android application context.
 * @property pinnedPublicKey Optional public key for ECDSA signature verification.
 * @property sources Pluggable list of [ModelSource] providers (HTTP, Play Asset Delivery, Bundled Assets).
 * @property storageManager Storage manager for app-private staging and atomic swapping.
 * @property logger SDK diagnostic logger.
 */
public class ModelDownloadManager(
    private val context: Context,
    private val pinnedPublicKey: PublicKey? = null,
    private val sources: List<ModelSource> =
        listOf(
            HttpModelSource(context),
            PlayAssetPackSource(context),
            BundledAssetSource(context),
        ),
    private val storageManager: ModelStorageManager = ModelStorageManager(context),
    private val logger: RagChatLogger? = null,
) {
    private val integrityVerifier = IntegrityVerifier(pinnedPublicKey)
    private val deviceProfiler = DeviceProfiler(context)

    /**
     * Inspects device hardware capabilities and recommends an execution tier.
     */
    public fun getDeviceProfile(): DeviceProfile = deviceProfiler.profile()

    /**
     * Resolves the active filesystem file for [modelId], or null if not yet installed.
     */
    public fun getInstalledModelPath(modelId: String): File? = storageManager.getActiveModelFile(modelId)

    /**
     * Retrieves the currently active installed version string for [modelId].
     */
    public fun getActiveVersion(modelId: String): String? = storageManager.getActiveVersion(modelId)

    /**
     * Atomically rolls back to the previously installed version of [modelId].
     */
    public fun rollback(modelId: String): Boolean = storageManager.rollback(modelId)

    /**
     * Pins [version] of [modelId] to exempt it from garbage collection.
     */
    public fun pinVersion(
        modelId: String,
        version: String,
    ): Unit = storageManager.pinVersion(modelId, version)

    /**
     * Runs garbage collection to purge unpinned, stale versions of [modelId].
     */
    public fun runGarbageCollection(modelId: String): Unit = storageManager.garbageCollect(modelId)

    /**
     * Downloads and installs a model specified by [entry].
     *
     * @param entry Catalog metadata record.
     * @return [Flow] emitting [ModelDownloadProgress] stages.
     */
    public fun downloadAndInstall(entry: ModelEntry): Flow<ModelDownloadProgress> =
        flow {
            emit(progress(entry, 0L, ModelStage.QUEUED))

            if (!checkQuota(entry, storageManager, this)) return@flow
            val source = resolveSource(entry, sources, this) ?: return@flow
            val stagedFile = downloadStaged(entry, source, storageManager, this) ?: return@flow

            if (!verifyStaged(entry, stagedFile, integrityVerifier, this)) return@flow
            if (!installStaged(entry, stagedFile, storageManager, this)) return@flow

            logger?.log(LogLevel.INFO, "ModelManager", "Model ${entry.id} version ${entry.version} installed")
            emit(progress(entry, entry.sizeBytes, ModelStage.READY))
        }.flowOn(Dispatchers.IO)
}

private fun progress(
    entry: ModelEntry,
    bytes: Long,
    stage: ModelStage,
    err: Throwable? = null,
) = ModelDownloadProgress(
    modelId = entry.id,
    version = entry.version,
    bytesDownloaded = bytes,
    totalBytes = entry.sizeBytes,
    stage = stage,
    error = err,
)

private suspend fun checkQuota(
    entry: ModelEntry,
    storageManager: ModelStorageManager,
    collector: kotlinx.coroutines.flow.FlowCollector<ModelDownloadProgress>,
): Boolean =
    try {
        storageManager.checkStorageQuota(entry.sizeBytes)
        true
    } catch (e: SdkError) {
        collector.emit(progress(entry, 0L, ModelStage.FAILED, e))
        false
    } catch (e: IllegalStateException) {
        collector.emit(progress(entry, 0L, ModelStage.FAILED, e))
        false
    }

private suspend fun resolveSource(
    entry: ModelEntry,
    sources: List<ModelSource>,
    collector: kotlinx.coroutines.flow.FlowCollector<ModelDownloadProgress>,
): ModelSource? {
    val source = sources.firstOrNull { it.supports(entry) }
    if (source == null) {
        val err = SdkError.ModelUnavailableError(entry.id, "No compatible ModelSource found")
        collector.emit(progress(entry, 0L, ModelStage.FAILED, err))
    }
    return source
}

private suspend fun downloadStaged(
    entry: ModelEntry,
    source: ModelSource,
    storageManager: ModelStorageManager,
    collector: kotlinx.coroutines.flow.FlowCollector<ModelDownloadProgress>,
): File? {
    val stagingDir = storageManager.getStagingDirectory(entry.id, entry.version)
    collector.emit(progress(entry, 0L, ModelStage.DOWNLOADING))
    return try {
        source.fetch(entry, stagingDir) { _, _ -> }
    } catch (e: SdkError) {
        collector.emit(progress(entry, 0L, ModelStage.FAILED, e))
        null
    } catch (e: java.io.IOException) {
        collector.emit(progress(entry, 0L, ModelStage.FAILED, e))
        null
    }
}

private suspend fun verifyStaged(
    entry: ModelEntry,
    stagedFile: File,
    verifier: IntegrityVerifier,
    collector: kotlinx.coroutines.flow.FlowCollector<ModelDownloadProgress>,
): Boolean {
    collector.emit(progress(entry, stagedFile.length(), ModelStage.VERIFYING))
    return try {
        verifier.verify(stagedFile, entry)
        true
    } catch (e: SdkError) {
        stagedFile.delete()
        stagedFile.parentFile?.deleteRecursively()
        collector.emit(progress(entry, stagedFile.length(), ModelStage.FAILED, e))
        false
    }
}

private suspend fun installStaged(
    entry: ModelEntry,
    stagedFile: File,
    storageManager: ModelStorageManager,
    collector: kotlinx.coroutines.flow.FlowCollector<ModelDownloadProgress>,
): Boolean {
    collector.emit(progress(entry, stagedFile.length(), ModelStage.INSTALLING))
    return try {
        storageManager.installStagedModel(entry.id, entry.version, stagedFile)
        true
    } catch (e: SdkError) {
        collector.emit(progress(entry, stagedFile.length(), ModelStage.FAILED, e))
        false
    } catch (e: java.io.IOException) {
        collector.emit(progress(entry, stagedFile.length(), ModelStage.FAILED, e))
        false
    }
}
