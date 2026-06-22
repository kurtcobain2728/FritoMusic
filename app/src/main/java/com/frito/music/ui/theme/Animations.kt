package com.frito.music.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Constantes de animación reutilizables para toda la app Frito Music.
 * Usar estas especificaciones garantiza consistencia visual en todas las pantallas.
 */
object AppAnimations {

    // --- Duraciones ---
    const val DURATION_FAST = 180
    const val DURATION_MEDIUM = 280
    const val DURATION_SLOW = 420
    const val DURATION_PLAYER = 380

    // --- Tweens con easing ---
    val fastTween: TweenSpec<Float> get() = tween(DURATION_FAST, easing = FastOutSlowInEasing)
    val mediumTween: TweenSpec<Float> get() = tween(DURATION_MEDIUM, easing = FastOutSlowInEasing)
    val slowTween: TweenSpec<Float> get() = tween(DURATION_SLOW, easing = FastOutSlowInEasing)

    // --- Springs ---
    /** Spring sin rebote, ideal para pantallas y overlays */
    val noBounceSpring: SpringSpec<Float> get() = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Spring suave con leve rebote, ideal para botones y FABs */
    val softBounceSpring: SpringSpec<Float> get() = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // --- Int variants (para offsets de pantalla) ---
    fun screenSlideTween() = tween<Int>(DURATION_MEDIUM, easing = FastOutSlowInEasing)
    fun playerSlideTween() = tween<Int>(DURATION_PLAYER, easing = FastOutSlowInEasing)

    // --- Float variants para alpha/scale ---
    fun fadeTween() = tween<Float>(DURATION_MEDIUM, easing = FastOutSlowInEasing)
    fun quickFadeTween() = tween<Float>(DURATION_FAST, easing = FastOutSlowInEasing)
}
