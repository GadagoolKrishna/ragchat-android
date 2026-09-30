package com.ragchat.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragchat.api.model.Citation
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.state.MessageFeedbackState
import com.ragchat.ui.compose.state.MessageRole
import com.ragchat.ui.compose.state.ModelOriginBadge
import com.ragchat.ui.compose.state.UiChatMessage
import com.ragchat.ui.compose.theme.RagChatTheme

/**
 * Message bubble with user/assistant visual distinction, Markdown rendering (code blocks & tables),
 * citations, model source badge, and feedback controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
public fun MessageBubble(
    message: UiChatMessage,
    onCitationClick: (Citation) -> Unit,
    onFeedbackGiven: (String, MessageFeedbackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val bubbleShape =
        if (isUser) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        }

    val backgroundColor =
        if (isUser) {
            RagChatTheme.colors.userBubbleBackground
        } else {
            RagChatTheme.colors.assistantBubbleBackground
        }

    val contentColor =
        if (isUser) {
            RagChatTheme.colors.userBubbleContent
        } else {
            RagChatTheme.colors.assistantBubbleContent
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 340.dp)
                    .clip(bubbleShape)
                    .background(backgroundColor)
                    .padding(12.dp),
        ) {
            Column {
                if (!isUser && message.originBadge != ModelOriginBadge.NONE) {
                    val originText =
                        if (message.originBadge == ModelOriginBadge.ON_DEVICE) {
                            stringResource(R.string.ragchat_model_on_device)
                        } else {
                            stringResource(R.string.ragchat_model_cloud)
                        }
                    val originColor =
                        if (message.originBadge == ModelOriginBadge.ON_DEVICE) {
                            RagChatTheme.colors.modelBadgeOnDevice
                        } else {
                            RagChatTheme.colors.modelBadgeCloud
                        }

                    Text(
                        text = "🔒 $originText",
                        style = RagChatTheme.typography.modelBadge,
                        color = originColor,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                MarkdownContent(
                    content = message.content,
                    textColor = contentColor,
                )

                if (!isUser && message.citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        message.citations.forEach { citation ->
                            CitationChip(
                                citation = citation,
                                onClick = onCitationClick,
                            )
                        }
                    }
                }
            }
        }

        if (!isUser) {
            FeedbackControls(
                state = message.feedbackState,
                onThumbsUp = { onFeedbackGiven(message.id, MessageFeedbackState.HELPFUL) },
                onThumbsDown = { onFeedbackGiven(message.id, MessageFeedbackState.UNHELPFUL) },
                onReport = { onFeedbackGiven(message.id, MessageFeedbackState.UNHELPFUL) },
            )
        }
    }
}

/**
 * Parses and renders Markdown text supporting paragraphs, fenced code blocks, and simple Markdown tables.
 */
@Composable
internal fun MarkdownContent(
    content: String,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val segments = parseMarkdownSegments(content)

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                }
                is MarkdownSegment.CodeBlock -> {
                    CodeBlock(
                        code = segment.code,
                        language = segment.language,
                    )
                }
                is MarkdownSegment.Table -> {
                    MarkdownTable(
                        headers = segment.headers,
                        rows = segment.rows,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
internal fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RagChatTheme.colors.codeBlockBackground)
                .padding(8.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = RagChatTheme.colors.codeBlockContent.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = code,
                style = RagChatTheme.typography.codeBlock,
                color = RagChatTheme.colors.codeBlockContent,
            )
        }
    }
}

@Composable
internal fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(RagChatTheme.colors.tableHeaderBackground.copy(alpha = 0.3f))
                .padding(4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(RagChatTheme.colors.tableHeaderBackground)
                    .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            headers.forEach { h ->
                Text(
                    text = h.trim(),
                    style = RagChatTheme.typography.tableText,
                    fontWeight = FontWeight.Bold,
                    color = RagChatTheme.colors.assistantBubbleContent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = RagChatTheme.colors.tableBorder)

        rows.forEach { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell.trim(),
                        style = RagChatTheme.typography.tableText,
                        color = RagChatTheme.colors.assistantBubbleContent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = RagChatTheme.colors.tableBorder.copy(alpha = 0.5f))
        }
    }
}

internal sealed interface MarkdownSegment {
    data class Text(
        val text: String,
    ) : MarkdownSegment

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MarkdownSegment

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : MarkdownSegment
}

@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "ComplexCondition")
internal fun parseMarkdownSegments(raw: String): List<MarkdownSegment> {
    val results = mutableListOf<MarkdownSegment>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        if (line.trim().startsWith("```")) {
            val language =
                line
                    .trim()
                    .removePrefix("```")
                    .trim()
                    .ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            results.add(MarkdownSegment.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        val isTableStart =
            line.trim().startsWith("|") &&
                line.trim().endsWith("|") &&
                i + 1 < lines.size &&
                lines[i + 1].contains("|-")

        if (isTableStart) {
            val headers = line.split("|").filter { it.isNotBlank() }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = lines[i].split("|").filter { it.isNotBlank() }
                rows.add(cells)
                i++
            }
            results.add(MarkdownSegment.Table(headers, rows))
            continue
        }

        val textLines = mutableListOf<String>()
        while (i < lines.size && !isSpecialBlockLine(lines[i])) {
            textLines.add(lines[i])
            i++
        }
        val text = textLines.joinToString("\n").trim()
        if (text.isNotEmpty()) {
            results.add(MarkdownSegment.Text(text))
        }
    }

    return results.ifEmpty { listOf(MarkdownSegment.Text(raw)) }
}

private fun isSpecialBlockLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("```") || (trimmed.startsWith("|") && trimmed.endsWith("|"))
}
