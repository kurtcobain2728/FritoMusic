package com.frito.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

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

@Composable
fun FritoMusicTheme(
    themeMode: String,
    accentColorValue: Long,
    backgroundImageUri: String?,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColorValue)

    val (background, surface, textPrimary, textSecondary) = when (themeMode) {
        "Color predominante" -> {
            // Un color un poco más oscuro o claro basado en el acento
            val isAccentDark = (accent.red * 0.299 + accent.green * 0.587 + accent.blue * 0.114) < 0.5
            val bgColor = accent.copy(alpha = 0.15f) // Mezcla sutil con el fondo
            val sfcColor = accent.copy(alpha = 0.25f)
            
            // Si tiene fondo transparente global por la imagen
            if (backgroundImageUri != null) {
                listOf(Color.Transparent, Color(0x80000000), Color.White, Color.LightGray)
            } else {
                listOf(
                    if (isDark) Color(0xFF121212) else Color(0xFFF5F5F5), // Usaremos el acento mezclado en la app
                    if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF),
                    if (isDark) Color.White else Color.Black,
                    if (isDark) Color.Gray else Color.DarkGray
                )
            }
        }
        else -> {
            if (backgroundImageUri != null) {
                // Background transparent so image shows
                listOf(Color.Transparent, if(isDark) Color(0xAA121212) else Color(0xAAFFFFFF), if(isDark) Color.White else Color.Black, if(isDark) Color.Gray else Color.DarkGray)
            } else if (isDark) {
                listOf(Color(0xFF121212), Color(0xFF1A1A1A), Color.White, Color.Gray)
            } else {
                listOf(Color(0xFFF5F5F5), Color(0xFFFFFFFF), Color.Black, Color.DarkGray)
            }
        }
    }

    // Sobre-escribir fondo si es color predominante y NO hay imagen
    val finalBackground = if (themeMode == "Color predominante" && backgroundImageUri == null) {
        // Un tono súper claro del acento si es de día, o súper oscuro si es de noche
        if (isDark) Color(0xFF121212) else Color(0xFFF0F0F0)
    } else background

    val appColors = AppColors(
        background = finalBackground,
        surface = surface,
        accent = accent,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        isDark = isDark,
        themeMode = themeMode,
        backgroundImageUri = backgroundImageUri
    )

    CompositionLocalProvider(LocalAppColors provides appColors) {
        content()
    }
}
