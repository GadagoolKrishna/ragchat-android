package com.ragchat.testing

import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.Chunk
import com.ragchat.api.prompt.PromptTemplate

/**
 * Test fake implementation of [PromptTemplate].
 */
public class FakePromptTemplate : PromptTemplate {
    override fun buildPrompt(
        systemInstruction: String?,
        contextChunks: List<Chunk>,
        chatHistory: List<ChatMessage>,
        query: String,
    ): String {
        val sb = StringBuilder()
        if (!systemInstruction.isNullOrBlank()) {
            sb.append("System: ").append(systemInstruction).append("\n\n")
        }
        if (contextChunks.isNotEmpty()) {
            sb.append("Context:\n")
            for (chunk in contextChunks) {
                sb.append("- ").append(chunk.content).append("\n")
            }
            sb.append("\n")
        }
        sb.append("User: ").append(query)
        return sb.toString()
    }
}
