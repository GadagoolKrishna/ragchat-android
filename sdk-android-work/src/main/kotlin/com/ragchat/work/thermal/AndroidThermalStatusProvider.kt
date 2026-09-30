package com.ragchat.work.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.ragchat.api.ingestion.ThermalState
import com.ragchat.api.ingestion.ThermalStatusProvider
import java.util.concurrent.atomic.AtomicReference

/**
 * Android implementation of [ThermalStatusProvider] bridging [PowerManager] thermal status.
 *
 * Automatically registers listeners on API 29+ devices to monitor hardware thermal thresholds,
 * gracefully falling back to [ThermalState.NORMAL] on earlier Android versions.
 */
public class AndroidThermalStatusProvider(
    context: Context,
) : ThermalStatusProvider {
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val currentThermalState = AtomicReference(ThermalState.NORMAL)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            updateFromPowerManager(powerManager.currentThermalStatus)
            try {
                powerManager.addThermalStatusListener { status ->
                    updateFromPowerManager(status)
                }
            } catch (_: Exception) {
                // Fallback if platform service listener registration fails
            }
        }
    }

    private fun updateFromPowerManager(status: Int) {
        val state =
            when {
                status >= PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.CRITICAL
                status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.THROTTLED
                status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                else -> ThermalState.NORMAL
            }
        currentThermalState.set(state)
    }

    override fun getCurrentThermalState(): ThermalState = currentThermalState.get()
}
