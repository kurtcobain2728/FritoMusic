package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.LyricsUiState
import com.frito.music.ui.components.AddToYouTubePlaylistModal
import com.frito.music.ui.components.DownloadQualityBottomSheet
import com.frito.music.ui.components.SyncedLyricsView
import com.frito.music.ui.theme.AppAnimations
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.glowShadow
import com.frito.music.ui.theme.rememberAppHaptics
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.utils.ImageUtils
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Pantalla completa de reproducción con estética Glassmorphism idéntica a Flutter:
 * - Carátula 3D con scale punch y resplandor esmeralda dinámico.
 * - Botón Play central con gradiente verde y resplandor difuso.
 * - Barra Waveform acústica de 62 bandas con oscilador analógico continuo.
 * - Chip de artista interactivo y controles en cristal translúcido.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    streamViewModel: StreamViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onClose: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentAudio by viewModel.currentAudio.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isCurrentFavorite by viewModel.isCurrentFavorite.collectAsState()
    val likedSongIds by onlineLibraryViewModel.likedSongIds.collectAsState()

    val currentPath = currentAudio?.path.orEmpty()
    val isOnline = currentPath.startsWith("http")
    val currentVideoId by viewModel.currentVideoId.collectAsState()
    val isOnlineLiked = currentVideoId != null && likedSongIds.contains(currentVideoId)
    val playlists by viewModel.playlists.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showAddToYouTubePlaylist by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }

    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = appColors.isDark

    val audio = currentAudio ?: return

    val favIsActive = if (isOnline) isOnlineLiked else isCurrentFavorite

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        if (dragY > 60 && !showLyrics) {
                            haptics.light()
                            onClose()
                        } else if (dragY > 60 && showLyrics) {
                            haptics.light()
                            showLyrics = false
                        } else if (dragY < -60 && !showLyrics) {
                            haptics.light()
                            showLyrics = true
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragY += dragAmount
                    }
                )
            }
    ) {
        // Fondo ambiental difuminado
        if (appColors.backgroundImageUri != null) {
            AsyncImage(
                model = appColors.backgroundImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 50.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.background)
            )
        }

        // Contenido del reproductor
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tirador superior de arrastre (Dismiss handle)
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 12.dp)
                    .width(44.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.25f)
                        else Color.Black.copy(alpha = 0.20f)
                    )
                    .clickable {
                        haptics.light()
                        onClose()
                    }
            )

            // Fila de acciones superior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón circular de cerrar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.12f)
                            else Color.Black.copy(alpha = 0.06f),
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.light()
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Insignia central: REPRODUCIENDO + Álbum
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                (if (isPlaying) Glassmorphism.emeraldAccent else appColors.accent)
                                    .copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Glassmorphism.emeraldAccent)
                                )
                            }
                            Text(
                                text = "REPRODUCIENDO",
                                color = if (isPlaying) Glassmorphism.emeraldAccent else appColors.accent,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = audio.album.ifBlank { if (isOnline) "Streaming" else "Colección local" },
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botón circular de añadir a lista
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.12f)
                            else Color.Black.copy(alpha = 0.06f),
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.light()
                            if (isOnline && currentVideoId != null) {
                                showAddToYouTubePlaylist = true
                            } else {
                                showPlaylistSheet = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Rounded.PlaylistAdd else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Añadir a lista",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Carátula 3D con Scale Punch y Aura Esmeralda Dinámica
            val albumArtScale by animateFloatAsState(
                targetValue = if (isPlaying) 1.0f else 0.92f,
                animationSpec = AppAnimations.fluidPlayerTween,
                label = "albumArtScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(1f)
                    .scale(albumArtScale)
                    .glowShadow(
                        color = Color.Black,
                        alpha = if (isDark) 0.55f else 0.18f,
                        blurRadius = 36.dp,
                        offsetY = 16.dp,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .then(
                        if (isPlaying) {
                            Modifier.glowShadow(
                                color = Glassmorphism.emeraldAccent,
                                alpha = 0.28f,
                                blurRadius = 36.dp,
                                spreadRadius = 2.dp,
                                offsetY = 10.dp,
                                shape = RoundedCornerShape(28.dp)
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(appColors.surface)
                    .border(
                        width = 1.2.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.14f else 0.25f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(100.dp)
                )

                if (audio.albumUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageUtils.highRes(audio.albumUri))
                            .crossfade(300)
                            .build(),
                        contentDescription = "Carátula",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Título, Artista y Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audio.title,
                        color = appColors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Chip estilizado de artista con flecha '>'
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.06f)
                                else Color.Black.copy(alpha = 0.04f)
                            )
                            .clickable(enabled = audio.artist.isNotBlank()) {
                                haptics.light()
                                val artistName = audio.artist.trim()
                                if (artistName.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val artistId = streamViewModel.findArtistIdByName(artistName)
                                        if (artistId != null) {
                                            onClose()
                                            onNavigateToArtist(artistId)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No se encontró perfil para $artistName",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = audio.artist.ifBlank { "Desconocido" },
                                color = appColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = appColors.textSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Botón de Descarga (solo para streaming online)
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.12f)
                                else Color.Black.copy(alpha = 0.06f),
                                shape = CircleShape
                            )
                            .clickable {
                                haptics.light()
                                showDownloadQualitySheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Descargar",
                            tint = appColors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Botón de Favorito / Me Gusta con aura roja/rosada
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (favIsActive) Glassmorphism.favoritePink.copy(alpha = 0.15f)
                            else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        )
                        .border(
                            width = 1.dp,
                            color = if (favIsActive) Glassmorphism.favoritePink.copy(alpha = 0.40f)
                            else (if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)),
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.light()
                            val videoId = currentVideoId
                            if (isOnline && videoId != null) {
                                onlineLibraryViewModel.likeSong(videoId, !isOnlineLiked)
                            } else {
                                viewModel.toggleFavorite()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (favIsActive) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (favIsActive) Glassmorphism.favoritePink else appColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra Waveform de Progreso Acústico & Tiempos
            PlayerProgressSection(
                viewModel = viewModel,
                isPlaying = isPlaying,
                showLyrics = showLyrics,
                isOnline = isOnline,
                audioPath = audio.path,
                appColors = appColors
            )

            Spacer(modifier = Modifier.weight(1f))

            // Controles Principales de Reproducción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (shuffleModeEnabled) Glassmorphism.emeraldAccent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .then(
                            if (shuffleModeEnabled) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Glassmorphism.emeraldAccent.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                            } else Modifier
                        )
                        .clickable {
                            haptics.light()
                            viewModel.toggleShuffle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (shuffleModeEnabled) Glassmorphism.emeraldAccent else appColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Anterior
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.07f)
                            else Color.Black.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.10f)
                            else Color.Black.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.light()
                            viewModel.skipPrevious()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Botón Play / Pause Central con Gradiente Esmeralda y Resplandor Difuso
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val playButtonScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.92f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    ),
                    label = "playScale"
                )

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(playButtonScale)
                        .glowShadow(
                            color = Glassmorphism.emeraldGradientStart,
                            alpha = 0.42f,
                            blurRadius = 24.dp,
                            offsetY = 8.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Glassmorphism.emeraldGradientStart,
                                    Glassmorphism.emeraldGradientEnd
                                )
                            )
                        )
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null
                        ) {
                            haptics.medium()
                            viewModel.playPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Siguiente
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.07f)
                            else Color.Black.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.10f)
                            else Color.Black.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                        .clickable {
                            haptics.light()
                            viewModel.skipNext()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Repetir
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRepeatActive) Glassmorphism.emeraldAccent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .then(
                            if (isRepeatActive) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Glassmorphism.emeraldAccent.copy(alpha = 0.45f),
                                    shape = CircleShape
                                )
                            } else Modifier
                        )
                        .clickable {
                            haptics.light()
                            viewModel.toggleRepeat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repetir",
                        tint = if (isRepeatActive) Glassmorphism.emeraldAccent else appColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botón Cápsula de Cristal: "Letra en tiempo real"
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .glowShadow(
                        color = Color.Black,
                        alpha = if (isDark) 0.30f else 0.12f,
                        blurRadius = 16.dp,
                        offsetY = 4.dp,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable {
                        haptics.light()
                        showLyrics = true
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Letra en tiempo real",
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = appColors.textSecondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Overlay de Letras Sincronizadas
        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = AppAnimations.playerSlideTween()
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = AppAnimations.playerSlideTween()
            )
        ) {
            SyncedLyricsView(
                lyricsState = lyricsState,
                positionMs = viewModel.positionMs.collectAsState().value,
                onSeekTo = { viewModel.seekTo(it) },
                onClose = {
                    haptics.light()
                    showLyrics = false
                },
                onRefresh = { viewModel.refreshLyrics() },
                albumArtUri = audio.albumUri,
                title = audio.title,
                artist = audio.artist,
                accentColor = appColors.accent
            )
        }

        // Bottom Sheet para Listas de Reproducción Locales
        if (showPlaylistSheet) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showPlaylistSheet = false },
                containerColor = appColors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Añadir a la lista",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPlaylistSheet = false
                                        showCreateDialog = true
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(appColors.background, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Crear", tint = appColors.textPrimary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Crear lista de reproducción", color = appColors.textPrimary, fontSize = 16.sp)
                            }
                        }
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addCurrentAudioToPlaylist(playlist.id)
                                        showPlaylistSheet = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(appColors.background, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Lista", tint = appColors.textSecondary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(playlist.name, color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${playlist.audioPaths.size} canciones", color = appColors.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo para Crear Lista Local
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(text = "Nueva Lista de Reproducción", color = appColors.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nombre", color = appColors.textSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.textSecondary
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val newPl = viewModel.createPlaylist(newPlaylistName.trim())
                            viewModel.addCurrentAudioToPlaylist(newPl.id)
                        }
                        showCreateDialog = false
                        newPlaylistName = ""
                    }) {
                        Text("Guardar", color = appColors.accent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancelar", color = appColors.textSecondary)
                    }
                },
                containerColor = appColors.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Modal para añadir a YouTube Music Playlist
        if (showAddToYouTubePlaylist) {
            val videoId = currentVideoId ?: audio.path.substringAfterLast("/")
            AddToYouTubePlaylistModal(
                videoId = videoId,
                streamViewModel = streamViewModel,
                onDismiss = { showAddToYouTubePlaylist = false },
                onPlaylistCreated = { showAddToYouTubePlaylist = false }
            )
        }

        // Sheet de Selección de Calidad de Descarga
        if (showDownloadQualitySheet) {
            val videoId = currentVideoId?.ifBlank { null }
                ?: Regex("""(?<=v=|\/vi\/|youtu\.be\/|\/shorts\/)[a-zA-Z0-9_-]{11}""").find(audio.path)?.value
                ?: if (audio.path.length == 11 && !audio.path.startsWith("http")) audio.path else ""
            DownloadQualityBottomSheet(
                videoId = videoId,
                title = audio.title,
                artist = audio.artist,
                albumArtUrl = audio.albumUri,
                albumName = audio.album,
                onDismiss = { showDownloadQualitySheet = false }
            )
        }
    }
}

/**
 * Sección de barra de progreso con Waveform Acústico realista de 62 bandas,
 * oscilador continuo y badges de calidad.
 */
@Composable
private fun PlayerProgressSection(
    viewModel: PlayerViewModel,
    isPlaying: Boolean,
    showLyrics: Boolean,
    isOnline: Boolean,
    audioPath: String,
    appColors: com.frito.music.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.progress.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val isDark = appColors.isDark

    Column(modifier = modifier.fillMaxWidth()) {
        WaveformProgress(
            progress = progress,
            onProgressChange = { newProgress ->
                viewModel.seekTo(newProgress)
            },
            isPlaying = isPlaying && !showLyrics,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Timestamps e indicador central de fidelidad
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(positionMs),
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Badge central de calidad de audio
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.06f)
                        else Color.Black.copy(alpha = 0.04f)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) Glassmorphism.emeraldAccent else appColors.accent
                            )
                    )
                    Text(
                        text = if (isOnline) "Streaming • Alta fidelidad"
                        else (if (audioPath.contains(".")) audioPath.substringAfterLast(".").uppercase() else "AUDIO"),
                        color = appColors.textSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = formatDuration(durationMs),
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Espectrograma acústico realista de 62 bandas (bajos, medios y agudos)
 * con oscilador analógico continuo y arrastre táctil fluido.
 */
@Composable
fun WaveformProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    isPlaying: Boolean,
    appColors: com.frito.music.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val numBars = 62
    val haptics = rememberAppHaptics()

    // Perfil realista de frecuencias armónicas (envolvente acústica)
    val baseHeights = remember {
        val random = java.util.Random(84)
        List(numBars) { i ->
            val norm = i.toFloat() / (numBars - 1)
            val envelope = when {
                norm < 0.25f -> 0.55f + 0.40f * sin(norm / 0.25f * (PI / 2)).toFloat()
                norm < 0.75f -> {
                    val midNorm = (norm - 0.25f) / 0.50f
                    0.45f + 0.45f * sin(midNorm * PI).toFloat()
                }
                else -> {
                    val highNorm = (norm - 0.75f) / 0.25f
                    0.35f + 0.30f * (1.0f - highNorm)
                }
            }
            val variance = (random.nextDouble().toFloat() - 0.5f) * 0.38f
            (envelope + variance).coerceIn(0.22f, 0.96f)
        }
    }

    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = dragProgress ?: progress

    // Oscilador continuo en tiempo real
    val infiniteTransition = rememberInfiniteTransition(label = "waveformOscillator")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animationMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "multiplier"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        haptics.tick()
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val newP = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (kotlin.math.abs(newP - (dragProgress ?: 0f)) > 0.05f) {
                            haptics.tick()
                        }
                        dragProgress = newP
                    },
                    onDragEnd = {
                        dragProgress?.let {
                            haptics.light()
                            onProgressChange(it)
                        }
                        dragProgress = null
                    },
                    onDragCancel = {
                        dragProgress = null
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptics.light()
                    onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gap = 2.dp.toPx()
            val totalGaps = (numBars - 1) * gap
            val actualBarWidth = ((width - totalGaps) / numBars).coerceAtLeast(1.5f)

            for (i in 0 until numBars) {
                val x = i * (actualBarWidth + gap)
                val barProgress = i.toFloat() / numBars
                val isPlayed = barProgress <= displayProgress

                val waveEffect = sin(phase.toDouble() + i * 0.35).toFloat() * 0.35f
                val finalMultiplier = 1f + (waveEffect * animationMultiplier)
                val barHeight = (height * baseHeights[i] * finalMultiplier).coerceIn(height * 0.12f, height * 0.95f)

                val yOffset = (height - barHeight) / 2
                val color = if (isPlayed) Glassmorphism.emeraldAccent else appColors.textSecondary.copy(alpha = 0.28f)

                drawLine(
                    color = color,
                    start = Offset(x + actualBarWidth / 2, yOffset),
                    end = Offset(x + actualBarWidth / 2, yOffset + barHeight),
                    strokeWidth = actualBarWidth,
                    cap = StrokeCap.Round
                )
            }

            // Indicador de aguja / cabezal de reproducción
            val thumbX = displayProgress * width
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(thumbX, height / 2)
            )
            drawCircle(
                color = Glassmorphism.emeraldAccent,
                radius = 3.dp.toPx(),
                center = Offset(thumbX, height / 2)
            )
        }
    }
}
