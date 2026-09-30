package com.ragchat.llm.local.nano

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ragchat.api.llm.Availability

/**
 * Evaluates hardware, OS compatibility, and AICore system package readiness on the Android device.
 */
public open class AiCoreAvailabilityChecker(
    private val context: Context,
) {
    private companion object {
        private const val AICORE_PACKAGE = "com.google.android.aicore"
        private const val MIN_REQUIRED_API_LEVEL = Build.VERSION_CODES.UPSIDE_DOWN_CAKE // API 34+
    }

    /**
     * Determines whether Gemini Nano is executable on this device.
     */
    public open fun checkAvailability(): Availability {
        // 1. Android OS level check (Gemini Nano / AICore system integration requires API 34+)
        if (Build.VERSION.SDK_INT < MIN_REQUIRED_API_LEVEL) {
            return Availability.HARDWARE_UNSUPPORTED
        }

        val packageManager = context.packageManager
        val isAiCoreInstalled =
            try {
                packageManager.getPackageInfo(AICORE_PACKAGE, 0) != null
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }

        val hardware = Build.HARDWARE.lowercase()
        val socModel =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.lowercase()
            } else {
                ""
            }

        val isCertifiedChipset =
            hardware.contains("zuma") ||
                hardware.contains("tensor") ||
                hardware.contains("qcom") ||
                socModel.contains("sm8650") ||
                hardware.contains("dimensity")

        return when {
            !isAiCoreInstalled -> Availability.UNAVAILABLE
            !isCertifiedChipset -> Availability.HARDWARE_UNSUPPORTED
            else -> Availability.AVAILABLE
        }
    }
}
