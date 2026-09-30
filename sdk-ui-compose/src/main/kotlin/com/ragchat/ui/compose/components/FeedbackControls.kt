package com.ragchat.ui.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.state.MessageFeedbackState
import com.ragchat.ui.compose.theme.RagChatTheme

/**
 * Assistant message feedback controls (thumbs up, thumbs down, report).
 *
 * @param state Current user feedback state.
 * @param onThumbsUp Callback when helpful button is clicked.
 * @param onThumbsDown Callback when unhelpful button is clicked.
 * @param onReport Callback when report issue button is clicked.
 * @param modifier Composable modifier.
 */
@Composable
public fun FeedbackControls(
    state: MessageFeedbackState,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == MessageFeedbackState.NONE) {
            IconButton(
                onClick = onThumbsUp,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("👍")
            }
            IconButton(
                onClick = onThumbsDown,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("👎")
            }
            IconButton(
                onClick = onReport,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("⚠️")
            }
        } else {
            Text(
                text = stringResource(R.string.ragchat_feedback_submitted),
                style = RagChatTheme.typography.citationText,
                color = RagChatTheme.colors.assistantBubbleContent.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
