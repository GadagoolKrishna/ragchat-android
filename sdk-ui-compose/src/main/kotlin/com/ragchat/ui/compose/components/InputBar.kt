package com.ragchat.ui.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ragchat.ui.compose.R

/**
 * Bottom interactive input bar supporting text input, document attachment,
 * message dispatch, and response generation cancellation.
 */
@Suppress("LongParameterList")
@Composable
public fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    val sendDesc = stringResource(R.string.ragchat_send_button_description)
    val attachDesc = stringResource(R.string.ragchat_attach_button_description)
    val stopDesc = stringResource(R.string.ragchat_stop_button_description)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onAttach,
            modifier =
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { this.contentDescription = attachDesc },
        ) {
            Text("📎", style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(text = stringResource(R.string.ragchat_input_hint)) },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
        )

        if (isGenerating) {
            IconButton(
                onClick = onStop,
                modifier =
                    Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { this.contentDescription = stopDesc },
            ) {
                Text("⏹️", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                    }
                },
                enabled = text.isNotBlank(),
                modifier =
                    Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { this.contentDescription = sendDesc },
            ) {
                Text("🚀", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
