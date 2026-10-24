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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showAddToYouTubePlaylist by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    var showNerdStatsSheet by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isVideoMode by remember(currentVideoId) { mutableStateOf(false) }
    var isVideoAvailable by remember(currentVideoId) { mutableStateOf(false) }

    LaunchedEffect(currentVideoId, isOnline) {
        isVideoMode = false
        isVideoAvailable = false
        if (isOnline && !currentVideoId.isNullOrBlank()) {
            val available = com.frito.music.data.network.yt.YouTubeRepository.hasVideoTrack(currentVideoId!!)
            isVideoAvailable = available
            if (available) {
                val isRealVideo = com.frito.music.data.network.yt.YouTubeRepository.isRealMusicVideo(currentVideoId!!)
                if (isRealVideo) {
                    isVideoMode = true
                }
            }
        }
    }

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
        // Fondo del reproductor: Video Canvas full-bleed (estilo Spotify Canvas / YT Music) o ambiental
        if (isVideoMode && !currentVideoId.isNullOrBlank()) {
            NativeVideoCanvasPlayer(
                videoId = currentVideoId!!,
                isPlaying = isPlaying,
                positionMs = positionMs,
                albumArtUri = audio.albumUri?.let { ImageUtils.highRes(it) } ?: audio.albumUri,
                onFallbackToCover = { isVideoMode = false },
                modifier = Modifier.fillMaxSize()
            )
            // Gradiente oscuro para máxima legibilidad de títulos y controles
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
        } else if (appColors.backgroundImageUri != null) {
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

                // Acciones a la derecha: Botón "!" (Información y Metadatos) y Añadir a lista
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón "!" para ver estadísticas nerd y editar metadatos
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
                                showNerdStatsSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Información y Metadatos",
                            tint = appColors.textPrimary,
                            modifier = Modifier.size(22.dp)
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
            }

            Spacer(modifier = Modifier.weight(1f))

            // Toggle [ Carátula | Video ] visible únicamente si la pista es online y tiene video disponible
            if (isOnline && !currentVideoId.isNullOrBlank() && isVideoAvailable) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (!isVideoMode) (if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f))
                                else Color.Transparent
                            )
                            .clickable {
                                haptics.light()
                                isVideoMode = false
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Carátula",
                            color = if (!isVideoMode) appColors.textPrimary else appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isVideoMode) Glassmorphism.emeraldAccent.copy(alpha = 0.35f)
                                else Color.Transparent
                            )
                            .clickable {
                                haptics.light()
                                isVideoMode = true
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Video",
                            color = if (isVideoMode) Color.White else appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isVideoMode) {
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
                            blurRadius = 40.dp,
                            offsetY = 18.dp,
                            shape = RoundedCornerShape(28.dp)
                        )
                        .then(
                            if (isPlaying) {
                                Modifier.glowShadow(
                                    color = Glassmorphism.emeraldAccent,
                                    alpha = if (isDark) 0.35f else 0.25f,
                                    blurRadius = 36.dp,
                                    spreadRadius = 1.dp,
                                    offsetY = 8.dp,
                                    shape = RoundedCornerShape(28.dp)
                                )
                            } else Modifier
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(appColors.surface)
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(28.dp)
                        )
                ) {
                    val cover = audio.albumUri?.let { ImageUtils.highRes(it) } ?: audio.albumUri
                    if (!cover.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(cover)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Carátula de ${audio.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            appColors.accent.copy(alpha = 0.3f),
                                            appColors.accent.copy(alpha = 0.1f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }
            } else {
                // Modo Video activo: la carátula se retira por completo para mostrar el video Canvas en todo el reproductor
                Spacer(modifier = Modifier.weight(1f))
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

            // Botones Cápsula de Cristal: Velocidad & Tono + Letra + Lista
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Botón Velocidad y Tono (Estilo Musicolet)
                Box(
                    modifier = Modifier
                        .glowShadow(
                            color = Color.Black,
                            alpha = if (isDark) 0.30f else 0.12f,
                            blurRadius = 16.dp,
                            offsetY = 4.dp,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (playbackSpeed != 1.0f || playbackPitch != 1.0f)
                                Glassmorphism.emeraldAccent.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (playbackSpeed != 1.0f || playbackPitch != 1.0f)
                                Glassmorphism.emeraldAccent.copy(alpha = 0.40f)
                            else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable {
                            haptics.light()
                            showSpeedPitchSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (playbackSpeed != 1.0f || playbackPitch != 1.0f)
                                        Glassmorphism.emeraldAccent.copy(alpha = 0.25f)
                                    else Color(0xFF3B82F6).copy(alpha = 0.20f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = if (playbackSpeed != 1.0f || playbackPitch != 1.0f)
                                    Glassmorphism.emeraldAccent
                                else Color(0xFF60A5FA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2fx", playbackSpeed),
                            color = appColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }

                // 2. Botón Letra en tiempo real
                Box(
                    modifier = Modifier
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
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "Letra",
                            color = appColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = appColors.textSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. Botón Lista (Cola de Reproducción)
                Box(
                    modifier = Modifier
                        .glowShadow(
                            color = Color.Black,
                            alpha = if (isDark) 0.30f else 0.12f,
                            blurRadius = 16.dp,
                            offsetY = 4.dp,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (showQueueSheet) Glassmorphism.emeraldAccent.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (showQueueSheet) Glassmorphism.emeraldAccent.copy(alpha = 0.40f)
                            else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable {
                            haptics.light()
                            showQueueSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "Lista",
                            color = appColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        if (currentQueue.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(appColors.accent.copy(alpha = 0.25f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${currentQueue.size}",
                                    color = appColors.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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

        // Bottom Sheet: Estadísticas Nerd y Edición de Metadatos
        if (showNerdStatsSheet) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showNerdStatsSheet = false },
                containerColor = appColors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                var editTitle by remember { mutableStateOf(audio.title) }
                var editArtist by remember { mutableStateOf(audio.artist) }
                var editAlbum by remember { mutableStateOf(audio.album) }

                val fileFormat = remember(audio.path) {
                    if (isOnline) "Streaming (WebM / Opus)"
                    else if (audio.path.contains(".")) audio.path.substringAfterLast(".").uppercase()
                    else "Desconocido"
                }

                val fileSizeStr = remember(audio.path) {
                    if (isOnline) "En línea (YouTube Music)"
                    else {
                        val file = File(audio.path)
                        if (file.exists()) {
                            String.format(java.util.Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
                        } else "Desconocido"
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Información y Metadatos",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Sección Estadísticas Nerd
                    Text(
                        text = "ESTADÍSTICAS NERD",
                        color = appColors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NerdStatRow("Formato / Códec", fileFormat, appColors)
                        NerdStatRow("Duración", "${formatDuration(audio.durationMs)} (${audio.durationMs / 1000}s)", appColors)
                        NerdStatRow("Tamaño de archivo", fileSizeStr, appColors)
                        NerdStatRow("Ruta / ID", if (isOnline) (currentVideoId ?: audio.path) else audio.path, appColors)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sección Editar Metadatos
                    Text(
                        text = "EDITAR METADATOS",
                        color = appColors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título", color = appColors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artista", color = appColors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Álbum", color = appColors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.updateCurrentAudioMetadata(editTitle, editArtist, editAlbum)
                            android.widget.Toast.makeText(context, "Metadatos actualizados", android.widget.Toast.LENGTH_SHORT).show()
                            showNerdStatsSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.accent)
                    ) {
                        Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // Bottom Sheet: Velocidad y Tono (Estilo Musicolet)
        if (showSpeedPitchSheet) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showSpeedPitchSheet = false },
                containerColor = appColors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Velocidad y Tono",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            viewModel.applySpeedPitchPreset("normal")
                        }) {
                            Text("Restablecer", color = appColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3 Accesos Rápidos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isSlowed = (playbackSpeed == 0.97f && playbackPitch == 0.85f)
                        val isNormal = (playbackSpeed == 1.00f && playbackPitch == 1.00f)
                        val isNightcore = (playbackSpeed == 1.05f && playbackPitch == 1.30f)

                        PresetChip(
                            title = "Slowed",
                            subtitle = "0.97x | 0.85",
                            selected = isSlowed,
                            onClick = { viewModel.applySpeedPitchPreset("slowed") },
                            modifier = Modifier.weight(1f),
                            appColors = appColors
                        )

                        PresetChip(
                            title = "Normal",
                            subtitle = "1.00x | 1.00",
                            selected = isNormal,
                            onClick = { viewModel.applySpeedPitchPreset("normal") },
                            modifier = Modifier.weight(1f),
                            appColors = appColors
                        )

                        PresetChip(
                            title = "Nightcore",
                            subtitle = "1.05x | 1.30",
                            selected = isNightcore,
                            onClick = { viewModel.applySpeedPitchPreset("nightcore") },
                            modifier = Modifier.weight(1f),
                            appColors = appColors
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Barra Velocidad de Reproducción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Velocidad de reproducción", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            String.format(java.util.Locale.US, "%.2fx", playbackSpeed),
                            color = appColors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = playbackSpeed,
                        onValueChange = { newSpeed ->
                            val rounded = (Math.round(newSpeed * 100f) / 100f)
                            viewModel.setPlaybackParameters(rounded, playbackPitch)
                        },
                        valueRange = 0.50f..2.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = appColors.accent,
                            activeTrackColor = appColors.accent,
                            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Barra Tono
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tono", color = appColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            String.format(java.util.Locale.US, "%.2f", playbackPitch),
                            color = appColors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = playbackPitch,
                        onValueChange = { newPitch ->
                            val rounded = (Math.round(newPitch * 100f) / 100f)
                            viewModel.setPlaybackParameters(playbackSpeed, rounded)
                        },
                        valueRange = 0.50f..1.50f,
                        colors = SliderDefaults.colors(
                            thumbColor = appColors.accent,
                            activeTrackColor = appColors.accent,
                            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                        )
                    )
                }
            }
        }

        // Bottom Sheet: Cola de Reproducción ("Lista")
        if (showQueueSheet) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = appColors.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cola de reproducción",
                                color = appColors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${currentQueue.size} pista${if (currentQueue.size != 1) "s" else ""}",
                                color = appColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = { showQueueSheet = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar",
                                tint = appColors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentQueue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay canciones en la cola",
                                color = appColors.textSecondary,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(Unit) {
                            if (currentQueueIndex in currentQueue.indices) {
                                listState.animateScrollToItem(currentQueueIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                currentQueue,
                                key = { index, item -> "${item.path}_$index" }
                            ) { index, item ->
                                val isCurrent = (index == currentQueueIndex) || (item.path == currentAudio?.path)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isCurrent) appColors.accent.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = if (isDark) 0.04f else 0.02f)
                                        )
                                        .border(
                                            width = if (isCurrent) 1.2.dp else 0.dp,
                                            color = if (isCurrent) appColors.accent.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            haptics.light()
                                            viewModel.playQueueIndex(index)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent && isPlaying) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Sonando",
                                                tint = appColors.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${index + 1}",
                                                color = if (isCurrent) appColors.accent else appColors.textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(appColors.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.albumUri != null) {
                                            AsyncImage(
                                                model = item.albumUri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = appColors.textSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = if (isCurrent) appColors.accent else appColors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.artist,
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (item.durationMs > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatDuration(item.durationMs),
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NerdStatRow(
    label: String,
    value: String,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = appColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun PresetChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appColors: com.frito.music.ui.theme.AppColors
) {
    val isDark = appColors.isDark
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) appColors.accent.copy(alpha = 0.20f)
                else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
            )
            .border(
                width = 1.dp,
                color = if (selected) appColors.accent
                else (if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (selected) appColors.accent else appColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = if (selected) appColors.accent.copy(alpha = 0.85f) else appColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
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
    val realFormat by viewModel.realAudioFormat.collectAsState()
    val isDark = appColors.isDark
    var showFormatDialog by remember { mutableStateOf(false) }

    val formatDotColor = when {
        !isOnline -> appColors.accent
        realFormat?.codec == "OPUS" -> Color(0xFF3B82F6) // Neon Blue
        realFormat?.codec == "AAC" -> Color(0xFFA855F7)  // Neon Purple
        else -> Glassmorphism.emeraldAccent
    }

    val formatBadgeText = if (isOnline) {
        val codec = realFormat?.codec ?: "STREAM"
        val bitrate = realFormat?.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" } ?: "Auto"
        "$codec • $bitrate"
    } else {
        val ext = realFormat?.codec?.ifBlank { null }
            ?: (if (audioPath.contains(".")) audioPath.substringAfterLast(".").uppercase() else "AUDIO")
        "$ext • LOCAL"
    }

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

            // Badge central interactivo de calidad de audio
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                    .clickable { showFormatDialog = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(formatDotColor)
                    )
                    Text(
                        text = formatBadgeText,
                        color = appColors.textPrimary.copy(alpha = 0.90f),
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

    if (showFormatDialog) {
        val context = LocalContext.current
        val summaryPref = com.frito.music.data.preferences.StreamQualityPreferences.getSummaryText(context)
        val format = realFormat

        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(formatDotColor)
                    )
                    Text(
                        text = "Estadísticas de Audio",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Información técnica en tiempo real provista por el motor de reproducción ExoPlayer:",
                        color = appColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = appColors.textSecondary.copy(alpha = 0.2f))

                    AudioTelemetryRow("Códec", format?.codec ?: (if (isOnline) "Auto / Cargando" else "Local"), appColors)
                    AudioTelemetryRow("Contenedor", format?.container ?: (if (isOnline) "WebM / M4A" else "Archivo"), appColors)
                    AudioTelemetryRow("Tasa de bits real", if ((format?.bitrateKbps ?: 0) > 0) "${format?.bitrateKbps} kbps" else "Calculando...", appColors)
                    AudioTelemetryRow("Muestreo", if ((format?.sampleRateHz ?: 0) > 0) "${format?.sampleRateHz} Hz" else "44,100 / 48,000 Hz", appColors)
                    AudioTelemetryRow("Canales", if ((format?.channelCount ?: 0) > 0) "${format?.channelCount} (Estéreo)" else "Estéreo", appColors)
                    if (isOnline && format?.itag != null) {
                        AudioTelemetryRow("ITAG YouTube", "#${format.itag}", appColors)
                    }
                    AudioTelemetryRow("Fuente", format?.sourceDescription ?: (if (isOnline) "YouTube Stream" else "Dispositivo local"), appColors)
                    AudioTelemetryRow("Ajuste activo", summaryPref, appColors)
                }
            },
            confirmButton = {
                if (isOnline) {
                    TextButton(
                        onClick = {
                            viewModel.reloadCurrentStreamQuality()
                            showFormatDialog = false
                        }
                    ) {
                        Text("Recargar Stream", color = Glassmorphism.emeraldAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Cerrar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun AudioTelemetryRow(
    label: String,
    value: String,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = appColors.textSecondary,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
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

/**
 * Reproductor de video nativo Canvas con aceleración por hardware (ExoPlayer).
 * Reproduce streams directos .mp4 silenciados y sincronizados con el audio de MusicService.
 * Utiliza TextureView mediante PlayerView con surface_type="texture_view" para integración nativa con Jetpack Compose.
 * Soporta crossfade fluido al decodificar el primer fotograma y carátula ambiental mientras conecta.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun NativeVideoCanvasPlayer(
    videoId: String,
    isPlaying: Boolean,
    positionMs: Long,
    albumArtUri: String?,
    onFallbackToCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var isFirstFrameRendered by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        isLoading = true
        isFirstFrameRendered = false
        val result = com.frito.music.data.network.yt.YouTubeRepository.getVideoStreamUrl(videoId)
        val url = result.getOrNull()
        if (!url.isNullOrBlank()) {
            videoUrl = url
            isLoading = false
        } else {
            isLoading = false
            android.widget.Toast.makeText(
                context,
                "No se pudo cargar el stream de video, mostrando carátula",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onFallbackToCover()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 1. Capa ambiental de fondo: Carátula suavemente difuminada para evitar pantalla en negro
        if (!albumArtUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 35.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // 2. Indicador sutil de carga si aún está conectando el stream o esperando el primer fotograma
        if (isLoading || !isFirstFrameRendered) {
            CircularProgressIndicator(
                color = Glassmorphism.emeraldAccent,
                modifier = Modifier.size(36.dp),
                strokeWidth = 2.5.dp
            )
        }

        // 3. Reproductor ExoPlayer acoplado a TextureView
        if (videoUrl != null) {
            val exoPlayer = remember(context, videoUrl) {
                val okHttpFactory = OkHttpDataSource.Factory(
                    com.frito.music.data.network.yt.PlaybackHttpClient.client
                )
                val defaultDataSource = DefaultDataSource.Factory(context, okHttpFactory)
                val resolvingDataSource = androidx.media3.datasource.ResolvingDataSource.Factory(defaultDataSource) { dataSpec ->
                    if (dataSpec.length < 0) {
                        val host = dataSpec.uri.host
                        if (host != null && com.frito.music.data.network.yt.StreamClientUtils.isYouTubeMediaHost(host)) {
                            val totalLength = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }
                            if (totalLength != null) {
                                val remaining = totalLength - dataSpec.position
                                if (remaining > 0) {
                                    dataSpec.buildUpon().setLength(remaining).build()
                                } else dataSpec
                            } else dataSpec
                        } else dataSpec
                    } else dataSpec
                }
                val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSource)

                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build().apply {
                        volume = 0f // Silenciado: el audio de alta fidelidad suena desde MusicService
                        repeatMode = Player.REPEAT_MODE_ONE
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                android.util.Log.i("NativeVideoCanvas", "onRenderedFirstFrame")
                                isFirstFrameRendered = true
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                android.util.Log.i("NativeVideoCanvas", "playbackState: $playbackState")
                                if (playbackState == Player.STATE_READY) {
                                    isFirstFrameRendered = true
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.w("NativeVideoCanvas", "ExoPlayer video error: ${error.message}", error)
                                com.frito.music.data.network.yt.YouTubeRepository.invalidateVideoStreamCache(videoId)
                                onFallbackToCover()
                            }
                        })
                        setMediaItem(MediaItem.fromUri(videoUrl!!))
                        prepare()
                        if (positionMs > 0) {
                            seekTo(positionMs)
                        }
                        if (isPlaying) {
                            play()
                        }
                    }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.release()
                }
            }

            // Sincronizar play / pause
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    if (!exoPlayer.isPlaying) exoPlayer.play()
                } else {
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                }
            }

            // Sincronizar seek manual si se desvía más de 2 segundos
            LaunchedEffect(positionMs) {
                val delta = kotlin.math.abs(exoPlayer.currentPosition - positionMs)
                if (delta > 2000L) {
                    exoPlayer.seekTo(positionMs)
                }
            }

            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx).inflate(
                        com.frito.music.R.layout.view_video_canvas,
                        null,
                        false
                    ) as androidx.media3.ui.PlayerView
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.useController = false
                    view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    view.player = exoPlayer
                    view
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
