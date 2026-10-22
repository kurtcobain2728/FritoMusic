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

    // --- Curvas de fluidez estilo Flutter ---
    val fluidCubicEasing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    val easeOutCubicEasing = androidx.compose.animation.core.CubicBezierEasing(0.215f, 0.61f, 0.355f, 1.0f)

    // --- Tweens con easing ---
    val fastTween: TweenSpec<Float> get() = tween(DURATION_FAST, easing = FastOutSlowInEasing)
    val mediumTween: TweenSpec<Float> get() = tween(DURATION_MEDIUM, easing = FastOutSlowInEasing)
    val slowTween: TweenSpec<Float> get() = tween(DURATION_SLOW, easing = FastOutSlowInEasing)
    val fluidPlayerTween: TweenSpec<Float> get() = tween(DURATION_PLAYER, easing = fluidCubicEasing)

    fun <T> fluidTween(durationMillis: Int = DURATION_MEDIUM) =
        tween<T>(durationMillis, easing = fluidCubicEasing)

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

    /** Spring elástico para indicadores de pestaña (GlassIconBar) */
    val elasticSpring: SpringSpec<Float> get() = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // --- Int variants (para offsets de pantalla) ---
    fun screenSlideTween() = tween<Int>(DURATION_MEDIUM, easing = FastOutSlowInEasing)

    /**
     * Para slideInHorizontally/slideOutHorizontally, que animan un IntOffset
     * (no un Int): usar ESTA variante o el compilador rechazará el spec.
     */
    fun screenSlideOffsetTween() =
        tween<androidx.compose.ui.unit.IntOffset>(DURATION_MEDIUM, easing = FastOutSlowInEasing)

    fun playerSlideTween() =
        tween<androidx.compose.ui.unit.IntOffset>(DURATION_PLAYER, easing = fluidCubicEasing)

    // --- Float variants para alpha/scale ---
    fun fadeTween() = tween<Float>(DURATION_MEDIUM, easing = FastOutSlowInEasing)
    fun quickFadeTween() = tween<Float>(DURATION_FAST, easing = FastOutSlowInEasing)
}
