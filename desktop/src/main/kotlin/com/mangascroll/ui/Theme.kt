package com.mangascroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: deep ink background with a coral -> amber "sunset" accent.
object Palette {
    val Ink = Color(0xFF0E0E13)
    val Surface = Color(0xFF16161D)
    val Card = Color(0xFF1E1E28)
    val CardHigh = Color(0xFF272734)
    val Line = Color(0xFF2E2E3D)
    val Text = Color(0xFFF3F3F8)
    val TextDim = Color(0xFF9E9EB3)
    val Coral = Color(0xFFFF6B6B)
    val Amber = Color(0xFFFFA94D)
    val Violet = Color(0xFF9775FA)
    val Mint = Color(0xFF63E6BE)

    val Sunset = Brush.linearGradient(listOf(Coral, Amber))
    val SunsetH = Brush.horizontalGradient(listOf(Coral, Amber))
}

private fun outfit(weight: Int) = Font("font/outfit_$weight.ttf", FontWeight(weight))

val Outfit = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700), outfit(800))

private fun style(size: Int, weight: Int, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Outfit, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    displaySmall = style(34, 800, 40, -0.5f),
    headlineMedium = style(28, 700, 34, -0.3f),
    headlineSmall = style(24, 700, 30, -0.2f),
    titleLarge = style(20, 700, 26),
    titleMedium = style(16, 600, 22),
    titleSmall = style(14, 600, 20),
    bodyLarge = style(16, 400, 24),
    bodyMedium = style(14, 400, 20),
    bodySmall = style(12, 400, 16),
    labelLarge = style(14, 600, 20, 0.1f),
    labelMedium = style(12, 600, 16, 0.3f),
    labelSmall = style(11, 500, 14, 0.4f),
)

private val AppColors = darkColorScheme(
    primary = Palette.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1F24),
    onPrimaryContainer = Color(0xFFFFD9D6),
    secondary = Palette.Amber,
    tertiary = Palette.Violet,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.CardHigh,
    surfaceContainerHighest = Palette.CardHigh,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Color(0xFFFF8787),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MangaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides AppColors.onBackground,
            androidx.compose.material3.LocalTextStyle provides AppTypography.bodyMedium,
            content = content,
        )
    }
}

/** Pill-shaped button filled with the sunset gradient. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Palette.SunsetH else Brush.horizontalGradient(listOf(Palette.CardHigh, Palette.CardHigh)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Color.White,
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

