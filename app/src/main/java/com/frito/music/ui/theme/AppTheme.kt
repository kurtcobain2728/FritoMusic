package com.frito.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

data class AppColors(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean,
    val themeMode: String,
    val backgroundImageUri: String?
)

val LocalAppColors = compositionLocalOf<AppColors> {
    error("No AppColors provided")
}

/**
 * Calcula la luminancia relativa de un color según el estándar WCAG 2.1.
 * Devuelve un valor entre 0 (negro puro) y 1 (blanco puro).
 */
fun calculateLuminance(color: Color): Double {
    fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val r = linearize(color.red)
    val g = linearize(color.green)
    val b = linearize(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * Calcula si el texto sobre un color debería ser blanco o negro
 * usando la ratio de contraste WCAG (mínimo 4.5:1 para texto normal).
 */
fun textColorForBackground(bgColor: Color): Color {
    val luminance = calculateLuminance(bgColor)
    // Si la luminancia > 0.35, el fondo es "claro" -> texto negro
    // Si la luminancia <= 0.35, el fondo es "oscuro" -> texto blanco
    return if (luminance > 0.35) Color.Black else Color.White
}

/**
 * Crea una versión más oscura o más clara de un color según su luminancia.
 * Si el color es oscuro, lo aclara. Si es claro, lo oscurece.
 * Sirve para generar el color de "superficie" a partir del color predominante.
 */
fun adjustColorForSurface(color: Color): Color {
    val luminance = calculateLuminance(color)
    val argb = color.toArgb()
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    return if (luminance < 0.35) {
        // Color oscuro -> surface será más claro (añadir ~15% de brillo)
        Color(
            red = (r + 0.18f).coerceIn(0f, 1f),
            green = (g + 0.18f).coerceIn(0f, 1f),
            blue = (b + 0.18f).coerceIn(0f, 1f)
        )
    } else {
        // Color claro -> surface será más oscuro (quitar ~15% de brillo)
        Color(
            red = (r - 0.18f).coerceIn(0f, 1f),
            green = (g - 0.18f).coerceIn(0f, 1f),
            blue = (b - 0.18f).coerceIn(0f, 1f)
        )
    }
}

@Composable
fun FritoMusicTheme(
    themeMode: String,
    accentColorValue: Long,
    backgroundImageUri: String?,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColorValue)

    val appColors: AppColors = when (themeMode) {
        "Color predominante" -> {
            // --- LÓGICA INTELIGENTE DE COLOR PREDOMINANTE ---
            val dominantColor = accent
            val luminance = calculateLuminance(dominantColor)
            val isDominantDark = luminance <= 0.35

            // El texto primario se calcula por contraste WCAG
            val textPrimary = textColorForBackground(dominantColor)
            // El texto secundario es igual pero con 60% de opacidad
            val textSecondary = textPrimary.copy(alpha = 0.6f)
            // La superficie es una variación del color dominante
            val surface = adjustColorForSurface(dominantColor)

            AppColors(
                background = dominantColor,
                surface = surface,
                accent = textPrimary, // El acento (botones activos, íconos) sigue el contraste
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isDark = isDominantDark,
                themeMode = themeMode,
                backgroundImageUri = null // En modo predominante, ignoramos imagen de fondo
            )
        }
        else -> {
            // Modo Oscuro, Claro, Automático e imagen de fondo
            if (backgroundImageUri != null) {
                AppColors(
                    background = Color.Transparent,
                    surface = if (isDark) Color(0xAA121212) else Color(0xAAFFFFFF),
                    accent = accent,
                    textPrimary = if (isDark) Color.White else Color.Black,
                    textSecondary = if (isDark) Color.Gray else Color.DarkGray,
                    isDark = isDark,
                    themeMode = themeMode,
                    backgroundImageUri = backgroundImageUri
                )
            } else if (isDark) {
                AppColors(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1A1A1A),
                    accent = accent,
                    textPrimary = Color.White,
                    textSecondary = Color.Gray,
                    isDark = true,
                    themeMode = themeMode,
                    backgroundImageUri = null
                )
            } else {
                AppColors(
                    background = Color(0xFFF5F5F5),
                    surface = Color(0xFFFFFFFF),
                    accent = accent,
                    textPrimary = Color.Black,
                    textSecondary = Color.DarkGray,
                    isDark = false,
                    themeMode = themeMode,
                    backgroundImageUri = null
                )
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        content()
    }
}

