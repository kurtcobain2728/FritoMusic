package com.frito.music.ui.theme

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("frito_theme_prefs", Context.MODE_PRIVATE)

    // Valores por defecto
    private val defaultThemeMode = "Oscuro"
    private val defaultAccentColor = 0xFF1DB954 // Green

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", defaultThemeMode) ?: defaultThemeMode)
    val themeMode = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(prefs.getLong("accent_color", defaultAccentColor))
    val accentColor = _accentColor.asStateFlow()

    private val _backgroundImageUri = MutableStateFlow<String?>(prefs.getString("background_image_uri", null))
    val backgroundImageUri = _backgroundImageUri.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setAccentColor(colorHex: Long) {
        _accentColor.value = colorHex
        prefs.edit().putLong("accent_color", colorHex).apply()
    }

    fun setBackgroundImage(uri: String?) {
        _backgroundImageUri.value = uri
        prefs.edit().putString("background_image_uri", uri).apply()
    }

    fun isDarkThemeActive(): Boolean {
        return when (_themeMode.value) {
            "Claro" -> false
            "Oscuro" -> true
            "Automático" -> {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                currentHour >= 18 || currentHour < 6 // Dark from 6 PM to 6 AM
            }
            "Color predominante" -> false // Para color predominante, solemos usar texto oscuro (claro)
            else -> true
        }
    }
}
