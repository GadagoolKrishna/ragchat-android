package com.ragchat.ingestion.backpressure

import com.ragchat.api.ingestion.ThermalState
import com.ragchat.api.ingestion.ThermalStatusProvider
import kotlinx.coroutines.delay

/**
 * Controller calculating dynamic batch sizing and inter-batch delays to respect device thermal
 * thresholds and prevent excessive memory allocation.
 *
 * Free of any Android framework dependencies (pure Kotlin/JVM).
 */
public class BackpressureController(
    private val thermalProvider: ThermalStatusProvider? = null,
    private val baseBatchSize: Int = 16,
    private val minBatchSize: Int = 1,
) {
    /**
     * Resolves the recommended embedding batch size based on thermal status and available heap.
     */
    public fun resolveBatchSize(): Int {
        val thermal = thermalProvider?.getCurrentThermalState() ?: ThermalState.NORMAL
        val memoryFactor = calculateMemoryFactor()

        val thermalBatch =
            when (thermal) {
                ThermalState.NORMAL -> baseBatchSize
                ThermalState.MODERATE -> (baseBatchSize * 3) / 4
                ThermalState.THROTTLED -> baseBatchSize / 2
                ThermalState.CRITICAL -> minBatchSize
            }

        val effectiveMin = minBatchSize.coerceAtMost(baseBatchSize)
        val calculated = (thermalBatch * memoryFactor).toInt()
        return calculated.coerceIn(effectiveMin, baseBatchSize)
    }

    /**
     * Applies backpressure delay if device is experiencing thermal pressure.
     */
    public suspend fun applyThrottleDelay() {
        val thermal = thermalProvider?.getCurrentThermalState() ?: ThermalState.NORMAL
        val delayMs =
            when (thermal) {
                ThermalState.NORMAL -> 0L
                ThermalState.MODERATE -> 50L
                ThermalState.THROTTLED -> 250L
                ThermalState.CRITICAL -> 1000L
            }
        if (delayMs > 0L) {
            delay(delayMs)
        }
    }

    private fun calculateMemoryFactor(): Float {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val allocatedMemory = runtime.totalMemory() - runtime.freeMemory()
        val freeMemory = maxMemory - allocatedMemory
        val freeRatio = freeMemory.toDouble() / maxMemory.toDouble()

        return when {
            freeRatio < 0.15 -> 0.5f
            freeRatio < 0.30 -> 0.75f
            else -> 1.0f
        }
    }
}
