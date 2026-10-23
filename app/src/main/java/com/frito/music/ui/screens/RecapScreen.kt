package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.repository.RecapManager
import com.frito.music.data.repository.RecapPeriod
import com.frito.music.data.repository.TopArtistStat
import com.frito.music.data.repository.TopSongStat
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.glowShadow
import com.frito.music.utils.ImageUtils

@Composable
fun RecapScreen(
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var selectedPeriod by remember { mutableStateOf(RecapPeriod.WEEKLY) }
    val summary = remember(selectedPeriod) { RecapManager.getSummary(selectedPeriod) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color.Transparent)
    ) {
        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Recap Musical",
                    color = appColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Tus estadísticas de escucha",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Selector de Período (Semanal, Mensual, De siempre)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PeriodTabItem(
                title = "Semanal",
                selected = selectedPeriod == RecapPeriod.WEEKLY,
                onClick = { selectedPeriod = RecapPeriod.WEEKLY },
                modifier = Modifier.weight(1f),
                appColors = appColors
            )
            PeriodTabItem(
                title = "Mensual",
                selected = selectedPeriod == RecapPeriod.MONTHLY,
                onClick = { selectedPeriod = RecapPeriod.MONTHLY },
                modifier = Modifier.weight(1f),
                appColors = appColors
            )
            PeriodTabItem(
                title = "De siempre",
                selected = selectedPeriod == RecapPeriod.ALL_TIME,
                onClick = { selectedPeriod = RecapPeriod.ALL_TIME },
                modifier = Modifier.weight(1f),
                appColors = appColors
            )
        }

        // Contenido con animación de cambio de período
        AnimatedContent(
            targetState = summary,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "recapContent"
        ) { currentSummary ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta Héroe: Minutos Totales
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glowShadow(
                                color = Glassmorphism.emeraldGradientStart,
                                alpha = 0.25f,
                                blurRadius = 24.dp,
                                offsetY = 6.dp,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0F382A),
                                        Color(0xFF132A22)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Glassmorphism.emeraldAccent.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Glassmorphism.emeraldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "TIEMPO ESCUCHADO",
                                        color = Glassmorphism.emeraldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Glassmorphism.emeraldAccent.copy(alpha = 0.20f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when (currentSummary.period) {
                                            RecapPeriod.WEEKLY -> "Últimos 7 días"
                                            RecapPeriod.MONTHLY -> "Últimos 30 días"
                                            RecapPeriod.ALL_TIME -> "Histórico"
                                        },
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "${currentSummary.totalMinutes}",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.5).sp
                            )
                            Text(
                                text = "minutos de música disfrutados",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MiniMetricBox(
                                    label = "Reproducciones",
                                    value = "${currentSummary.totalPlays}",
                                    modifier = Modifier.weight(1f)
                                )
                                MiniMetricBox(
                                    label = "Pistas únicas",
                                    value = "${currentSummary.uniqueTracksCount}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Tarjeta de Artista Top
                if (currentSummary.topArtists.isNotEmpty()) {
                    val topArtist = currentSummary.topArtists.first()
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(appColors.surface)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = if (isDark) 0.08f else 0.12f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    appColors.accent,
                                                    Glassmorphism.emeraldAccent
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(appColors.accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TU ARTISTA TOP",
                                            color = appColors.accent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = topArtist.name,
                                        color = appColors.textPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${topArtist.playCount} canciones reproducidas (${topArtist.minutesPlayed} min)",
                                        color = appColors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sección: Top Canciones
                item {
                    Text(
                        text = "Tus Canciones Más Escuchadas",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (currentSummary.topSongs.isEmpty()) {
                    item {
                        Text(
                            text = "Aún no hay reproducciones registradas para este período.",
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(currentSummary.topSongs) { index, song ->
                        TopSongRow(rank = index + 1, song = song, appColors = appColors)
                    }
                }

                // Sección: Top Artistas
                item {
                    Text(
                        text = "Tus Artistas Principales",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (currentSummary.topArtists.isEmpty()) {
                    item {
                        Text(
                            text = "Aún no hay suficientes datos de artistas.",
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(currentSummary.topArtists) { index, artist ->
                        TopArtistRow(rank = index + 1, artist = artist, appColors = appColors)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodTabItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) appColors.accent
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else appColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MiniMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TopSongRow(
    rank: Int,
    song: TopSongStat,
    appColors: com.frito.music.ui.theme.AppColors
) {
    val context = LocalContext.current
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> appColors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = rankColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(appColors.background),
            contentAlignment = Alignment.Center
        ) {
            if (!song.albumUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ImageUtils.highRes(song.albumUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${song.playCount}x",
                color = appColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${song.minutesPlayed} min",
                color = appColors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TopArtistRow(
    rank: Int,
    artist: TopArtistStat,
    appColors: com.frito.music.ui.theme.AppColors
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> appColors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = rankColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(appColors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = appColors.accent,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.playCount} reproducciones",
                color = appColors.textSecondary,
                fontSize = 12.sp
            )
        }

        Text(
            text = "${artist.percentage}%",
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
