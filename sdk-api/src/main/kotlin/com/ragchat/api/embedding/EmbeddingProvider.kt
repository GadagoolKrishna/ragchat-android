package com.ragchat.api.embedding

/**
 * Service Provider Interface (SPI) for text embedding generation.
 */
public interface EmbeddingProvider : AutoCloseable {
    /**
     * Unique identifier of the embedding model (e.g., "embeddinggemma-256", "text-embedding-004").
     */
    public val modelId: String

    /**
     * Semantic version string of the model.
     */
    public val version: String

    /**
     * Dimensionality of vectors produced by this model.
     */
    public val dimensions: Int

    /**
     * Maximum input token count permitted per text passage.
     */
    public val maxInputTokens: Int

    /**
     * Whether output vectors are L2-normalized.
     */
    public val normalize: Boolean

    /**
     * Computes dense vector embeddings for a batch of text passages.
     *
     * @param texts Input passages to embed.
     * @param taskType Target objective guiding embedding orientation.
     * @return List of float vectors corresponding 1:1 with input passages.
     */
    public suspend fun embed(
        texts: List<String>,
        taskType: EmbeddingTaskType = EmbeddingTaskType.RETRIEVAL_DOCUMENT,
    ): List<FloatArray>

    /**
     * Closes underlying native session or client resources.
     */
    override fun close()
}
