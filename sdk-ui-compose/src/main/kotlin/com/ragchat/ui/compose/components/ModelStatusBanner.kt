package com.ragchat.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ragchat.ui.compose.R
import com.ragchat.ui.compose.state.ModelOriginBadge
import com.ragchat.ui.compose.theme.RagChatTheme

/**
 * Top banner indicating model execution route (on-device vs enterprise cloud)
 * and presenting cloud consent actions when required.
 *
 * @param origin Current model execution origin.
 * @param requiresConsent Whether consent needs to be granted for cloud processing.
 * @param onGrantConsent Callback triggered when user taps consent button.
 * @param modifier Composable modifier.
 */
@Composable
public fun ModelStatusBanner(
    origin: ModelOriginBadge,
    requiresConsent: Boolean,
    onGrantConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = requiresConsent || origin != ModelOriginBadge.NONE,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(RagChatTheme.colors.statusBannerBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusText =
                when {
                    requiresConsent -> stringResource(R.string.ragchat_consent_required_banner)
                    origin == ModelOriginBadge.ON_DEVICE -> stringResource(R.string.ragchat_model_on_device)
                    origin == ModelOriginBadge.ENTERPRISE_CLOUD -> stringResource(R.string.ragchat_model_cloud)
                    else -> ""
                }

            val badgeColor =
                when (origin) {
                    ModelOriginBadge.ON_DEVICE -> RagChatTheme.colors.modelBadgeOnDevice
                    ModelOriginBadge.ENTERPRISE_CLOUD -> RagChatTheme.colors.modelBadgeCloud
                    else -> RagChatTheme.colors.modelBadgeOnDevice
                }

            Text(
                text = statusText,
                style = RagChatTheme.typography.modelBadge,
                color = badgeColor,
                modifier = Modifier.weight(1f),
            )

            if (requiresConsent) {
                Button(
                    onClick = onGrantConsent,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(text = stringResource(R.string.ragchat_grant_consent))
                }
            }
        }
    }
}
