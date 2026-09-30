package com.ragchat.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ragchat.ui.compose.R

/**
 * Friendly empty state with starter prompt suggestions.
 *
 * @param onSuggestionClick Callback when a prompt suggestion is clicked.
 * @param modifier Composable modifier.
 */
@Composable
public fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "💬",
            style = MaterialTheme.typography.displayMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ragchat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.ragchat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        val prompt1 = stringResource(R.string.ragchat_suggestion_summarize)
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSuggestionClick(prompt1) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(text = "✨ $prompt1", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val prompt2 = stringResource(R.string.ragchat_suggestion_key_facts)
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSuggestionClick(prompt2) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(text = "🔍 $prompt2", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Error banner with retry and dismiss options.
 *
 * @param error Error message to display.
 * @param onRetry Callback when retry button is pressed.
 * @param onDismiss Callback when dismiss is pressed.
 * @param modifier Composable modifier.
 */
@Composable
public fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ragchat_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(text = stringResource(R.string.ragchat_close))
                }
                Button(
                    onClick = onRetry,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(text = stringResource(R.string.ragchat_retry))
                }
            }
        }
    }
}
