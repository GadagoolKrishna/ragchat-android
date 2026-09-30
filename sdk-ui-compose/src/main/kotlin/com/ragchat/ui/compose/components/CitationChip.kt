package com.ragchat.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.theme.RagChatTheme

/**
 * Clickable citation chip attached to grounded answers.
 *
 * @param citation Grounding citation reference.
 * @param onClick Callback triggered when user clicks this citation.
 * @param modifier Composable modifier.
 */
@Composable
public fun CitationChip(
    citation: Citation,
    onClick: (Citation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val docName = citation.metadata["name"] ?: citation.documentId
    val pageNum = citation.pageNumber
    val pageText = if (pageNum != null) stringResource(R.string.ragchat_citation_page_format, pageNum) else null

    val label =
        if (pageText != null) {
            "${stringResource(R.string.ragchat_citation_prefix)}: $docName ($pageText)"
        } else {
            "${stringResource(R.string.ragchat_citation_prefix)}: $docName"
        }

    val cd = "${stringResource(R.string.ragchat_citation_prefix)} $docName"

    Row(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RagChatTheme.colors.citationChipBackground)
                .clickable(role = Role.Button) { onClick(citation) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics {
                    this.contentDescription = cd
                    this.role = Role.Button
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RagChatTheme.typography.citationText,
            color = RagChatTheme.colors.citationChipContent,
        )
    }
}
