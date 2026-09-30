package com.ragchat.core.chat

import com.ragchat.api.chat.ChatEvent
import com.ragchat.api.chat.ChatHistoryStore
import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.governance.PiiRedactor
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.Answer
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.LlmEvent
import com.ragchat.api.model.MessageRole
import com.ragchat.api.storage.SearchResult
import com.ragchat.core.citation.CitationBuilder
import com.ragchat.core.structured.JsonSchemaValidator
import com.ragchat.core.structured.ValidationResult
import kotlinx.coroutines.flow.FlowCollector
import java.util.UUID

internal class ChatStreamExecutor(
    private val historyStore: ChatHistoryStore,
    private val piiRedactor: PiiRedactor?,
) {
    internal data class Context(
        val llmProvider: LlmProvider,
        val retrievedResults: List<SearchResult>,
        val options: ChatOptions,
    )

    suspend fun execute(
        initialRequest: LlmRequest,
        context: Context,
        collector: FlowCollector<ChatEvent>,
    ) {
        var currentReq = initialRequest
        var attemptsLeft = context.options.maxStructuredRetries

        while (attemptsLeft >= 0) {
            val rawAnswer = collectLlmStream(context.llmProvider, currentReq, collector)
            val sanitized = piiRedactor?.redact(rawAnswer) ?: rawAnswer

            val retryPrompt = checkSchemaRetry(sanitized, context.options.jsonSchema, attemptsLeft)
            if (retryPrompt != null) {
                attemptsLeft--
                currentReq = createRetryRequest(currentReq, rawAnswer, retryPrompt)
                continue
            }

            finalizeAnswer(sanitized, context, collector)
            return
        }
    }

    private fun checkSchemaRetry(
        answer: String,
        schema: String?,
        attemptsLeft: Int,
    ): String? {
        if (schema == null || attemptsLeft <= 0) return null
        val validation = JsonSchemaValidator.validate(answer, schema)
        return if (validation is ValidationResult.Invalid) {
            "Your previous output did not conform to JSON schema (${validation.reason}). Provide ONLY valid JSON."
        } else {
            null
        }
    }

    private fun createRetryRequest(
        req: LlmRequest,
        rawAnswer: String,
        prompt: String,
    ): LlmRequest {
        val updatedMessages =
            req.messages.toMutableList().apply {
                add(ChatMessage(id = "model-err-" + UUID.randomUUID().toString(), role = MessageRole.ASSISTANT, content = rawAnswer))
                add(ChatMessage(id = "retry-" + UUID.randomUUID().toString(), role = MessageRole.USER, content = prompt))
            }
        return req.copy(messages = updatedMessages)
    }

    private suspend fun collectLlmStream(
        llm: LlmProvider,
        req: LlmRequest,
        collector: FlowCollector<ChatEvent>,
    ): String {
        val builder = StringBuilder()
        llm.generate(req).collect { event ->
            when (event) {
                is LlmEvent.Token -> {
                    builder.append(event.text)
                    collector.emit(ChatEvent.Token(event.text))
                }
                is LlmEvent.Metadata -> {
                    collector.emit(ChatEvent.Metadata(event.promptTokens, event.candidateTokens, event.finishReason))
                }
                is LlmEvent.Error -> {
                    collector.emit(ChatEvent.Error(event.error))
                    throw event.error
                }
                is LlmEvent.Done -> {}
            }
        }
        return builder.toString()
    }

    private suspend fun finalizeAnswer(
        sanitizedAnswer: String,
        context: Context,
        collector: FlowCollector<ChatEvent>,
    ) {
        val citations = CitationBuilder.extractCitations(sanitizedAnswer, context.retrievedResults)
        for (c in citations) {
            collector.emit(ChatEvent.CitationFound(c))
        }

        val answer =
            Answer(
                text = sanitizedAnswer,
                citations = citations,
                confidence = context.retrievedResults.firstOrNull()?.score,
                modelUsed = context.llmProvider.id,
                locality = context.llmProvider.capabilities.locality,
            )
        collector.emit(ChatEvent.Done(answer))

        val assistantMsg =
            ChatMessage(
                id = "msg-" + UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = sanitizedAnswer,
                citations = citations,
            )
        historyStore.addMessage(context.options.workspaceId, context.options.sessionId, assistantMsg)
    }
}
