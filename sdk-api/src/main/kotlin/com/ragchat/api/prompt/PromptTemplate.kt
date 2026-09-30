package com.ragchat.api.prompt

import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.Chunk

/**
 * Service Provider Interface (SPI) for formatting RAG prompts supplied to the LLM.
 */
public interface PromptTemplate {
    /**
     * Constructs the final prompt string incorporating system instructions, citations context,
     * conversational history, and user query.
     *
     * @param systemInstruction Optional top-level framing instruction.
     * @param contextChunks Retrieved relevant document passages.
     * @param chatHistory Prior conversation turns.
     * @param query Current user prompt.
     * @return Formatted prompt string ready for LLM consumption.
     */
    public fun buildPrompt(
        systemInstruction: String?,
        contextChunks: List<Chunk>,
        chatHistory: List<ChatMessage>,
        query: String,
    ): String
}
