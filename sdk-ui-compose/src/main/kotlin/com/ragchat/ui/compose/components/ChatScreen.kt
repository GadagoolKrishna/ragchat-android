package com.ragchat.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.state.ChatUiState
import com.ragchat.ui.compose.state.MessageFeedbackState

/**
 * Top-level chat screen integrating message stream, model origin banner,
 * grounding citation bottom sheet, error displays, and query input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
public fun ChatScreen(
    state: ChatUiState,
    onInputTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onCitationClick: (Citation) -> Unit,
    onDismissCitationSheet: () -> Unit,
    onGrantConsent: () -> Unit,
    onFeedbackGiven: (String, MessageFeedbackState) -> Unit,
    onDismissError: () -> Unit,
    onManageDocsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ragchat_app_name)) },
                actions = {
                    IconButton(
                        onClick = onManageDocsClick,
                        modifier =
                            Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { this.contentDescription = "Manage documents" },
                    ) {
                        Text("📁", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            ModelStatusBanner(
                origin = state.currentOrigin,
                requiresConsent = state.requiresConsentPrompt,
                onGrantConsent = onGrantConsent,
            )

            state.activeError?.let { err ->
                ErrorState(
                    error = err,
                    onRetry = {
                        val lastQuery =
                            state.messages
                                .lastOrNull {
                                    it.role == com.ragchat.ui.compose.state.MessageRole.USER
                                }?.content
                        if (!lastQuery.isNullOrBlank()) {
                            onSend(lastQuery)
                        }
                    },
                    onDismiss = onDismissError,
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                if (state.messages.isEmpty()) {
                    EmptyState(
                        onSuggestionClick = { prompt ->
                            onInputTextChange(prompt)
                            onSend(prompt)
                        },
                    )
                } else {
                    MessageList(
                        messages = state.messages,
                        onCitationClick = onCitationClick,
                        onFeedbackGiven = onFeedbackGiven,
                    )
                }
            }

            InputBar(
                text = state.inputText,
                onTextChange = onInputTextChange,
                onSend = onSend,
                onAttach = onAttach,
                onStop = onStop,
                isGenerating = state.isGenerating,
            )
        }
    }

    SourceViewerSheet(
        citation = state.activeCitation,
        onDismiss = onDismissCitationSheet,
    )
}
