package com.mangascroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

data class Accent(val name: String, val seed: Color)

/** Accent colors offered in Look & feel. The scheme is generated from the seed. */
val Accents = listOf(
    Accent("Indigo", Color(0xFF5B5BD6)),
    Accent("Coral", Color(0xFFFF6B6B)),
    Accent("Purple", Color(0xFFA855F7)),
    Accent("Pink", Color(0xFFEC4899)),
    Accent("Orange", Color(0xFFF97316)),
    Accent("Gold", Color(0xFFEAB308)),
    Accent("Green", Color(0xFF22C55E)),
    Accent("Teal", Color(0xFF14B8A6)),
    Accent("Sky", Color(0xFF38BDF8)),
)

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

/**
 * Older code refers to these names; they now follow the current theme.
 * Coral = the accent, Amber/Violet = the secondary tones, Mint = "done/read" green.
 */
object Palette {
    val Coral: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val Amber: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
    val Violet: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
    val Mint: Color @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.isDark) Color(0xFF6EE7B7) else Color(0xFF059669)
    val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
    val Card: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
    val CardHigh: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Line: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
    val Text: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
    val TextDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Sunset: Brush @Composable @ReadOnlyComposable get() = SolidColor(MaterialTheme.colorScheme.primary)
    val SunsetH: Brush @Composable @ReadOnlyComposable get() = SolidColor(MaterialTheme.colorScheme.primary)
}

// ---------- fonts ----------

private fun res(name: String, weight: Int) = Font("font/${name}_$weight.ttf", FontWeight(weight))
private val SpaceGrotesk = FontFamily((400..700 step 100).map { res("spacegrotesk", it) })
private val Outfit = FontFamily((400..800 step 100).map { res("outfit", it) })

val FontChoices = listOf("space" to "Space Grotesk", "outfit" to "Outfit", "system" to "System")

fun familyFor(id: String): FontFamily = when (id) {
    "outfit" -> Outfit
    "system" -> FontFamily.Default
    else -> SpaceGrotesk
}

private fun typographyFor(f: FontFamily): Typography {
    fun s(size: Int, weight: Int, line: Int, spacing: Float = 0f) = TextStyle(
        fontFamily = f, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp,
    )
    return Typography(
        displayLarge = s(52, 400, 60, -1f),
        displayMedium = s(44, 400, 52, -0.5f),
        displaySmall = s(36, 400, 44, -0.5f),
        headlineLarge = s(32, 400, 40),
        headlineMedium = s(28, 500, 36),
        headlineSmall = s(24, 500, 32),
        titleLarge = s(22, 500, 28),
        titleMedium = s(16, 500, 22, 0.1f),
        titleSmall = s(14, 500, 20, 0.1f),
        bodyLarge = s(16, 400, 24, 0.2f),
        bodyMedium = s(14, 400, 20, 0.2f),
        bodySmall = s(12, 400, 16, 0.3f),
        labelLarge = s(14, 600, 20, 0.2f),
        labelMedium = s(12, 600, 16, 0.4f),
        labelSmall = s(11, 500, 14, 0.4f),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun ColorScheme.blackened() = copy(
    background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.7f),
)

/** Builds the color scheme for the user's settings. */
@Composable
fun rememberScheme(settings: AppSettings): ColorScheme {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        "light" -> false
        "dark", "black" -> true
        else -> systemDark
    }
    val black = settings.theme == "black"
    val seed = Accents.getOrElse(settings.accent) { Accents[0] }.seed
    return remember(dark, black, seed) {
        val base = dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = PaletteStyle.TonalSpot)
        if (black) base.blackened() else base
    }
}

@Composable
fun MangaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val scheme = rememberScheme(settings)
    val type = remember(settings.font) { typographyFor(familyFor(settings.font)) }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalTextStyle provides type.bodyMedium,
            content = content,
        )
    }
}

/** Big pill button in the accent color (the app's main action button). */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) c.primary else c.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) c.onPrimary else c.onSurfaceVariant,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
