package com.mangascroll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A circle with a scalloped ("cookie") edge: [lobes] bumps, [depth] = how deep the dips are. */
class CookieShape(private val lobes: Int = 9, private val depth: Float = 0.08f, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(cookiePath(size, Offset.Zero, lobes, depth, rotation))
}

fun cookiePath(size: Size, offset: Offset, lobes: Int, depth: Float, rotation: Float = 0f): Path {
    val cx = offset.x + size.width / 2
    val cy = offset.y + size.height / 2
    val r = min(size.width, size.height) / 2
    val steps = 180
    return Path().apply {
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * PI.toFloat()
            // smooth bumps: radius dips by `depth` between lobes
            val rr = r * (1f - depth * (0.5f - 0.5f * cos(lobes * (t + rotation))))
            val x = cx + rr * cos(t)
            val y = cy + rr * sin(t)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Icon sitting in a soft scalloped badge, like the category icons. */
@Composable
fun CookieBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    lobes: Int = 9,
) {
    Box(
        modifier.size(size).clip(CookieShape(lobes, 0.12f)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Large soft blob peeking in from a card corner (decoration only). */
@Composable
fun CornerBlob(color: Color, modifier: Modifier = Modifier, lobes: Int = 6, depth: Float = 0.18f) {
    Canvas(modifier) {
        drawPath(cookiePath(size, Offset.Zero, lobes, depth, rotation = 0.3f), color)
    }
}
