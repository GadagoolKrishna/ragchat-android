package com.ragchat.ingestion.retry

import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Exponential backoff retry configuration for transient network or engine errors.
 *
 * @property maxRetries Maximum number of attempts before throwing an unrecoverable error.
 * @property initialDelayMs Initial delay in milliseconds before the first retry.
 * @property maxDelayMs Maximum ceiling delay between retry attempts.
 * @property backoffMultiplier Exponential factor applied to delay duration.
 */
public data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 200L,
    val maxDelayMs: Long = 2000L,
    val backoffMultiplier: Double = 2.0,
) {
    /**
     * Executes the given block with idempotent exponential backoff.
     */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun <T> executeWithRetry(
        onRetry: ((attempt: Int, exception: Throwable) -> Unit)? = null,
        block: suspend (attempt: Int) -> T,
    ): T {
        var currentAttempt = 0
        var currentDelay = initialDelayMs

        while (true) {
            try {
                return block(currentAttempt)
            } catch (e: Exception) {
                currentAttempt++
                if (currentAttempt > maxRetries) {
                    throw e
                }
                onRetry?.invoke(currentAttempt, e)
                delay(currentDelay)
                val nextDelay = (currentDelay * backoffMultiplier).toLong()
                currentDelay = min(nextDelay, maxDelayMs)
            }
        }
    }
}
