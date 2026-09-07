package com.frito.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.LyricLine
import com.frito.music.data.models.LyricsData
import com.frito.music.data.models.LyricsUiState
import com.frito.music.utils.ImageUtils
import kotlinx.coroutines.delay

@Composable
fun SyncedLyricsView(
    lyricsState: LyricsUiState,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    albumArtUri: String?,
    title: String,
    artist: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
    ) {
        // 1. Ambient Blurred Backdrop
        if (!albumArtUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ImageUtils.highRes(albumArtUri))
                    .crossfade(500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(70.dp)
            )
        }

        // Dark gradient overlay for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Drag handle pill & close bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                        .clickable { onClose() }
                )
            }

            // Top Header: Title, Artist, Badges, and Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Sin título" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist.ifBlank { "Artista desconocido" },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge: Sincronizada / Texto
                    when (lyricsState) {
                        is LyricsUiState.Success -> {
                            val isSynced = lyricsState.lyrics.isSynced && lyricsState.lyrics.lines.isNotEmpty()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSynced) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSynced) accentColor else Color.White.copy(alpha = 0.6f))
                                    )
                                    Text(
                                        text = if (isSynced) "Sincronizada" else "Texto",
                                        color = if (isSynced) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    // Refresh Button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar letra",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Cerrar letra",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body content according to state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (lyricsState) {
                    is LyricsUiState.Loading, is LyricsUiState.Idle -> {
                        LoadingLyricsState(accentColor = accentColor)
                    }
                    is LyricsUiState.Empty -> {
                        EmptyLyricsState(onRefresh = onRefresh, accentColor = accentColor)
                    }
                    is LyricsUiState.Success -> {
                        val lyrics = lyricsState.lyrics
                        if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                            SyncedLyricsContent(
                                lines = lyrics.lines,
                                positionMs = positionMs,
                                onSeekTo = onSeekTo,
                                accentColor = accentColor
                            )
                        } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                            PlainLyricsContent(plainLyrics = lyrics.plainLyrics)
                        } else {
                            EmptyLyricsState(onRefresh = onRefresh, accentColor = accentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsContent(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    accentColor: Color
) {
    val listState = rememberLazyListState()

    // Calculate active line index (last line where timestampMs <= current position)
    val activeIndex = remember(lines, positionMs) {
        val idx = lines.indexOfLast { it.timestampMs <= positionMs }
        if (idx >= 0) idx else 0
    }

    // Detect user manual scrolling to pause auto-center temporarily
    var userHasScrolledManually by remember { mutableStateOf(false) }

    // If user interacts with the list, mark as manual scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userHasScrolledManually = true
        }
    }

    // Auto-scroll to keep active line vertically centered (offset by ~2 lines)
    LaunchedEffect(activeIndex, userHasScrolledManually) {
        if (!userHasScrolledManually && activeIndex in lines.indices) {
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 100.dp,
                bottom = 160.dp
            )
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> Color.White
                        isPast -> Color.White.copy(alpha = 0.40f)
                        else -> Color.White.copy(alpha = 0.28f)
                    },
                    animationSpec = tween(durationMillis = 250),
                    label = "textColor"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.03f else 1.0f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "textScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isActive) 14.dp else 10.dp)
                        .scale(scale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSeekTo(line.timestampMs)
                            userHasScrolledManually = false
                        }
                ) {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = textColor,
                        fontSize = if (isActive) 26.sp else 21.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        lineHeight = if (isActive) 34.sp else 28.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        // Floating pill to re-sync / jump back to current line
        AnimatedVisibility(
            visible = userHasScrolledManually,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = accentColor.copy(alpha = 0.92f),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    userHasScrolledManually = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sincronizar letra",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsContent(plainLyrics: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp)
    ) {
        item {
            Text(
                text = plainLyrics,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 18.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoadingLyricsState(accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(
            color = accentColor,
            modifier = Modifier.size(44.dp),
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Buscando letra sincronizada...",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyLyricsState(onRefresh: () -> Unit, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Letra no disponible",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No encontramos la letra de esta canción en los servidores ni en tu dispositivo.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.clickable { onRefresh() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reintentar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Reintentar",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
