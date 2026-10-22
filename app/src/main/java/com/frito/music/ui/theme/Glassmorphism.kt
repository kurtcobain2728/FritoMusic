package com.frito.music.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Glassmorphism and Radiant Glow styling helpers for Frito Music.
 * Mirrors Flutter's BackdropFilter / BoxShadow styling with hardware GPU acceleration in Compose.
 */
object Glassmorphism {
    val emeraldAccent = Color(0xFF1DB954)
    val emeraldGradientStart = Color(0xFF22C55E)
    val emeraldGradientEnd = Color(0xFF16A34A)
    val mintTranslation = Color(0xFF6EE7B7)
    val favoritePink = Color(0xFFFF4757)

    fun glassSurfaceColor(isDark: Boolean): Color {
        return if (isDark) Color(0xD817171C) else Color(0xF2F8F8FC)
    }

    fun glassBorderColor(isDark: Boolean, isHighlighted: Boolean = false): Color {
        return if (isHighlighted) {
            emeraldAccent.copy(alpha = 0.45f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        }
    }
}

/**
 * Applies a soft glowing shadow behind the composable using native Android blur filter.
 */
fun Modifier.glowShadow(
    color: Color,
    alpha: Float = 0.35f,
    blurRadius: Dp = 20.dp,
    spreadRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier = this.drawBehind {
    val blurRadiusPx = blurRadius.toPx()
    if (blurRadiusPx <= 0f) return@drawBehind

    val spreadPx = spreadRadius.toPx()
    val offsetXPx = offsetX.toPx()
    val offsetYPx = offsetY.toPx()

    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            this.color = color.copy(alpha = alpha).toArgb()
            this.isAntiAlias = true
            this.maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }

        val left = offsetXPx - spreadPx
        val top = offsetYPx - spreadPx
        val right = size.width + offsetXPx + spreadPx
        val bottom = size.height + offsetYPx + spreadPx

        canvas.nativeCanvas.drawRoundRect(
            left, top, right, bottom,
            blurRadiusPx / 2f, blurRadiusPx / 2f,
            paint
        )
    }
}

/**
 * Wraps a composable in a glassmorphic container with shape, surface tint, and subtle translucent border.
 */
fun Modifier.glassContainer(
    shape: Shape = RoundedCornerShape(24.dp),
    isDark: Boolean = true,
    isHighlighted: Boolean = false,
    borderWidth: Dp = 1.2.dp,
    backgroundColor: Color? = null
): Modifier = this
    .clip(shape)
    .background(backgroundColor ?: Glassmorphism.glassSurfaceColor(isDark))
    .border(
        width = borderWidth,
        color = Glassmorphism.glassBorderColor(isDark, isHighlighted),
        shape = shape
    )
