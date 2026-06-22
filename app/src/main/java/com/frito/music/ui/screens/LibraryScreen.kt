package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.data.models.AudioFile
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import java.util.concurrent.TimeUnit

enum class SortOption { TITLE, ARTIST, ALBUM, RECENT }

@Composable
fun LibraryScreen(homeViewModel: HomeViewModel, playerViewModel: PlayerViewModel) {
    val allSongs = remember { homeViewModel.getAllAudios() }
    val favorites by playerViewModel.favorites.collectAsState()
    var selectedFilter by remember { mutableStateOf(SortOption.TITLE) }

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
            .background(Color(0xFF121212))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp, top = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Tu Biblioteca",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${allSongs.size} canciones • $hours horas $minutes min",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // Filters
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        item { FilterChipUI("Título", selectedFilter == SortOption.TITLE) { selectedFilter = SortOption.TITLE } }
                        item { FilterChipUI("Artista", selectedFilter == SortOption.ARTIST) { selectedFilter = SortOption.ARTIST } }
                        item { FilterChipUI("Álbum", selectedFilter == SortOption.ALBUM) { selectedFilter = SortOption.ALBUM } }
                        item { FilterChipUI("Reciente", selectedFilter == SortOption.RECENT) { selectedFilter = SortOption.RECENT } }
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
                                .background(Color(0xFF2A2A2A))
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
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954))
                                .clickable {
                                    if (sortedSongs.isNotEmpty()) {
                                        playerViewModel.playAudios(sortedSongs, 0)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            items(sortedSongs) { song ->
                val isFavorite = favorites.contains(song.path)
                AudioFileRowUI(song = song, isFavorite = isFavorite) {
                    val index = sortedSongs.indexOf(song)
                    playerViewModel.playAudios(sortedSongs, if (index >= 0) index else 0)
                }
            }
        }
    }
}

@Composable
fun FilterChipUI(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF1DB954) else Color(0xFF2A2A2A))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun AudioFileRowUI(song: AudioFile, isFavorite: Boolean, onClick: () -> Unit) {
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
            AsyncImage(
                model = song.albumUri,
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
                    .background(Color.DarkGray)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val extension = song.path.substringAfterLast('.', "N/A").uppercase()
            Text(
                text = extension,
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Like",
            tint = if (isFavorite) Color(0xFFFF6B6B) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = durationText,
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}
