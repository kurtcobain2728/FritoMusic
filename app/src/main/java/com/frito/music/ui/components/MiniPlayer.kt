package com.frito.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.glowShadow
import com.frito.music.ui.theme.rememberAppHaptics
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.utils.resize

/**
 * MiniPlayer flotante con estética Glassmorphism, resplandor esmeralda dinámico
 * y ecualizador animado sobre la carátula, idéntico a la versión de Flutter.
 */
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAudio by viewModel.currentAudio.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val isDark = appColors.isDark

    val audio = currentAudio ?: return

    val miniInteraction = remember { MutableInteractionSource() }
    val miniPressed by miniInteraction.collectIsPressedAsState()
    val miniScale by animateFloatAsState(
        targetValue = if (miniPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "miniPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .scale(miniScale)
            .glowShadow(
                color = Color.Black,
                alpha = if (isDark) 0.45f else 0.12f,
                blurRadius = 22.dp,
                offsetY = 8.dp,
                shape = RoundedCornerShape(24.dp)
            )
            .then(
                if (isPlaying) {
                    Modifier.glowShadow(
                        color = Glassmorphism.emeraldAccent,
                        alpha = 0.22f,
                        blurRadius = 20.dp,
                        spreadRadius = 1.dp,
                        shape = RoundedCornerShape(24.dp)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Glassmorphism.glassSurfaceColor(isDark))
            .border(
                width = 1.2.dp,
                color = Glassmorphism.glassBorderColor(isDark, isHighlighted = isPlaying),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = miniInteraction,
                indication = null
            ) {
                haptics.light()
                onClick()
            }
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -30) {
                            haptics.medium()
                            onSwipeUp()
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tirador central sutil para indicar arrastre hacia arriba (estilo Bencho/Flutter)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 2.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.22f)
                        else Color.Black.copy(alpha = 0.16f)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Carátula con bordes redondeados y ecualizador animado superpuesto
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = appColors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )

                    if (audio.albumUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(audio.albumUri.resize(width = 96))
                                .crossfade(250)
                                .build(),
                            contentDescription = "Carátula",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Ecualizador animado cuando está sonando
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.40f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniEqualizerBars()
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Título y artista
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audio.title,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = audio.artist.ifBlank { "Desconocido" },
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botón Play / Pause
                IconButton(
                    onClick = {
                        haptics.light()
                        viewModel.playPause()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) Glassmorphism.emeraldAccent.copy(alpha = 0.16f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = if (isPlaying) Glassmorphism.emeraldAccent else appColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Botón Siguiente
                IconButton(
                    onClick = {
                        haptics.light()
                        viewModel.skipNext()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Botón Cerrar / Detener
                IconButton(
                    onClick = {
                        haptics.medium()
                        viewModel.stopAndClear()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = appColors.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Barra de progreso inferior de alta precisión (2.5dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(appColors.accent.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(appColors.accent)
                )
            }
        }
    }
}

/**
 * 3 barras verticales que oscilan como ecualizador animado sobre la miniatura.
 */
@Composable
private fun MiniEqualizerBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "miniEqualizer")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    Row(
        modifier = Modifier.height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h1.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Glassmorphism.emeraldAccent)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h2.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Glassmorphism.emeraldAccent)
        )
    }
}
