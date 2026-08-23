package com.frito.music.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.frito.music.utils.ImageUtils
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import androidx.media3.common.Player
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.data.models.AudioFile
import com.frito.music.ui.components.AddToYouTubePlaylistModal

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, streamViewModel: StreamViewModel, onClose: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentAudio by viewModel.currentAudio.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isCurrentFavorite by viewModel.isCurrentFavorite.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentLyrics by streamViewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by streamViewModel.isLoadingLyrics.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showAddToYouTubePlaylist by remember { mutableStateOf(false) }
    
    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        if (dragY > 50 && !showLyrics) {
                            onClose()
                        } else if (dragY > 50 && showLyrics) {
                            showLyrics = false
                        } else if (dragY < -50 && !showLyrics) {
                            showLyrics = true
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragY += dragAmount
                    }
                )
            }
    ) {
        // Blur Background Layer
        if (appColors.backgroundImageUri != null) {
            AsyncImage(
                model = appColors.backgroundImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 60.dp)
            )
            // Overlay so UI remains visible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        } else {
            // Solid Background — sigue el tema activo (incluye "Color predominante")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.background)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = appColors.textPrimary,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onClose() }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REPRODUCIENDO DE",
                        color = appColors.textSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Music",
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (currentAudio?.path?.startsWith("http") == true) Icons.AutoMirrored.Filled.PlaylistAdd else Icons.Default.AddCircleOutline,
                    contentDescription = "Add",
                    tint = appColors.textPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable {
                            if (currentAudio?.path?.startsWith("http") == true) {
                                showAddToYouTubePlaylist = true
                            } else {
                                showPlaylistSheet = true
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Album Art con crossfade y scale animado
            val albumArtScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.95f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "albumArtScale"
            )
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .aspectRatio(1f)
                    .scale(albumArtScale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Album Art",
                    tint = appColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(120.dp)
                )
                if (currentAudio?.albumUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageUtils.highRes(currentAudio?.albumUri))
                            .crossfade(400)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Song Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentAudio?.title ?: "Sin reproducir",
                        color = appColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentAudio?.artist ?: "",
                        color = appColors.textSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (isCurrentFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isCurrentFavorite) Color(0xFFFF6B6B) else appColors.textPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { viewModel.toggleFavorite() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Waveform Progress Bar
            WaveformProgress(
                progress = progress,
                onProgressChange = { viewModel.seekTo(it) },
                isPlaying = isPlaying,
                appColors = appColors,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // Timestamps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(positionMs), color = appColors.textSecondary, fontSize = 12.sp)
                Text(formatDuration(durationMs), color = appColors.textSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Info
            QualityInfo(audio = currentAudio)

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleModeEnabled) appColors.accent else appColors.textPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { viewModel.toggleShuffle() }
                )
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(40.dp).clickable { viewModel.skipPrevious() }
                )
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
                        .size(72.dp)
                        .scale(playButtonScale)
                        .clip(CircleShape)
                        .background(appColors.textPrimary)
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null
                        ) { viewModel.playPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = if(appColors.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(40.dp).clickable { viewModel.skipNext() }
                )
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                val repeatTint = if (repeatMode != Player.REPEAT_MODE_OFF) appColors.accent else appColors.textPrimary
                
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = repeatTint,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { viewModel.toggleRepeat() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))


        }
        
        // Lyrics Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showLyrics,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close Lyrics",
                    tint = appColors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .size(36.dp)
                        .clickable { showLyrics = false }
                )

                when {
                    isLoadingLyrics -> {
                        CircularProgressIndicator(color = appColors.accent)
                    }
                    currentLyrics != null -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 80.dp)
                        ) {
                            item {
                                Text(
                                    text = currentLyrics!!,
                                    color = appColors.textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Letras no disponibles",
                            color = appColors.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        if (showPlaylistSheet) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showPlaylistSheet = false },
                containerColor = appColors.surface,
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
                                        .background(appColors.background, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Crear", tint = appColors.textPrimary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Crear lista de reproducción", color = appColors.textPrimary, fontSize = 16.sp)
                            }
                        }
                        items(playlists) { playlist ->
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
                                        .background(appColors.background, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Lista", tint = appColors.textSecondary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(playlist.name, color = appColors.textPrimary, fontSize = 16.sp)
                                    Text("${playlist.audioPaths.size} canciones", color = appColors.textSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(text = "Nueva Lista de Reproducción", color = appColors.textPrimary) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nombre", color = appColors.textSecondary) },
                        singleLine = true,
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
                containerColor = appColors.surface
            )
        }

        if (showAddToYouTubePlaylist) {
            currentAudio?.let { audio ->
                val videoId = audio.path.substringAfterLast("/")
                AddToYouTubePlaylistModal(
                    videoId = videoId,
                    streamViewModel = streamViewModel,
                    onDismiss = { showAddToYouTubePlaylist = false },
                    onPlaylistCreated = { showAddToYouTubePlaylist = false }
                )
            }
        }
    }
}

@Composable
fun WaveformProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    isPlaying: Boolean,
    appColors: com.frito.music.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val numBars = 45
    val baseHeights = remember { List(numBars) { kotlin.random.Random.nextFloat() * 0.8f + 0.2f } }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = dragProgress ?: progress
    
    // Solo animamos la fase cuando está reproduciendo para ahorrar GPU
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animationMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "multiplier"
    )
    // Evita redraws del Canvas cuando está pausado (ahorra GPU)
    val shouldAnimate = animationMultiplier > 0.01f

    Box(modifier = modifier
        .fillMaxWidth()
        .height(36.dp)
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                },
                onHorizontalDrag = { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                },
                onDragEnd = {
                    dragProgress?.let { onProgressChange(it) }
                    dragProgress = null
                },
                onDragCancel = {
                    dragProgress = null
                }
            )
        }
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gap = 3.dp.toPx()
            val totalGaps = (numBars - 1) * gap
            val actualBarWidth = (width - totalGaps) / numBars

            for (i in 0 until numBars) {
                val x = i * (actualBarWidth + gap)
                val barProgress = i.toFloat() / numBars
                val isPlayed = barProgress <= displayProgress

                // Cuando no está reproduciendo, usamos la fase fija para evitar redraws innecesarios
                val effectivePhase = if (shouldAnimate) phase else 0f
                val waveEffect = Math.sin((effectivePhase + i * 0.4).toDouble()).toFloat() * 0.4f
                val finalMultiplier = 1f + (waveEffect * animationMultiplier)
                val barHeight = (height * baseHeights[i] * finalMultiplier).coerceIn(height * 0.1f, height)

                val yOffset = (height - barHeight) / 2

                val color = if (isPlayed) appColors.accent else appColors.textSecondary.copy(alpha = 0.3f)

                drawLine(
                    color = color,
                    start = Offset(x + actualBarWidth / 2, yOffset),
                    end = Offset(x + actualBarWidth / 2, yOffset + barHeight),
                    strokeWidth = actualBarWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // El pulgador usa displayProgress para moverse en sincronía con las
            // barras mientras se arrastra (antes usaba `progress` y se desincronizaba)
            val thumbX = displayProgress * width
            drawCircle(
                color = appColors.textPrimary,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, height / 2)
            )
        }
    }
}

@Composable
fun QualityInfo(audio: AudioFile?) {
    val appColors = LocalAppColors.current

    val qualityText = if (audio?.path?.startsWith("http") == true) {
        "Streaming • YouTube Music"
    } else {
        // No inventamos sample rate: mostramos solo el formato real del archivo
        val extension = audio?.path?.substringAfterLast(".")?.uppercase() ?: "AUDIO"
        "Archivo local • ${extension}"
    }
    
    Text(
        text = qualityText,
        color = appColors.textSecondary,
        fontSize = 12.sp
    )
}

@Composable
fun MiniPlayer(viewModel: PlayerViewModel, onClick: () -> Unit, onSwipeUp: () -> Unit) {
    val currentAudio by viewModel.currentAudio.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val appColors = LocalAppColors.current

    val audio = currentAudio ?: return

    // Mini-player flotante estilo tarjeta: esquinas redondeadas, elevación,
    // barra de progreso de 2dp arriba y botón "siguiente".
    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = appColors.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = appColors.accent,
                    trackColor = Color.Transparent,
                )
                // Feedback táctil: la tarjeta se hunde ligeramente al presionar
                val miniInteraction = remember { MutableInteractionSource() }
                val miniPressed by miniInteraction.collectIsPressedAsState()
                val miniScale by animateFloatAsState(
                    targetValue = if (miniPressed) 0.97f else 1f,
                    animationSpec = tween(150),
                    label = "miniPressScale"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(miniScale)
                        .clickable(
                            interactionSource = miniInteraction,
                            indication = null
                        ) { onClick() }
                        .pointerInput(Unit) {
                            // dragY vive DENTRO del pointerInput: sobrevive a las
                            // recomposiciones y se reinicia en cada gesto
                            var totalDragY = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onDragEnd = {
                                    if (totalDragY < -30) {
                                        onSwipeUp()
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDragY += dragAmount
                                }
                            )
                        }
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
                        if (audio.albumUri != null) {
                            val miniCtx = androidx.compose.ui.platform.LocalContext.current
                            AsyncImage(
                                model = ImageRequest.Builder(miniCtx)
                                    .data(ImageUtils.highRes(audio.albumUri))
                                    .crossfade(300)
                                    .build(),
                                contentDescription = "Album Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(audio.title, color = appColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(audio.artist, color = appColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.playPause() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = appColors.textPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = appColors.textSecondary
                        )
                    }
                    // X: cierra el mini-player Y detiene la canción por completo
                    IconButton(onClick = { viewModel.stopAndClear() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar y detener",
                            tint = appColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
