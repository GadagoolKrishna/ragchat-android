package com.ragchat.ui.compose.state

import androidx.compose.runtime.Immutable
import com.ragchat.api.model.Citation

/**
 * Visual role classification of a chat message.
 */
public enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/**
 * Execution / delivery status of a chat message.
 */
public enum class MessageDeliveryStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    FAILED,
}

/**
 * User feedback state on an assistant message.
 */
public enum class MessageFeedbackState {
    NONE,
    HELPFUL,
    UNHELPFUL,
}

/**
 * Execution origin badge for assistant answers.
 */
public enum class ModelOriginBadge {
    ON_DEVICE,
    ENTERPRISE_CLOUD,
    NONE,
}

/**
 * Immutable message item consumed by [MessageList] and [MessageBubble].
 */
@Immutable
public data class UiChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val citations: List<Citation> = emptyList(),
    val status: MessageDeliveryStatus = MessageDeliveryStatus.COMPLETE,
    val originBadge: ModelOriginBadge = ModelOriginBadge.NONE,
    val feedbackState: MessageFeedbackState = MessageFeedbackState.NONE,
    val timestampMs: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
)

/**
 * State for the overall [ChatScreen].
 */
@Immutable
public data class ChatUiState(
    val messages: List<UiChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val inputText: String = "",
    val activeCitation: Citation? = null,
    val activeError: String? = null,
    val requiresConsentPrompt: Boolean = false,
    val currentOrigin: ModelOriginBadge = ModelOriginBadge.ON_DEVICE,
)

/**
 * Ingestion state of a document item.
 */
public enum class DocumentStatus {
    QUEUED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXED,
    FAILED,
}

/**
 * Document item representation in [DocumentManagerScreen].
 */
@Immutable
public data class DocumentUiItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: DocumentStatus = DocumentStatus.INDEXED,
    val progressFraction: Float = 1.0f,
    val errorMessage: String? = null,
)

/**
 * State for the [DocumentManagerScreen].
 */
@Immutable
public data class DocumentManagerUiState(
    val documents: List<DocumentUiItem> = emptyList(),
    val isUploading: Boolean = false,
    val searchQuery: String = "",
    val deleteConfirmationDocId: String? = null,
)
