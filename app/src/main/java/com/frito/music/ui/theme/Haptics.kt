package com.frito.music.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight tactile haptic feedback utility for Compose.
 * Mirrors Flutter's HapticFeedback.lightImpact() and mediumImpact().
 */
class HapticHelper(private val view: View) {
    fun light() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    fun medium() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) {}
    }

    fun tick() {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Exception) {}
    }
}

@Composable
fun rememberAppHaptics(): HapticHelper {
    val view = LocalView.current
    return remember(view) { HapticHelper(view) }
}
