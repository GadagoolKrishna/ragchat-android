package com.ragchat.core.routing

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Health states of an LLM provider circuit breaker.
 */
public enum class CircuitState {
    /**
     * Provider is operational and processing calls normally.
     */
    CLOSED,

    /**
     * Provider has experienced consecutive errors exceeding the threshold and is blocked.
     */
    OPEN,

    /**
     * Circuit is testing recovery by allowing a trial request through.
     */
    HALF_OPEN,
}

/**
 * Thread-safe circuit breaker tracking provider reliability and cool-down periods.
 */
public class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeoutMs: Long = 60_000L,
) {
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastFailureTimestamps = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Inspects the current circuit state for a given provider.
     */
    public fun getState(providerId: String): CircuitState {
        val failures = failureCounts[providerId]?.get() ?: 0
        if (failures < failureThreshold) {
            return CircuitState.CLOSED
        }
        val lastFailure = lastFailureTimestamps[providerId]?.get() ?: 0L
        val elapsed = System.currentTimeMillis() - lastFailure
        return if (elapsed >= resetTimeoutMs) {
            CircuitState.HALF_OPEN
        } else {
            CircuitState.OPEN
        }
    }

    /**
     * Records an execution failure against a provider.
     */
    public fun recordFailure(providerId: String) {
        val counter = failureCounts.computeIfAbsent(providerId) { AtomicInteger(0) }
        counter.incrementAndGet()
        val ts = lastFailureTimestamps.computeIfAbsent(providerId) { AtomicLong(0L) }
        ts.set(System.currentTimeMillis())
    }

    /**
     * Records a successful execution, resetting failure metrics.
     */
    public fun recordSuccess(providerId: String) {
        failureCounts[providerId]?.set(0)
    }

    /**
     * Resets circuit breaker tracking for all providers.
     */
    public fun resetAll() {
        failureCounts.clear()
        lastFailureTimestamps.clear()
    }
}
