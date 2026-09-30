package com.ragchat.api.llm

import com.ragchat.api.model.LlmEvent
import kotlinx.coroutines.flow.Flow

/**
 * Service Provider Interface (SPI) for Large Language Model inference.
 *
 * Pluggable implementations execute locally (e.g., Gemini Nano, Gemma via LiteRT/MediaPipe)
 * or connect to remote cloud endpoints.
 */
public interface LlmProvider : AutoCloseable {
    /**
     * Unique identifier for this provider implementation (e.g., "gemini-nano", "cloud-vertex").
     */
    public val id: String

    /**
     * Declared capabilities and limits of this model engine.
     */
    public val capabilities: LlmCapabilities

    /**
     * Evaluates current operational readiness of the provider.
     *
     * @return [Availability] status describing whether model is ready, downloading, or unsupported.
     */
    public suspend fun availability(): Availability

    /**
     * Generates a streaming response for the given request.
     *
     * @param request Generation parameters and conversation history.
     * @return Cold [Flow] emitting [LlmEvent] items (tokens, metadata, error, completion).
     */
    public fun generate(request: LlmRequest): Flow<LlmEvent>

    /**
     * Counts the number of tokens required to represent [text] in this provider's tokenizer.
     *
     * @param text Raw string to calculate token length for.
     * @return Computed token count.
     */
    public suspend fun countTokens(text: String): Int

    /**
     * Releases active native resources, model sessions, or network connections.
     */
    override fun close()
}
