package com.ragchat.models.storage

import android.content.Context
import android.os.StatFs
import com.ragchat.api.error.SdkError
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import org.json.JSONObject
import java.io.File

/**
 * Manages atomic filesystem staging, installation, version pinning, rollback, and garbage collection.
 *
 * Enforces isolation inside `context.noBackupFilesDir/models/`.
 */
public open class ModelStorageManager(
    private val context: Context,
    private val logger: RagChatLogger? = null,
    baseDirectory: File? = null,
) {
    private val baseDir: File =
        baseDirectory ?: File(
            try {
                context.noBackupFilesDir ?: context.filesDir
            } catch (_: RuntimeException) {
                File(System.getProperty("java.io.tmpdir"), "ragchat_models")
            },
            "models",
        )
    private val stagingDir: File = File(baseDir, ".staging")

    init {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!stagingDir.exists()) stagingDir.mkdirs()
    }

    /**
     * Checks whether the device filesystem has sufficient headroom for [requiredBytes].
     *
     * Requires [requiredBytes] + 20% safety margin.
     */
    public open fun checkStorageQuota(requiredBytes: Long) {
        val freeBytes: Long =
            try {
                val statFs = StatFs(baseDir.absolutePath)
                statFs.availableBlocksLong * statFs.blockSizeLong
            } catch (_: RuntimeException) {
                baseDir.usableSpace.takeIf { it > 0L } ?: Long.MAX_VALUE
            }

        val requiredWithMargin = (requiredBytes.toDouble() * 1.2).toLong()
        if (freeBytes < requiredWithMargin) {
            logger?.log(
                LogLevel.ERROR,
                "ModelManager",
                "Insufficient storage: requires $requiredWithMargin bytes, available $freeBytes bytes",
            )
            throw SdkError.StorageCryptoError("INSUFFICIENT_STORAGE_QUOTA")
        }
    }

    /**
     * Allocates a staging directory for an in-flight download or unpacking operation.
     */
    public fun getStagingDirectory(
        modelId: String,
        version: String,
    ): File {
        val dir = File(stagingDir, "${modelId}_${version}_${System.currentTimeMillis()}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Atomically promotes a staged file to the active installation path `models/{modelId}/{version}/model.bin`.
     */
    public fun installStagedModel(
        modelId: String,
        version: String,
        stagedFile: File,
    ): File {
        val targetVersionDir = File(baseDir, "$modelId/$version")
        if (!targetVersionDir.exists()) targetVersionDir.mkdirs()

        val finalModelFile = File(targetVersionDir, "model.bin")
        if (finalModelFile.exists()) {
            finalModelFile.delete()
        }

        val success = stagedFile.renameTo(finalModelFile)
        if (!success) {
            // Fallback to copy if cross-filesystem move fails
            stagedFile.copyTo(finalModelFile, overwrite = true)
            stagedFile.delete()
        }

        // Clean up parent staging folder
        stagedFile.parentFile?.deleteRecursively()

        // Atomically update active_version.json
        setActiveVersion(modelId, version)

        logger?.log(LogLevel.INFO, "ModelManager", "Installed model $modelId version $version atomically")
        return finalModelFile
    }

    /**
     * Returns the resolved active file path for [modelId], or null if not installed.
     */
    public fun getActiveModelFile(modelId: String): File? {
        val activeVersion = getActiveVersion(modelId) ?: return null
        val modelFile = File(baseDir, "$modelId/$activeVersion/model.bin")
        return if (modelFile.exists() && modelFile.length() > 0L) modelFile else null
    }

    /**
     * Retrieves the recorded active version string for [modelId].
     */
    public fun getActiveVersion(modelId: String): String? {
        val metaFile = File(baseDir, "$modelId/active_version.json")
        if (!metaFile.exists()) return null
        return try {
            val json = JSONObject(metaFile.readText())
            json.optString("activeVersion").takeIf { !it.isNullOrBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Atomically points active version metadata to [version].
     */
    public fun setActiveVersion(
        modelId: String,
        version: String,
    ) {
        val modelDir = File(baseDir, modelId)
        if (!modelDir.exists()) modelDir.mkdirs()

        val metaFile = File(modelDir, "active_version.json")
        val tmpFile = File(modelDir, "active_version.tmp")

        val currentMeta =
            if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

        val previous = currentMeta.optString("activeVersion", "")
        if (previous.isNotBlank() && previous != version) {
            currentMeta.put("previousVersion", previous)
        }
        currentMeta.put("activeVersion", version)
        currentMeta.put("updatedAt", System.currentTimeMillis())

        tmpFile.writeText(currentMeta.toString())
        tmpFile.renameTo(metaFile)
    }

    /**
     * Pins [version] of [modelId] to prevent garbage collection.
     */
    public fun pinVersion(
        modelId: String,
        version: String,
    ) {
        val modelDir = File(baseDir, modelId)
        if (!modelDir.exists()) modelDir.mkdirs()

        val metaFile = File(modelDir, "active_version.json")
        val currentMeta =
            if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

        currentMeta.put("pinnedVersion", version)
        metaFile.writeText(currentMeta.toString())
        logger?.log(LogLevel.INFO, "ModelManager", "Pinned model $modelId to version $version")
    }

    /**
     * Rolls back the active version to the previously installed version.
     *
     * @return True if a rollback succeeded, false if no previous version was recorded.
     */
    public fun rollback(modelId: String): Boolean {
        val metaFile = File(baseDir, "$modelId/active_version.json")
        val json =
            if (metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()) }.getOrNull()
            } else {
                null
            }

        val previousVersion = json?.optString("previousVersion", "").orEmpty()
        val previousModelFile = File(baseDir, "$modelId/$previousVersion/model.bin")
        val canRollback = previousVersion.isNotBlank() && previousModelFile.exists()

        if (canRollback) {
            setActiveVersion(modelId, previousVersion)
            logger?.log(LogLevel.INFO, "ModelManager", "Rolled back model $modelId to version $previousVersion")
        }
        return canRollback
    }

    /**
     * Deletes model versions that are neither active nor pinned.
     *
     * @param modelId Model identifier.
     * @param keepPinned Whether to preserve the pinned version.
     */
    public fun garbageCollect(
        modelId: String,
        keepPinned: Boolean = true,
    ) {
        val modelDir = File(baseDir, modelId)
        if (!modelDir.exists()) return

        val active = getActiveVersion(modelId)
        val metaFile = File(modelDir, "active_version.json")
        val pinned =
            if (keepPinned && metaFile.exists()) {
                runCatching { JSONObject(metaFile.readText()).optString("pinnedVersion") }.getOrNull()
            } else {
                null
            }

        val versionDirs = modelDir.listFiles { f -> f.isDirectory && f.name != ".staging" } ?: return
        for (dir in versionDirs) {
            val vName = dir.name
            if (vName != active && vName != pinned) {
                dir.deleteRecursively()
                logger?.log(LogLevel.INFO, "ModelManager", "Garbage collected old model version $modelId/$vName")
            }
        }

        // Clean stale temporary staging files
        stagingDir.listFiles()?.forEach { f ->
            if (System.currentTimeMillis() - f.lastModified() > 24 * 60 * 60 * 1000L) {
                f.deleteRecursively()
            }
        }
    }
}
