package com.ragchat.models.profiler

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Categorization of on-device LLM / embedding capabilities.
 */
public enum class ModelTier {
    /**
     * Devices with low memory (< 4GB RAM) or entry-level chipsets.
     * Suitable for quantized embedding models or cloud fallback.
     */
    LIGHTWEIGHT,

    /**
     * Devices with 4GB - 8GB RAM and mid-to-high 64-bit SoCs.
     * Capable of running 2B-3B models (e.g. Gemma 2B, EmbeddingGemma).
     */
    STANDARD,

    /**
     * Flagship devices (> 8GB RAM) with dedicated NPU / high-performance GPU.
     * Capable of running 7B+ models or heavy multi-model pipelines.
     */
    HIGH_PERFORMANCE,
}

/**
 * Snapshot of device hardware capabilities.
 *
 * @property totalRamBytes Total physical RAM in bytes.
 * @property availableRamBytes Currently available system RAM in bytes.
 * @property freeStorageBytes Available storage on app-private storage partition.
 * @property socClass Hardware platform or SoC model.
 * @property isNpuCapable Whether the device has a certified NPU for accelerated inference.
 * @property supportedAbis Supported CPU architectures.
 * @property recommendedTier Recommended model tier.
 */
public data class DeviceProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val socClass: String,
    val isNpuCapable: Boolean,
    val supportedAbis: List<String>,
    val recommendedTier: ModelTier,
)

/**
 * Profiles the host Android device hardware to recommend optimal model execution tiers.
 */
public class DeviceProfiler(
    private val context: Context,
) {
    private companion object {
        private const val FOUR_GB: Long = 4L * 1024L * 1024L * 1024L
        private const val EIGHT_GB: Long = 8L * 1024L * 1024L * 1024L
    }

    /**
     * Inspects device memory, storage, chipset, and ABIs to produce a [DeviceProfile].
     */
    public fun profile(): DeviceProfile {
        val (totalRam, availRam) = resolveRam()
        val freeStorage = resolveFreeStorage()
        val (socIdentifier, isNpu) = resolveSocAndNpu()
        val tier = resolveTier(totalRam, isNpu)
        val abis = resolveAbis()

        return DeviceProfile(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            freeStorageBytes = freeStorage,
            socClass = socIdentifier,
            isNpuCapable = isNpu,
            supportedAbis = abis,
            recommendedTier = tier,
        )
    }

    private fun resolveRam(): Pair<Long, Long> {
        var total = 0L
        var avail = 0L
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            total = memInfo.totalMem
            avail = memInfo.availMem
        } catch (_: RuntimeException) {
            // Unmocked Context in unit tests fallback
        }
        val totalRam = if (total > 0L) total else Runtime.getRuntime().maxMemory()
        val availRam = if (avail > 0L) avail else Runtime.getRuntime().freeMemory()
        return Pair(totalRam, availRam)
    }

    private fun resolveFreeStorage(): Long {
        val storageDir =
            try {
                context.noBackupFilesDir ?: context.filesDir ?: File("/")
            } catch (_: RuntimeException) {
                File("/")
            }
        return try {
            val statFs = StatFs(storageDir.absolutePath)
            statFs.availableBlocksLong * statFs.blockSizeLong
        } catch (_: RuntimeException) {
            storageDir.usableSpace.takeIf { it > 0L } ?: (10L * 1024L * 1024L * 1024L)
        }
    }

    private fun resolveSocAndNpu(): Pair<String, Boolean> {
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        val socModel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL?.lowercase() ?: ""
            } else {
                ""
            }
        val npuKeywords = listOf("zuma", "tensor", "qcom", "sm8650", "dimensity")
        val combined = "$hardware $socModel"
        val isNpu = npuKeywords.any { combined.contains(it) }
        val socIdentifier = socModel.ifBlank { hardware }
        return Pair(socIdentifier, isNpu)
    }

    private fun resolveTier(
        totalRam: Long,
        isNpu: Boolean,
    ): ModelTier =
        when {
            totalRam >= EIGHT_GB && isNpu -> ModelTier.HIGH_PERFORMANCE
            totalRam >= FOUR_GB -> ModelTier.STANDARD
            else -> ModelTier.LIGHTWEIGHT
        }

    private fun resolveAbis(): List<String> =
        try {
            Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
}
