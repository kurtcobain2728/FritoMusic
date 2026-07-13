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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.SongItem

@Composable
fun StreamAlbumDetailScreen(
    albumId: String,
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current

    val albumPage by streamViewModel.selectedAlbum.collectAsState()
    val isLoading by streamViewModel.isLoadingAlbum.collectAsState()
    val errorMessage by streamViewModel.errorMessage.collectAsState()

    LaunchedEffect(albumId) {
        streamViewModel.loadAlbumDetails(albumId)
    }

    DisposableEffect(Unit) {
        onDispose {
            streamViewModel.clearSelectedAlbum()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = { streamViewModel.loadAlbumDetails(albumId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        } else if (albumPage != null) {
            val page = albumPage!!
            val album = page.album
            val songs = page.songs

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Header
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
                            if (album.thumbnail != null) {
                                AsyncImage(
                                    model = album.thumbnail,
                                    contentDescription = album.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 48.dp, start = 16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, appColors.background),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        ) {
                            Text(
                                text = album.title,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artists?.joinToString(", ") { it.name } ?: "Álbum",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                            if (songs.isNotEmpty()) {
                                Text(
                                    text = "${songs.size} canciones",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Play All button
                if (songs.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                streamViewModel.playAlbumSong(songs.first(), playerViewModel)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reproducir todo",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Songs list
                itemsIndexed(songs) { index, song ->
                    StreamAlbumSongItem(
                        index = index + 1,
                        song = song,
                        onClick = { streamViewModel.playAlbumSong(song, playerViewModel) }
                    )
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
fun StreamAlbumSongItem(
    index: Int,
    song: SongItem,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            color = appColors.textSecondary,
            fontSize = 16.sp,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.joinToString(", ") { it.name },
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1DB954))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
