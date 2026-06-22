package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.AudioFile
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import java.util.concurrent.TimeUnit
import com.frito.music.ui.theme.LocalAppColors

enum class SortOption { TITLE, ARTIST, ALBUM, RECENT }

@Composable
fun LibraryScreen(homeViewModel: HomeViewModel, playerViewModel: PlayerViewModel) {
    // Envuelto en remember para evitar recomputación en cada recomposición
    val allSongs = remember(homeViewModel) { homeViewModel.getAllAudios() }
    val favorites by playerViewModel.favorites.collectAsState()
    var selectedFilter by remember { mutableStateOf(SortOption.TITLE) }
    val appColors = LocalAppColors.current

    val sortedSongs = remember(selectedFilter, allSongs) {
        when (selectedFilter) {
            SortOption.TITLE -> allSongs.sortedBy { it.title }
            SortOption.ARTIST -> allSongs.sortedBy { it.artist }
            SortOption.ALBUM -> allSongs.sortedBy { it.album }
            SortOption.RECENT -> allSongs.sortedByDescending { it.dateAdded }
        }
    }

    val totalDurationMs = allSongs.sumOf { it.durationMs }
    val hours = TimeUnit.MILLISECONDS.toHours(totalDurationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMs) % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp, top = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Tu Biblioteca",
                        color = appColors.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${allSongs.size} canciones • $hours horas $minutes min",
                        color = appColors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // Filters
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        item { FilterChipUI("Título", selectedFilter == SortOption.TITLE, appColors) { selectedFilter = SortOption.TITLE } }
                        item { FilterChipUI("Artista", selectedFilter == SortOption.ARTIST, appColors) { selectedFilter = SortOption.ARTIST } }
                        item { FilterChipUI("Álbum", selectedFilter == SortOption.ALBUM, appColors) { selectedFilter = SortOption.ALBUM } }
                        item { FilterChipUI("Reciente", selectedFilter == SortOption.RECENT, appColors) { selectedFilter = SortOption.RECENT } }
                    }

                    // Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(appColors.surface)
                                .clickable {
                                    if (sortedSongs.isNotEmpty()) {
                                        if (!playerViewModel.shuffleModeEnabled.value) {
                                            playerViewModel.toggleShuffle()
                                        }
                                        val randomIndex = sortedSongs.indices.random()
                                        playerViewModel.playAudios(sortedSongs, randomIndex)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = appColors.textPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(appColors.accent)
                                .clickable {
                                    if (sortedSongs.isNotEmpty()) {
                                        playerViewModel.playAudios(sortedSongs, 0)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            itemsIndexed(
                sortedSongs,
                key = { _, song -> song.path }
            ) { index, song ->
                val isFavorite = favorites.contains(song.path)
                AudioFileRowUI(song = song, isFavorite = isFavorite, appColors = appColors) {
                    playerViewModel.playAudios(sortedSongs, index)
                }
            }
        }
    }
}

@Composable
fun FilterChipUI(text: String, isSelected: Boolean, appColors: com.frito.music.ui.theme.AppColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) appColors.accent else appColors.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun AudioFileRowUI(song: AudioFile, isFavorite: Boolean, appColors: com.frito.music.ui.theme.AppColors, onClick: () -> Unit) {
    val durationText = String.format("%02d:%02d", 
        TimeUnit.MILLISECONDS.toMinutes(song.durationMs),
        TimeUnit.MILLISECONDS.toSeconds(song.durationMs) - 
        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(song.durationMs))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song.albumUri != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumUri)
                    .crossfade(300)
                    .build(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors.surface)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(appColors.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val extension = song.path.substringAfterLast('.', "N/A").uppercase()
            Text(
                text = extension,
                color = appColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Like",
            tint = if (isFavorite) Color(0xFFFF6B6B) else appColors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = durationText,
            color = appColors.textSecondary,
            fontSize = 14.sp
        )
    }
}
