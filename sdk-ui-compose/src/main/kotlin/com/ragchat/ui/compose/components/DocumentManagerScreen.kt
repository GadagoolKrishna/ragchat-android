package com.ragchat.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.state.DocumentManagerUiState
import com.ragchat.ui.compose.state.DocumentStatus
import com.ragchat.ui.compose.state.DocumentUiItem
import com.ragchat.ui.compose.theme.RagChatTheme
import java.util.Locale

/**
 * Screen displaying uploaded documents, ingestion progress, status badges, and deletion controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
public fun DocumentManagerScreen(
    state: DocumentManagerUiState,
    onUploadClick: () -> Unit,
    onDeleteClick: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ragchat_doc_manager_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    Button(
                        onClick = onUploadClick,
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.ragchat_upload_document))
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
            if (state.documents.isEmpty()) {
                EmptyDocumentsPlaceholder()
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onDelete = { onDeleteClick(doc.id) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    state.deleteConfirmationDocId?.let { docId ->
        DeleteConfirmationDialog(
            docId = docId,
            onConfirmDelete = onConfirmDelete,
            onDismiss = onDismissDeleteDialog,
        )
    }
}

@Composable
internal fun EmptyDocumentsPlaceholder() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📄", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No documents indexed yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Upload PDF or text files to begin semantic querying.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    docId: String,
    onConfirmDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ragchat_delete)) },
        text = { Text(text = stringResource(R.string.ragchat_confirm_delete)) },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(docId) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(text = stringResource(R.string.ragchat_delete))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(text = stringResource(R.string.ragchat_cancel))
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
internal fun DocumentItemCard(
    document: DocumentUiItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${document.mimeType} • ${formatBytes(document.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier =
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                this.contentDescription = "Delete ${document.name}"
                                this.role = Role.Button
                            },
                ) {
                    Text("🗑️")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val isProcessing =
                document.status != DocumentStatus.INDEXED &&
                    document.status != DocumentStatus.FAILED

            if (isProcessing) {
                LinearProgressIndicator(
                    progress = { document.progressFraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val statusText =
                when (document.status) {
                    DocumentStatus.INDEXED -> stringResource(R.string.ragchat_status_ready)
                    DocumentStatus.FAILED -> stringResource(R.string.ragchat_status_failed)
                    else -> stringResource(R.string.ragchat_status_indexing)
                }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color =
                    when (document.status) {
                        DocumentStatus.INDEXED -> RagChatTheme.colors.modelBadgeOnDevice
                        DocumentStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
