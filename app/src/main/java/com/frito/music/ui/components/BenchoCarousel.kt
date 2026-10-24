package com.frito.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.frito.music.utils.resize
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RenderCard<T>(
    val index: Int,
    val item: T,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotY: Float,
    val rotZ: Float,
    val depth: Float
)

/**
 * Carrusel 3D orbital tipo tornamesa inspirado en Carousel de bencho.dev / Flutter.
 * Las tarjetas giran en un anillo cilíndrico en el espacio 3D:
 * - x = sin(th) * orbit
 * - y = -(1 - f) * lean
 * - scale = lerp(depthScale, 1.0, f)
 * - rotY = -sin(th) * 22°
 * - rotZ = ángulo orgánico único por tarjeta
 */
@Composable
fun BenchoCarousel(
    songs: List<SongItem>,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    isVideo: Boolean = false
) {
    if (songs.isEmpty()) return

    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val coroutineScope = rememberCoroutineScope()
    val isDark = appColors.isDark

    val turnAnim = remember { Animatable(0f) }
    val maxTurn = (songs.size - 1).toFloat()
    val cardAngles = remember { listOf(-3.8f, 2.4f, -1.8f, 3.2f, 1.2f) }

    // Animación de respiración / flotación en reposo
    val infiniteTransition = rememberInfiniteTransition(label = "bob")
    val bobProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bobProgress"
    )

    val currentIndex = turnAnim.value.toInt().coerceIn(0, songs.size - 1)
    val currentSong = songs[currentIndex]

    fun glideTo(target: Int) {
        val clamped = target.coerceIn(0, songs.size - 1).toFloat()
        coroutineScope.launch {
            turnAnim.animateTo(
                targetValue = clamped,
                animationSpec = tween(440, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Cabecera con título y contador
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${currentIndex + 1} de ${songs.size}",
                    color = appColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Escenario del Carrusel 3D Orbital
        val density = LocalDensity.current
        val orbitPx = with(density) { 112.dp.toPx() }
        val leanPx = with(density) { 26.dp.toPx() }
        val backScale = 0.68f
        val pullPx = with(density) { 150.dp.toPx() }

        var isDragging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .pointerInput(songs.size) {
                    var dragStartTurn = 0f
                    var dragStartX = 0f
                    var lastDragX = 0f
                    var lastDragTime = 0L
                    var velocity = 0f

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragStartX = offset.x
                            lastDragX = offset.x
                            dragStartTurn = turnAnim.value
                            lastDragTime = System.currentTimeMillis()
                            velocity = 0f
                        },
                        onHorizontalDrag = { change, _ ->
                            val now = System.currentTimeMillis()
                            val dt = now - lastDragTime
                            if (dt > 0) {
                                val dx = change.position.x - lastDragX
                                velocity = (dx / dt.toFloat()) * 1000f
                                lastDragX = change.position.x
                                lastDragTime = now
                            }

                            val totalDx = change.position.x - dragStartX
                            val rawTurn = dragStartTurn - (totalDx / pullPx)
                            val clampedTurn = when {
                                rawTurn < 0f -> rawTurn * 0.28f
                                rawTurn > maxTurn -> maxTurn + (rawTurn - maxTurn) * 0.28f
                                else -> rawTurn
                            }
                            coroutineScope.launch {
                                turnAnim.snapTo(clampedTurn)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val carry = (-velocity / 750f).coerceIn(-2f, 2f)
                            val target = (turnAnim.value + carry).toInt().coerceIn(0, songs.size - 1)
                            haptics.tick()
                            glideTo(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            val target = turnAnim.value.toInt().coerceIn(0, songs.size - 1)
                            glideTo(target)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val turn = turnAnim.value
            val visibleCards = mutableListOf<RenderCard<SongItem>>()

            for (i in songs.indices) {
                val diff = i - turn
                if (kotlin.math.abs(diff) > 2.3f) continue

                val th = diff * ((2f * PI.toFloat()) / 5.0f)
                val depth = ((cos(th) + 1.0f) / 2.0f).coerceIn(0f, 1f)

                val x = sin(th) * orbitPx
                val y = -(1.0f - depth) * leanPx
                val s = backScale + (1.0f - backScale) * depth
                val rotY = -sin(th) * 24.0f
                val naturalAngle = cardAngles[i % cardAngles.size]
                val bobY = if (isDragging) 0f else sin(bobProgress.toDouble() + i * 1.3).toFloat() * 10f
                val bobAngle = if (isDragging) 0f else cos(bobProgress.toDouble() + i * 1.3).toFloat() * 1.2f

                visibleCards.add(
                    RenderCard(
                        index = i,
                        item = songs[i],
                        x = x,
                        y = y + bobY,
                        scale = s,
                        rotY = rotY,
                        rotZ = naturalAngle + bobAngle,
                        depth = depth
                    )
                )
            }

            // Ordenar de atrás hacia adelante para que la tarjeta frontal quede encima
            visibleCards.sortBy { it.depth }

            visibleCards.forEach { card ->
                val isFront = kotlin.math.abs(card.index - turn) < 0.45f
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            cameraDistance = 16f * density.density
                            translationX = card.x
                            translationY = card.y
                            scaleX = card.scale
                            scaleY = card.scale
                            rotationY = card.rotY
                            rotationZ = card.rotZ
                        }
                        .size(width = 145.dp, height = 200.dp)
                        .glowShadow(
                            color = Color.Black,
                            alpha = if (isDark) 0.50f else 0.18f,
                            blurRadius = if (isFront) 24.dp else 14.dp,
                            offsetY = 8.dp,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(appColors.surface)
                        .border(
                            width = 1.2.dp,
                            color = if (isFront) Glassmorphism.emeraldAccent.copy(alpha = 0.50f)
                            else Color.White.copy(alpha = if (isDark) 0.12f else 0.20f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            if (isFront) {
                                haptics.medium()
                                onPlaySong(card.item, songs)
                            } else {
                                haptics.light()
                                glideTo(card.index)
                            }
                        }
                ) {
                    // Carátula
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = appColors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(card.item.thumbnail.resize(width = 250))
                            .crossfade(200)
                            .build(),
                        contentDescription = card.item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Insignia VIDEO para videoclips musicales oficiales
                    if (isVideo) {
                        Surface(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Glassmorphism.emeraldAccent,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "VIDEO",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Overlay con botón Play sobre la tarjeta frontal
                    if (isFront) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Glassmorphism.emeraldAccent)
                                    .glowShadow(
                                        color = Glassmorphism.emeraldAccent,
                                        alpha = 0.45f,
                                        blurRadius = 14.dp,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tarjeta de Información y Controles de la Canción Frontal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0x991E1E22) else Color(0xE6F5F5F7))
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón anterior
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            haptics.light()
                            glideTo(currentIndex - 1)
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                        contentDescription = "Anterior",
                        tint = if (currentIndex > 0) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Información
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong.title,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong.artists.joinToString(", ") { it.name },
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botón reproducir canción enfocada
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .glowShadow(
                            color = Glassmorphism.emeraldAccent,
                            alpha = 0.38f,
                            blurRadius = 10.dp,
                            offsetY = 3.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(Glassmorphism.emeraldAccent)
                        .clickable {
                            haptics.medium()
                            onPlaySong(currentSong, songs)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Botón siguiente
                IconButton(
                    onClick = {
                        if (currentIndex < songs.size - 1) {
                            haptics.light()
                            glideTo(currentIndex + 1)
                        }
                    },
                    enabled = currentIndex < songs.size - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Siguiente",
                        tint = if (currentIndex < songs.size - 1) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Carrusel 3D orbital tipo tornamesa para Artistas recomendados.
 */
@Composable
fun BenchoArtistCarousel(
    artists: List<ArtistItem>,
    onArtistClick: (String) -> Unit,
    onSeeAllArtists: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    if (artists.isEmpty()) return

    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val coroutineScope = rememberCoroutineScope()
    val isDark = appColors.isDark

    val turnAnim = remember { Animatable(0f) }
    val maxTurn = (artists.size - 1).toFloat()

    val infiniteTransition = rememberInfiniteTransition(label = "artistBob")
    val bobProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "artistBobProgress"
    )

    val currentIndex = turnAnim.value.toInt().coerceIn(0, artists.size - 1)
    val currentArtist = artists[currentIndex]

    fun glideTo(target: Int) {
        val clamped = target.coerceIn(0, artists.size - 1).toFloat()
        coroutineScope.launch {
            turnAnim.animateTo(
                targetValue = clamped,
                animationSpec = tween(440, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Cabecera con título y botón "Ver todo"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title ?: "Artistas para ti",
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Ver todo",
                color = Glassmorphism.emeraldAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptics.light()
                        onSeeAllArtists()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        val density = LocalDensity.current
        val orbitPx = with(density) { 110.dp.toPx() }
        val leanPx = with(density) { 24.dp.toPx() }
        val backScale = 0.70f
        val pullPx = with(density) { 150.dp.toPx() }

        var isDragging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(artists.size) {
                    var dragStartTurn = 0f
                    var dragStartX = 0f

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragStartX = offset.x
                            dragStartTurn = turnAnim.value
                        },
                        onHorizontalDrag = { change, _ ->
                            val totalDx = change.position.x - dragStartX
                            val rawTurn = dragStartTurn - (totalDx / pullPx)
                            val clampedTurn = rawTurn.coerceIn(0f, maxTurn)
                            coroutineScope.launch {
                                turnAnim.snapTo(clampedTurn)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val target = turnAnim.value.toInt().coerceIn(0, artists.size - 1)
                            haptics.tick()
                            glideTo(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            val target = turnAnim.value.toInt().coerceIn(0, artists.size - 1)
                            glideTo(target)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val turn = turnAnim.value
            val visibleCards = mutableListOf<RenderCard<ArtistItem>>()

            for (i in artists.indices) {
                val diff = i - turn
                if (kotlin.math.abs(diff) > 2.3f) continue

                val th = diff * ((2f * PI.toFloat()) / 5.0f)
                val depth = ((cos(th) + 1.0f) / 2.0f).coerceIn(0f, 1f)

                val x = sin(th) * orbitPx
                val y = -(1.0f - depth) * leanPx
                val s = backScale + (1.0f - backScale) * depth
                val rotY = -sin(th) * 22.0f
                val bobY = if (isDragging) 0f else sin(bobProgress.toDouble() + i * 1.3).toFloat() * 8f

                visibleCards.add(
                    RenderCard(
                        index = i,
                        item = artists[i],
                        x = x,
                        y = y + bobY,
                        scale = s,
                        rotY = rotY,
                        rotZ = 0f,
                        depth = depth
                    )
                )
            }

            visibleCards.sortBy { it.depth }

            visibleCards.forEach { card ->
                val isFront = kotlin.math.abs(card.index - turn) < 0.45f
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            cameraDistance = 16f * density.density
                            translationX = card.x
                            translationY = card.y
                            scaleX = card.scale
                            scaleY = card.scale
                            rotationY = card.rotY
                        }
                        .size(130.dp)
                        .glowShadow(
                            color = Color.Black,
                            alpha = if (isDark) 0.50f else 0.16f,
                            blurRadius = if (isFront) 24.dp else 12.dp,
                            offsetY = 8.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(appColors.surface)
                        .border(
                            width = if (isFront) 2.dp else 1.2.dp,
                            color = if (isFront) Glassmorphism.emeraldAccent
                            else Color.White.copy(alpha = if (isDark) 0.12f else 0.20f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (isFront) {
                                haptics.light()
                                onArtistClick(card.item.id)
                            } else {
                                haptics.light()
                                glideTo(card.index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = appColors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(54.dp)
                    )

                    if (card.item.thumbnail != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(card.item.thumbnail.resize(width = 200))
                            .crossfade(200)
                            .build(),
                            contentDescription = card.item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // Nombre del artista enfocado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0x991E1E22) else Color(0xE6F5F5F7))
                    .clickable {
                        haptics.light()
                        onArtistClick(currentArtist.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentArtist.title,
                    color = appColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Carrusel 3D orbital tipo tornamesa para "Álbumes y singles nuevos".
 */
@Composable
fun BenchoAlbumCarousel(
    albums: List<AlbumItem>,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    if (albums.isEmpty()) return

    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val coroutineScope = rememberCoroutineScope()
    val isDark = appColors.isDark

    val turnAnim = remember { Animatable(0f) }
    val maxTurn = (albums.size - 1).toFloat()
    val cardAngles = remember { listOf(-3.8f, 2.4f, -1.8f, 3.2f, 1.2f) }

    val infiniteTransition = rememberInfiniteTransition(label = "albumBob")
    val bobProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "albumBobProgress"
    )

    val currentIndex = turnAnim.value.toInt().coerceIn(0, albums.size - 1)
    val currentAlbum = albums[currentIndex]

    fun glideTo(target: Int) {
        val clamped = target.coerceIn(0, albums.size - 1).toFloat()
        coroutineScope.launch {
            turnAnim.animateTo(
                targetValue = clamped,
                animationSpec = tween(440, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Cabecera con título y contador
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title ?: "Álbumes y singles nuevos",
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentIndex + 1} de ${albums.size}",
                color = appColors.textSecondary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Escenario del Carrusel 3D Orbital
        val density = LocalDensity.current
        val orbitPx = with(density) { 112.dp.toPx() }
        val leanPx = with(density) { 26.dp.toPx() }
        val backScale = 0.68f
        val pullPx = with(density) { 150.dp.toPx() }

        var isDragging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .pointerInput(albums.size) {
                    var dragStartTurn = 0f
                    var dragStartX = 0f
                    var lastDragX = 0f
                    var lastDragTime = 0L
                    var velocity = 0f

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragStartX = offset.x
                            lastDragX = offset.x
                            dragStartTurn = turnAnim.value
                            lastDragTime = System.currentTimeMillis()
                            velocity = 0f
                        },
                        onHorizontalDrag = { change, _ ->
                            val now = System.currentTimeMillis()
                            val dt = now - lastDragTime
                            if (dt > 0) {
                                val dx = change.position.x - lastDragX
                                velocity = (dx / dt.toFloat()) * 1000f
                                lastDragX = change.position.x
                                lastDragTime = now
                            }

                            val totalDx = change.position.x - dragStartX
                            val rawTurn = dragStartTurn - (totalDx / pullPx)
                            val clampedTurn = when {
                                rawTurn < 0f -> rawTurn * 0.28f
                                rawTurn > maxTurn -> maxTurn + (rawTurn - maxTurn) * 0.28f
                                else -> rawTurn
                            }
                            coroutineScope.launch {
                                turnAnim.snapTo(clampedTurn)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val carry = (-velocity / 750f).coerceIn(-2f, 2f)
                            val target = (turnAnim.value + carry).toInt().coerceIn(0, albums.size - 1)
                            haptics.tick()
                            glideTo(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            val target = turnAnim.value.toInt().coerceIn(0, albums.size - 1)
                            glideTo(target)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val turn = turnAnim.value
            val visibleCards = mutableListOf<RenderCard<AlbumItem>>()

            for (i in albums.indices) {
                val diff = i - turn
                if (kotlin.math.abs(diff) > 2.3f) continue

                val th = diff * ((2f * PI.toFloat()) / 5.0f)
                val depth = ((cos(th) + 1.0f) / 2.0f).coerceIn(0f, 1f)

                val x = sin(th) * orbitPx
                val y = -(1.0f - depth) * leanPx
                val s = backScale + (1.0f - backScale) * depth
                val rotY = -sin(th) * 24.0f
                val naturalAngle = cardAngles[i % cardAngles.size]
                val bobY = if (isDragging) 0f else sin(bobProgress.toDouble() + i * 1.3).toFloat() * 10f
                val bobAngle = if (isDragging) 0f else cos(bobProgress.toDouble() + i * 1.3).toFloat() * 1.2f

                visibleCards.add(
                    RenderCard(
                        index = i,
                        item = albums[i],
                        x = x,
                        y = y + bobY,
                        scale = s,
                        rotY = rotY,
                        rotZ = naturalAngle + bobAngle,
                        depth = depth
                    )
                )
            }

            visibleCards.sortBy { it.depth }

            visibleCards.forEach { card ->
                val isFront = kotlin.math.abs(card.index - turn) < 0.45f
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            cameraDistance = 16f * density.density
                            translationX = card.x
                            translationY = card.y
                            scaleX = card.scale
                            scaleY = card.scale
                            rotationY = card.rotY
                            rotationZ = card.rotZ
                        }
                        .size(width = 150.dp, height = 195.dp)
                        .glowShadow(
                            color = Color.Black,
                            alpha = if (isDark) 0.50f else 0.18f,
                            blurRadius = if (isFront) 24.dp else 14.dp,
                            offsetY = 8.dp,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(appColors.surface)
                        .border(
                            width = 1.2.dp,
                            color = if (isFront) Glassmorphism.emeraldAccent.copy(alpha = 0.50f)
                            else Color.White.copy(alpha = if (isDark) 0.12f else 0.20f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            if (isFront) {
                                haptics.medium()
                                onAlbumClick(card.item.browseId)
                            } else {
                                haptics.light()
                                glideTo(card.index)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = appColors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(card.item.thumbnail.resize(width = 280))
                            .crossfade(200)
                            .build(),
                        contentDescription = card.item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Overlay con botón de Ver/Explorar álbum sobre la tarjeta frontal
                    if (isFront) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Glassmorphism.emeraldAccent)
                                    .glowShadow(
                                        color = Glassmorphism.emeraldAccent,
                                        alpha = 0.45f,
                                        blurRadius = 14.dp,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Ver álbum",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tarjeta de Información y Controles del Álbum Frontal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0x991E1E22) else Color(0xE6F5F5F7))
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            haptics.light()
                            glideTo(currentIndex - 1)
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                        contentDescription = "Anterior",
                        tint = if (currentIndex > 0) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentAlbum.title,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentAlbum.artists?.joinToString(", ") { it.name }?.ifBlank { null }
                            ?: (currentAlbum.year?.toString() ?: "Álbum"),
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .glowShadow(
                            color = Glassmorphism.emeraldAccent,
                            alpha = 0.38f,
                            blurRadius = 10.dp,
                            offsetY = 3.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(Glassmorphism.emeraldAccent)
                        .clickable {
                            haptics.medium()
                            onAlbumClick(currentAlbum.browseId)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Ver álbum",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (currentIndex < albums.size - 1) {
                            haptics.light()
                            glideTo(currentIndex + 1)
                        }
                    },
                    enabled = currentIndex < albums.size - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Siguiente",
                        tint = if (currentIndex < albums.size - 1) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

