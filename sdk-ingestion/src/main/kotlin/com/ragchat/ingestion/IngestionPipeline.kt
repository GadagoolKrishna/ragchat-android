package com.ragchat.ingestion

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ingestion pipeline state machine entry point.
 */
public class IngestionPipeline {
    private val running = AtomicBoolean(false)

    /**
     * Checks if ingestion pipeline is active.
     */
    public fun isRunning(): Boolean = running.get()
}
