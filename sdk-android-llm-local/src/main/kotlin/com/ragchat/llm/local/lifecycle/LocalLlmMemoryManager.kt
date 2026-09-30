package com.ragchat.llm.local.lifecycle

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.llm.local.litert.GemmaLiteRtProvider
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Monitors system memory pressure events via [ComponentCallbacks2] and triggers proactive
 * unloading of on-device LLM models to prevent system Out-Of-Memory (OOM) termination.
 */
public class LocalLlmMemoryManager(
    private val logger: RagChatLogger? = null,
) : ComponentCallbacks2 {
    private val registeredGemmaProviders = CopyOnWriteArrayList<WeakReference<GemmaLiteRtProvider>>()

    /**
     * Registers a [GemmaLiteRtProvider] to receive memory trimming signals.
     */
    public fun register(provider: GemmaLiteRtProvider) {
        registeredGemmaProviders.add(WeakReference(provider))
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            logger?.log(LogLevel.WARN, "LocalLlm", "System low memory signal ($level) received. Unloading local LLM models.")
            registeredGemmaProviders.forEach { ref ->
                ref.get()?.unload()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op for orientation / configuration changes
    }

    override fun onLowMemory() {
        logger?.log(LogLevel.WARN, "LocalLlm", "onLowMemory received. Unloading all registered local LLMs.")
        registeredGemmaProviders.forEach { ref ->
            ref.get()?.unload()
        }
    }
}
