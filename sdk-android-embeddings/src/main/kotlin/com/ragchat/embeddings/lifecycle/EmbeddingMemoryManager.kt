package com.ragchat.embeddings.lifecycle

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global lifecycle monitor registering with the Android system to receive memory trim signals.
 *
 * Forwards [ComponentCallbacks2.onTrimMemory] events to registered on-device providers to
 * evict cached LiteRT interpreters and scratch buffers during low memory conditions.
 */
public class EmbeddingMemoryManager(
    private val logger: RagChatLogger? = null,
) : ComponentCallbacks2 {
    private val listeners = CopyOnWriteArrayList<WeakReference<ComponentCallbacks2>>()

    /**
     * Registers a listener to receive memory pressure notifications.
     */
    public fun registerListener(listener: ComponentCallbacks2) {
        listeners.add(WeakReference(listener))
    }

    /**
     * Registers this manager with the host Android [Context].
     */
    @Suppress("TooGenericExceptionCaught")
    public fun attachToContext(context: Context) {
        try {
            context.registerComponentCallbacks(this)
            logger?.log(LogLevel.INFO, "EmbeddingMemoryManager", "Attached to Android ComponentCallbacks2.")
        } catch (e: Exception) {
            logger?.log(LogLevel.WARN, "EmbeddingMemoryManager", "Failed to register component callbacks: ${e.message}")
        }
    }

    /**
     * Unregisters this manager from the host Android [Context].
     */
    public fun detachFromContext(context: Context) {
        try {
            context.unregisterComponentCallbacks(this)
        } catch (_: Exception) {
            // Ignore unregister errors
        }
    }

    override fun onTrimMemory(level: Int) {
        logger?.log(LogLevel.INFO, "EmbeddingMemoryManager", "onTrimMemory received with level $level")
        val iterator = listeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val target = ref.get()
            if (target != null) {
                target.onTrimMemory(level)
            } else {
                listeners.remove(ref)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        logger?.log(LogLevel.WARN, "EmbeddingMemoryManager", "onLowMemory received; evicting all cached interpreter memory.")
        val iterator = listeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val target = ref.get()
            if (target != null) {
                @Suppress("DEPRECATION")
                target.onLowMemory()
            } else {
                listeners.remove(ref)
            }
        }
    }
}
