package com.ragchat.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Extended color tokens specific to RagChat enterprise chat and RAG components.
 */
@Immutable
public data class RagChatColors(
    val userBubbleBackground: Color,
    val userBubbleContent: Color,
    val assistantBubbleBackground: Color,
    val assistantBubbleContent: Color,
    val codeBlockBackground: Color,
    val codeBlockContent: Color,
    val citationChipBackground: Color,
    val citationChipContent: Color,
    val modelBadgeOnDevice: Color,
    val modelBadgeCloud: Color,
    val statusBannerBackground: Color,
    val tableBorder: Color,
    val tableHeaderBackground: Color,
)

/**
 * Extended typography tokens for code blocks, tables, and citations.
 */
@Immutable
public data class RagChatTypography(
    val codeBlock: TextStyle,
    val citationText: TextStyle,
    val modelBadge: TextStyle,
    val tableText: TextStyle,
)

/**
 * Layout and spacing tokens following standard 4dp/8dp grid.
 */
@Immutable
public data class RagChatSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val default: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val minTouchTarget: Dp = 48.dp,
)

public val LocalRagChatColors: ProvidableCompositionLocal<RagChatColors> =
    staticCompositionLocalOf {
        error("No RagChatColors provided")
    }

public val LocalRagChatTypography: ProvidableCompositionLocal<RagChatTypography> =
    staticCompositionLocalOf {
        error("No RagChatTypography provided")
    }

public val LocalRagChatSpacing: ProvidableCompositionLocal<RagChatSpacing> =
    staticCompositionLocalOf {
        RagChatSpacing()
    }

private val LightRagChatColors =
    RagChatColors(
        userBubbleBackground = Color(0xFF005AC1),
        userBubbleContent = Color.White,
        assistantBubbleBackground = Color(0xFFF0F4FA),
        assistantBubbleContent = Color(0xFF191C1E),
        codeBlockBackground = Color(0xFF1E293B),
        codeBlockContent = Color(0xFFE2E8F0),
        citationChipBackground = Color(0xFFE1E9F5),
        citationChipContent = Color(0xFF0A58CA),
        modelBadgeOnDevice = Color(0xFF137333),
        modelBadgeCloud = Color(0xFF1A73E8),
        statusBannerBackground = Color(0xFFF8F9FA),
        tableBorder = Color(0xFFD1D5DB),
        tableHeaderBackground = Color(0xFFE5E7EB),
    )

private val DarkRagChatColors =
    RagChatColors(
        userBubbleBackground = Color(0xFF90CAF9),
        userBubbleContent = Color(0xFF001E3C),
        assistantBubbleBackground = Color(0xFF262A30),
        assistantBubbleContent = Color(0xFFE2E2E6),
        codeBlockBackground = Color(0xFF0F172A),
        codeBlockContent = Color(0xFFF1F5F9),
        citationChipBackground = Color(0xFF1E3A5F),
        citationChipContent = Color(0xFF8AB4F8),
        modelBadgeOnDevice = Color(0xFF81C995),
        modelBadgeCloud = Color(0xFF8AB4F8),
        statusBannerBackground = Color(0xFF1F2328),
        tableBorder = Color(0xFF374151),
        tableHeaderBackground = Color(0xFF1F2937),
    )

private val DefaultRagChatTypography =
    RagChatTypography(
        codeBlock =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        citationText =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        modelBadge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        tableText =
            TextStyle(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
    )

/**
 * RagChat enterprise theme wrapper providing Material 3 styling and customized chat tokens.
 *
 * @param darkTheme Whether to render in dark theme. Defaults to system setting.
 * @param customColors Optional host-supplied [RagChatColors] overrides.
 * @param content Target Composable UI.
 */
@Composable
public fun RagChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColors: RagChatColors? = null,
    content: @Composable () -> Unit,
) {
    val extendedColors = customColors ?: if (darkTheme) DarkRagChatColors else LightRagChatColors
    val m3Colors = if (darkTheme) darkColorScheme() else lightColorScheme()

    CompositionLocalProvider(
        LocalRagChatColors provides extendedColors,
        LocalRagChatTypography provides DefaultRagChatTypography,
        LocalRagChatSpacing provides RagChatSpacing(),
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content = content,
        )
    }
}

/**
 * Accessor object for RagChat theme tokens within Composables.
 */
public object RagChatTheme {
    public val colors: RagChatColors
        @Composable
        get() = LocalRagChatColors.current

    public val typography: RagChatTypography
        @Composable
        get() = LocalRagChatTypography.current

    public val spacing: RagChatSpacing
        @Composable
        get() = LocalRagChatSpacing.current
}
