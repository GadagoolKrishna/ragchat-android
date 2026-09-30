package com.ragchat.ui.compose.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ragchat.api.chat.ChatEvent
import com.ragchat.api.chat.ChatManager
import com.ragchat.api.chat.ChatOptions
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.state.ChatUiState
import com.ragchat.ui.compose.state.MessageDeliveryStatus
import com.ragchat.ui.compose.state.MessageFeedbackState
import com.ragchat.ui.compose.state.MessageRole
import com.ragchat.ui.compose.state.UiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Enterprise state holder managing chat messaging, token streaming, cancellation, and citations.
 */
public open class ChatViewModel(
    private val chatManager: ChatManager,
    private val workspaceId: String = "default_workspace",
    private val sessionId: String = UUID.randomUUID().toString(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    public val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    /**
     * Updates the current input query text.
     */
    public fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Submits a user question to the RAG chat engine.
     */
    @Suppress("TooGenericExceptionCaught")
    public fun sendMessage(query: String) {
        if (query.isBlank() || _uiState.value.isGenerating) return

        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()

        val userMessage =
            UiChatMessage(
                id = userMessageId,
                role = MessageRole.USER,
                content = query,
                status = MessageDeliveryStatus.COMPLETE,
            )

        val initialAssistantMessage =
            UiChatMessage(
                id = assistantMessageId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageDeliveryStatus.STREAMING,
            )

        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage + initialAssistantMessage,
                inputText = "",
                isGenerating = true,
                activeError = null,
            )
        }

        activeGenerationJob?.cancel()
        activeGenerationJob =
            viewModelScope.launch {
                try {
                    val stream =
                        chatManager.ask(
                            query = query,
                            options =
                                ChatOptions(
                                    workspaceId = workspaceId,
                                    sessionId = sessionId,
                                ),
                        )

                    stream.collect { event ->
                        handleChatEvent(assistantMessageId, event)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleGenerationException(assistantMessageId, e)
                } finally {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
    }

    private fun handleGenerationException(
        assistantMessageId: String,
        e: Exception,
    ) {
        _uiState.update { current ->
            current.copy(
                isGenerating = false,
                activeError = e.message ?: "Failed to generate answer",
                messages =
                    current.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(
                                status = MessageDeliveryStatus.FAILED,
                                errorMessage = e.message,
                            )
                        } else {
                            msg
                        }
                    },
            )
        }
    }

    @Suppress("LongMethod")
    private fun handleChatEvent(
        assistantMessageId: String,
        event: ChatEvent,
    ) {
        when (event) {
            is ChatEvent.Token -> {
                _uiState.update { current ->
                    current.copy(
                        messages =
                            current.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        content = msg.content + event.text,
                                        status = MessageDeliveryStatus.STREAMING,
                                    )
                                } else {
                                    msg
                                }
                            },
                    )
                }
            }
            is ChatEvent.CitationFound -> {
                _uiState.update { current ->
                    current.copy(
                        messages =
                            current.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(citations = msg.citations + event.citation)
                                } else {
                                    msg
                                }
                            },
                    )
                }
            }
            is ChatEvent.Done -> {
                _uiState.update { current ->
                    current.copy(
                        isGenerating = false,
                        messages =
                            current.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        content = event.answer.text,
                                        citations = event.answer.citations,
                                        status = MessageDeliveryStatus.COMPLETE,
                                    )
                                } else {
                                    msg
                                }
                            },
                    )
                }
            }
            is ChatEvent.Error -> {
                _uiState.update { current ->
                    current.copy(
                        isGenerating = false,
                        activeError = event.error.message,
                        messages =
                            current.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        status = MessageDeliveryStatus.FAILED,
                                        errorMessage = event.error.message,
                                    )
                                } else {
                                    msg
                                }
                            },
                    )
                }
            }
            else -> {
                // Ignore other intermediate events
            }
        }
    }

    /**
     * Immediately stops active token generation.
     */
    public fun stopGenerating() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { current ->
            current.copy(
                isGenerating = false,
                messages =
                    current.messages.map { msg ->
                        if (msg.status == MessageDeliveryStatus.STREAMING) {
                            msg.copy(status = MessageDeliveryStatus.COMPLETE)
                        } else {
                            msg
                        }
                    },
            )
        }
    }

    /**
     * Selects a citation to preview in the bottom sheet.
     */
    public fun selectCitation(citation: Citation?) {
        _uiState.update { it.copy(activeCitation = citation) }
    }

    /**
     * Records user feedback (helpful, unhelpful, or report).
     */
    public fun provideFeedback(
        messageId: String,
        feedbackState: MessageFeedbackState,
    ) {
        _uiState.update { current ->
            current.copy(
                messages =
                    current.messages.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(feedbackState = feedbackState)
                        } else {
                            msg
                        }
                    },
            )
        }
    }

    /**
     * Dismisses the active error message banner.
     */
    public fun dismissError() {
        _uiState.update { it.copy(activeError = null) }
    }

    /**
     * Marks consent as granted.
     */
    public fun grantConsent() {
        _uiState.update { it.copy(requiresConsentPrompt = false) }
    }
}

/**
 * Pluggable ViewModelProvider.Factory for instantiating [ChatViewModel].
 */
public class ChatViewModelFactory(
    private val chatManagerProvider: () -> ChatManager,
    private val workspaceId: String = "default_workspace",
    private val sessionId: String = UUID.randomUUID().toString(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                chatManager = chatManagerProvider(),
                workspaceId = workspaceId,
                sessionId = sessionId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
