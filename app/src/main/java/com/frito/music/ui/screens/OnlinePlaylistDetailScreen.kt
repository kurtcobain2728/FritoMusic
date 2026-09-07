package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.music.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistDetailScreen(
    playlistId: String,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val page by onlineLibraryViewModel.playlistSongs.collectAsState()
    val error by onlineLibraryViewModel.onlineError.collectAsState()

    LaunchedEffect(playlistId) {
        onlineLibraryViewModel.clearPlaylistSongs()
        onlineLibraryViewModel.loadPlaylistSongs(playlistId)
    }

    val songs = remember(page) { page?.songs ?: emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.playlist?.title ?: "Lista", color = appColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = appColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.background)
            )
        },
        containerColor = appColors.background
    ) { padding ->
        when {
            page == null && error == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
            songs.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Lista vacía", color = if (error != null) Color.Red else appColors.textSecondary, fontSize = 16.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                streamViewModel.playAlbumSong(songs.first(), playerViewModel, queueSongs = songs)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.accent),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent))
                        Spacer(Modifier.width(8.dp))
                        Text("Reproducir todo", color = com.frito.music.ui.theme.textColorForBackground(appColors.accent), fontWeight = FontWeight.Bold)
                    }
                }
                itemsIndexed(songs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { streamViewModel.playAlbumSong(song, playerViewModel, queueSongs = songs) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text((index + 1).toString(), color = appColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(28.dp))
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.thumbnail.isNotEmpty()) AsyncImage(model = song.thumbnail, contentDescription = song.title, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists.joinToString(", ") { it.name }, color = appColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Quitar de la lista",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(24.dp).clickable {
                                val sid = song.setVideoId
                                if (sid != null) {
                                    onlineLibraryViewModel.removeFromOnlinePlaylist(playlistId, song.id, sid)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
