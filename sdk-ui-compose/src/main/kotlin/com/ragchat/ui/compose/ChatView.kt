package com.ragchat.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ragchat.ui.compose.components.ChatScreen
import com.ragchat.ui.compose.viewmodel.ChatViewModel

/**
 * High-level drop-in Chat Composable connected to a [ChatViewModel].
 *
 * @param viewModel State holder driving the chat conversation.
 * @param onAttach Callback when user taps document attach icon.
 * @param onManageDocsClick Callback when user navigates to document management.
 * @param modifier Composable modifier.
 */
@Composable
public fun ChatView(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    onAttach: () -> Unit = {},
    onManageDocsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    ChatScreen(
        state = state,
        onInputTextChange = viewModel::onInputTextChanged,
        onSend = viewModel::sendMessage,
        onStop = viewModel::stopGenerating,
        onAttach = onAttach,
        onCitationClick = viewModel::selectCitation,
        onDismissCitationSheet = { viewModel.selectCitation(null) },
        onGrantConsent = viewModel::grantConsent,
        onFeedbackGiven = viewModel::provideFeedback,
        onDismissError = viewModel::dismissError,
        onManageDocsClick = onManageDocsClick,
        modifier = modifier,
    )
}
