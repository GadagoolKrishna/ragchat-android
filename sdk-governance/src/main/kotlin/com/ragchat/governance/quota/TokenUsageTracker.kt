package com.ragchat.governance.quota

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe tracker for cumulative cloud token usage within each calendar day.
 */
public class TokenUsageTracker {
    private val currentDay = AtomicReference<LocalDate>(LocalDate.now(ZoneOffset.UTC))
    private val dailyTokenCounter = AtomicLong(0L)

    /**
     * Records token usage for cloud inference.
     *
     * @param tokens Number of tokens consumed.
     * @return Cumulative tokens used today after adding [tokens].
     */
    public fun recordTokens(tokens: Long): Long {
        rollDayIfNeeded()
        return dailyTokenCounter.addAndGet(tokens.coerceAtLeast(0L))
    }

    /**
     * Gets the total cloud tokens consumed so far today.
     */
    public fun getDailyTokenCount(): Long {
        rollDayIfNeeded()
        return dailyTokenCounter.get()
    }

    /**
     * Resets the counter for testing or administrative overrides.
     */
    public fun reset() {
        dailyTokenCounter.set(0L)
        currentDay.set(LocalDate.now(ZoneOffset.UTC))
    }

    private fun rollDayIfNeeded() {
        val today = LocalDate.now(ZoneOffset.UTC)
        if (currentDay.get() != today) {
            synchronized(this) {
                if (currentDay.get() != today) {
                    currentDay.set(today)
                    dailyTokenCounter.set(0L)
                }
            }
        }
    }
}
