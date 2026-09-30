package com.ragchat.ui.compose.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.state.MessageFeedbackState
import com.ragchat.ui.compose.state.UiChatMessage

/**
 * Scrollable list of chat messages with auto-scrolling to bottom on new items or streaming tokens.
 *
 * @param messages Ordered list of chat messages.
 * @param onCitationClick Callback when a citation is selected.
 * @param onFeedbackGiven Callback when feedback is given on a message.
 * @param modifier Composable modifier.
 * @param listState Optional [LazyListState].
 */
@Composable
public fun MessageList(
    messages: List<UiChatMessage>,
    onCitationClick: (Citation) -> Unit,
    onFeedbackGiven: (String, MessageFeedbackState) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = messages,
            key = { it.id },
        ) { message ->
            MessageBubble(
                message = message,
                onCitationClick = onCitationClick,
                onFeedbackGiven = onFeedbackGiven,
            )
        }
    }
}
