package com.ragchat.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.components.ChatScreen
import com.ragchat.ui.compose.components.DocumentManagerScreen
import com.ragchat.ui.compose.components.EmptyState
import com.ragchat.ui.compose.components.InputBar
import com.ragchat.ui.compose.components.MessageBubble
import com.ragchat.ui.compose.state.ChatUiState
import com.ragchat.ui.compose.state.DocumentManagerUiState
import com.ragchat.ui.compose.state.DocumentStatus
import com.ragchat.ui.compose.state.DocumentUiItem
import com.ragchat.ui.compose.state.MessageDeliveryStatus
import com.ragchat.ui.compose.state.MessageRole
import com.ragchat.ui.compose.state.ModelOriginBadge
import com.ragchat.ui.compose.state.UiChatMessage
import com.ragchat.ui.compose.theme.RagChatTheme

@Preview(name = "MessageBubble Light", showBackground = true)
@Composable
public fun PreviewMessageBubbleLight() {
    RagChatTheme(darkTheme = false) {
        MessageBubble(
            message =
                UiChatMessage(
                    id = "1",
                    role = MessageRole.ASSISTANT,
                    content = "Code snippet:\n```kotlin\nval rag = RagChat.chat\n```",
                    citations =
                        listOf(
                            Citation("doc_1", "chunk_1", "Chunk summary text", 3),
                        ),
                    originBadge = ModelOriginBadge.ON_DEVICE,
                ),
            onCitationClick = {},
            onFeedbackGiven = { _, _ -> },
        )
    }
}

@Preview(name = "MessageBubble Dark", showBackground = true)
@Composable
public fun PreviewMessageBubbleDark() {
    RagChatTheme(darkTheme = true) {
        MessageBubble(
            message =
                UiChatMessage(
                    id = "2",
                    role = MessageRole.ASSISTANT,
                    content = "Dark theme assistant message with grounded context.",
                    citations =
                        listOf(
                            Citation("doc_2", "chunk_2", "Section 4.1 text", 1),
                        ),
                    originBadge = ModelOriginBadge.ENTERPRISE_CLOUD,
                ),
            onCitationClick = {},
            onFeedbackGiven = { _, _ -> },
        )
    }
}

@Preview(name = "InputBar Preview", showBackground = true)
@Composable
public fun PreviewInputBar() {
    RagChatTheme {
        InputBar(
            text = "What were the Q3 earnings?",
            onTextChange = {},
            onSend = {},
            onAttach = {},
            onStop = {},
            isGenerating = false,
        )
    }
}

@Preview(name = "EmptyState Preview", showBackground = true)
@Composable
public fun PreviewEmptyState() {
    RagChatTheme {
        EmptyState(onSuggestionClick = {})
    }
}

@Preview(name = "ChatScreen Full Preview", showBackground = true)
@Composable
public fun PreviewChatScreen() {
    val sampleState =
        ChatUiState(
            messages =
                listOf(
                    UiChatMessage("1", MessageRole.USER, "What are the key terms in the agreement?"),
                    UiChatMessage(
                        "2",
                        MessageRole.ASSISTANT,
                        "The agreement specifies confidentiality terms under Section 3.",
                        citations = listOf(Citation("doc_1", "chunk_1", "Confidentiality clause", 2)),
                        originBadge = ModelOriginBadge.ON_DEVICE,
                        status = MessageDeliveryStatus.COMPLETE,
                    ),
                ),
            inputText = "",
            isGenerating = false,
            currentOrigin = ModelOriginBadge.ON_DEVICE,
        )

    RagChatTheme {
        ChatScreen(
            state = sampleState,
            onInputTextChange = {},
            onSend = {},
            onStop = {},
            onAttach = {},
            onCitationClick = {},
            onDismissCitationSheet = {},
            onGrantConsent = {},
            onFeedbackGiven = { _, _ -> },
            onDismissError = {},
        )
    }
}

@Preview(name = "DocumentManager Preview", showBackground = true)
@Composable
public fun PreviewDocumentManager() {
    val sampleState =
        DocumentManagerUiState(
            documents =
                listOf(
                    DocumentUiItem("1", "FinancialReport.pdf", "application/pdf", 1024 * 1024 * 3, DocumentStatus.INDEXED),
                    DocumentUiItem("2", "ArchitectureGuide.pdf", "application/pdf", 1024 * 512, DocumentStatus.EMBEDDING, 0.65f),
                ),
        )

    RagChatTheme {
        DocumentManagerScreen(
            state = sampleState,
            onUploadClick = {},
            onDeleteClick = {},
            onConfirmDelete = {},
            onDismissDeleteDialog = {},
            onBackClick = {},
        )
    }
}
