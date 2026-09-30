package com.ragchat.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.theme.RagChatTheme

/**
 * Bottom sheet modal previewing citation source documents and highlighting retrieved chunks.
 *
 * @param citation Selected citation to view. If null, sheet is hidden.
 * @param onDismiss Callback when sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SourceViewerSheet(
    citation: Citation?,
    onDismiss: () -> Unit,
) {
    if (citation == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SourceViewerContent(citation = citation)
    }
}

@Composable
internal fun SourceViewerContent(
    citation: Citation,
    modifier: Modifier = Modifier,
) {
    val docTitle = citation.metadata["name"] ?: citation.documentId
    val pageNum = citation.pageNumber

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.ragchat_source_viewer_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = docTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (pageNum != null) {
            Text(
                text = stringResource(R.string.ragchat_citation_page_format, pageNum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.ragchat_chunk_highlighted_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val chunkContent = citation.textSnippet ?: "Chunk ID: ${citation.chunkId}"

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RagChatTheme.colors.citationChipBackground.copy(alpha = 0.5f))
                    .padding(16.dp),
        ) {
            Text(
                text = chunkContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
