package com.ragchat.core.chat

import com.ragchat.api.chat.ChatEvent
import com.ragchat.api.chat.ChatHistoryStore
import com.ragchat.api.chat.ChatManager
import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.config.ModelRoutingMode
import com.ragchat.api.config.RagChatConfig
import com.ragchat.api.embedding.EmbeddingTaskType
import com.ragchat.api.error.SdkError
import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.llm.Availability
import com.ragchat.api.llm.LlmProvider
import com.ragchat.api.llm.LlmRequest
import com.ragchat.api.model.Answer
import com.ragchat.api.model.ChatMessage
import com.ragchat.api.model.MessageRole
import com.ragchat.api.storage.SearchResult
import com.ragchat.core.memory.InMemoryChatHistoryStore
import com.ragchat.core.memory.RollingHistorySummarizer
import com.ragchat.core.prompt.PromptBuilder
import com.ragchat.retrieval.AclFilter
import com.ragchat.retrieval.ContextAssembler
import com.ragchat.retrieval.RetrievalConfig
import com.ragchat.retrieval.RetrievalPipeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Enterprise-grade conversational RAG pipeline orchestrator implementing [ChatManager].
 */
public class DefaultChatManager(
    private val config: RagChatConfig,
    private val historyStore: ChatHistoryStore = config.chatHistoryStore ?: InMemoryChatHistoryStore(),
    private val summarizer: RollingHistorySummarizer = RollingHistorySummarizer(),
) : ChatManager {
    private val streamExecutor = ChatStreamExecutor(historyStore, config.piiRedactor)

    override fun ask(
        query: String,
        options: ChatOptions,
    ): Flow<ChatEvent> =
        flow {
            recordUserMessage(query, options)
            val rewrittenQuery = rewriteQueryIfNeeded(query, options, this)
            evaluatePolicy(options)

            val activeLlm = resolveLlmProvider(options.routingMode ?: config.routingMode)
            val promptBuilder = PromptBuilder(modelFamily = PromptBuilder.detectFamily(activeLlm.id))
            val history =
                summarizer
                    .condenseIfNeeded(
                        workspaceId = options.workspaceId,
                        sessionId = options.sessionId,
                        historyStore = historyStore,
                        llmProvider = activeLlm,
                    ).dropLast(1)

            val retrievedResults = executeRetrieval(options, rewrittenQuery)
            emit(ChatEvent.RetrievalComplete(chunkCount = retrievedResults.size))

            if (retrievedResults.isEmpty()) {
                emitRefusalAnswer(promptBuilder, activeLlm, options, this)
                return@flow
            }

            val request =
                assembleRequest(
                    builder = promptBuilder,
                    llm = activeLlm,
                    history = history,
                    results = retrievedResults,
                    queryAndOptions = Pair(query, options),
                )
            val execContext = ChatStreamExecutor.Context(activeLlm, retrievedResults, options)
            streamExecutor.execute(request, execContext, this)
        }

    private suspend fun recordUserMessage(
        query: String,
        options: ChatOptions,
    ) {
        val userMsg =
            ChatMessage(
                id = "msg-" + UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = query,
            )
        historyStore.addMessage(options.workspaceId, options.sessionId, userMsg)
    }

    private suspend fun rewriteQueryIfNeeded(
        query: String,
        options: ChatOptions,
        collector: FlowCollector<ChatEvent>,
    ): String {
        val priorHistory = historyStore.getMessages(options.workspaceId, options.sessionId).dropLast(1)
        if (config.queryRewriter == null || priorHistory.isEmpty()) return query

        val rewritten = config.queryRewriter?.rewrite(query, priorHistory) ?: query
        if (rewritten != query) {
            collector.emit(ChatEvent.QueryRewritten(original = query, rewritten = rewritten))
        }
        return rewritten
    }

    private suspend fun evaluatePolicy(options: ChatOptions) {
        if (config.policyProvider == null) return
        val decision =
            config.policyProvider?.evaluate(
                intent = "CHAT_QUERY",
                context =
                    mapOf(
                        "workspaceId" to options.workspaceId,
                        "sessionId" to options.sessionId,
                        "collectionId" to options.collectionId,
                    ),
            )
        if (decision is PolicyDecision.Denied) {
            throw SdkError.PolicyViolationError(decision.ruleId, decision.category)
        }
    }

    private suspend fun emitRefusalAnswer(
        promptBuilder: PromptBuilder,
        activeLlm: LlmProvider,
        options: ChatOptions,
        collector: FlowCollector<ChatEvent>,
    ) {
        val refusalText = promptBuilder.fallbackMsg
        val answer =
            Answer(
                text = refusalText,
                citations = emptyList(),
                confidence = 0.0f,
                modelUsed = activeLlm.id,
                locality = activeLlm.capabilities.locality,
            )
        collector.emit(ChatEvent.Token(refusalText))
        collector.emit(ChatEvent.Done(answer))

        val assistantMsg =
            ChatMessage(
                id = "msg-" + UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = refusalText,
                citations = emptyList(),
            )
        historyStore.addMessage(options.workspaceId, options.sessionId, assistantMsg)
    }

    private suspend fun assembleRequest(
        builder: PromptBuilder,
        llm: LlmProvider,
        history: List<ChatMessage>,
        results: List<SearchResult>,
        queryAndOptions: Pair<String, ChatOptions>,
    ): LlmRequest {
        val (query, options) = queryAndOptions
        var historyTokens = 0
        for (msg in history) {
            historyTokens += llm.countTokens(msg.content)
        }
        val contextBudget = builder.calculateContextBudget(llm.capabilities, historyTokens)
        val assembler =
            ContextAssembler(
                maxTokenBudget = contextBudget,
                neighborExpansionWindow = if (llm.capabilities.contextWindow <= 4096) 0 else 1,
            )
        val assembledContext = assembler.assemble(results)
        val systemInstruction = builder.buildSystemInstruction(jsonSchema = options.jsonSchema)
        val contextText = builder.buildRetrievedContext(assembledContext.includedChunks)
        val messages = builder.assembleMessages(systemInstruction, history, contextText, query)

        return LlmRequest(
            messages = messages,
            temperature = options.temperature ?: 0.2f,
            maxTokens = options.maxTokens ?: llm.capabilities.maxOutputTokens.coerceAtMost(1024),
        )
    }

    private suspend fun executeRetrieval(
        options: ChatOptions,
        query: String,
    ): List<SearchResult> {
        val vs = config.vectorStore
        val ep = config.embeddingProvider
        val results =
            if (vs != null && ep != null) {
                runCatching {
                    val qv = ep.embed(listOf(query), EmbeddingTaskType.RETRIEVAL_QUERY).firstOrNull()
                    if (qv != null) {
                        val pipeline =
                            RetrievalPipeline(
                                vectorStore = vs,
                                reranker = config.reranker,
                                config =
                                    RetrievalConfig(
                                        topK = options.topK ?: config.defaultTopK,
                                        confidenceThreshold = options.confidenceThreshold ?: 0.35f,
                                    ),
                            )
                        pipeline.retrieve(
                            collectionId = options.collectionId,
                            query = query,
                            queryVector = qv,
                            aclFilter = options.aclFilter?.let { AclFilter(customAttributes = it) },
                        )
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        return results
    }

    private suspend fun resolveLlmProvider(mode: ModelRoutingMode): LlmProvider {
        val local = config.localLlmProvider
        val cloud = config.cloudLlmProvider
        return when (mode) {
            ModelRoutingMode.LOCAL_ONLY -> {
                val p = checkNotNull(local) { "Local LLM provider required for LOCAL_ONLY routing" }
                val avail = p.availability()
                if (avail != Availability.AVAILABLE) {
                    throw SdkError.ModelUnavailableError(p.id, "Local model status: $avail")
                }
                p
            }
            ModelRoutingMode.CLOUD_ONLY -> checkNotNull(cloud) { "Cloud LLM provider required for CLOUD_ONLY routing" }
            ModelRoutingMode.LOCAL_FIRST -> {
                if (local != null && local.availability() == Availability.AVAILABLE) {
                    local
                } else {
                    cloud ?: checkNotNull(local) { "No LLM provider configured" }
                }
            }
            ModelRoutingMode.CLOUD_FIRST, ModelRoutingMode.AUTO -> cloud ?: checkNotNull(local) { "No LLM provider configured" }
        }
    }

    override suspend fun getHistory(
        workspaceId: String,
        sessionId: String,
    ): List<ChatMessage> = historyStore.getMessages(workspaceId, sessionId)

    override suspend fun clearHistory(
        workspaceId: String,
        sessionId: String,
    ) {
        historyStore.clearHistory(workspaceId, sessionId)
    }
}
