package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.data.models.Playlist
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import java.util.concurrent.TimeUnit

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val allAudios = homeViewModel.getAllAudios()
    val favorites by playerViewModel.favorites.collectAsState()
    val playlistSongs = allAudios.filter { playlist.audioPaths.contains(it.path) }
    
    val totalDurationMs = playlistSongs.sumOf { it.durationMs }
    val hours = TimeUnit.MILLISECONDS.toHours(totalDurationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMs) % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 32.dp)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Playlist Cover",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${playlistSongs.size} canciones • $hours h $minutes min",
                color = Color.Gray,
                fontSize = 16.sp
            )

            // Play / Shuffle Buttons
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable {
                            if (playlistSongs.isNotEmpty()) {
                                if (!playerViewModel.shuffleModeEnabled.value) {
                                    playerViewModel.toggleShuffle()
                                }
                                val randomIndex = playlistSongs.indices.random()
                                playerViewModel.playAudios(playlistSongs, randomIndex)
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
                            if (playlistSongs.isNotEmpty()) {
                                playerViewModel.playAudios(playlistSongs, 0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                }
            }
        }

        // List
        if (playlistSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hay canciones en esta lista.",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(playlistSongs) { index, song ->
                    val isFavorite = favorites.contains(song.path)
                    AudioFileRowUI(song = song, isFavorite = isFavorite, onClick = {
                        playerViewModel.playAudios(playlistSongs, index)
                    })
                }
            }
        }
    }
}
