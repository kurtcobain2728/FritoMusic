package com.frito.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.frito.music.data.repository.FavoriteArtistsManager
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.utils.ImageUtils
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem

@Composable
fun StreamArtistDetailScreen(
    artistId: String,
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current

    val favoriteArtists by FavoriteArtistsManager.favoriteArtists.collectAsState()
    val isFavorite = favoriteArtists.any { it.id == artistId }

    val artistPage by streamViewModel.selectedArtist.collectAsState()
    val isLoading by streamViewModel.isLoadingArtist.collectAsState()
    val errorMessage by streamViewModel.errorMessage.collectAsState()

    val listState = rememberSaveable(key = "artist_$artistId", saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(artistId) {
        streamViewModel.loadArtistDetails(artistId)
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
                        onClick = { streamViewModel.loadArtistDetails(artistId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        } else if (artistPage != null) {
            val page = artistPage!!
            val artist = page.artist
            val sections = page.sections

            // Find songs section (usually the first one with title "Songs" or similar)
            val songsSection = sections.firstOrNull { section ->
                section.items.any { it is SongItem }
            }
            val songs = songsSection?.items?.filterIsInstance<SongItem>()?.take(10) ?: emptyList()

            // Find album sections
            val albumSections = sections.filter { section ->
                section.items.any { it is AlbumItem }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
                            if (artist.thumbnail != null && artist.thumbnail!!.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageUtils.highRes(artist.thumbnail),
                                    contentDescription = artist.title,
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
                        Text(
                            text = artist.title,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        )
                    }
                }

                // Top Songs
                if (songs.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Populares",
                                color = appColors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isFavorite) Color(0xFFFFD700).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, if (isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.clickable {
                                    FavoriteArtistsManager.toggleFavorite(artist)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                                        tint = if (isFavorite) Color(0xFFFFD700) else appColors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFavorite) "Siguiendo" else "Favorito",
                                        color = if (isFavorite) Color(0xFFFFD700) else appColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(songs) { index, song ->
                        StreamArtistSongItem(
                            index = index + 1,
                            song = song,
                            onClick = { streamViewModel.playArtistSong(song, playerViewModel, queueSongs = songs) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Album Sections
                albumSections.forEach { section ->
                    val albums = section.items.filterIsInstance<AlbumItem>()
                    if (albums.isNotEmpty()) {
                        item {
                            Text(
                                text = section.title.ifEmpty { "Álbumes" },
                                color = appColors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(albums) { album ->
                                    StreamAlbumCard(
                                        title = album.title,
                                        subtitle = album.artists?.joinToString(", ") { it.name } ?: "Álbum",
                                        imageUrl = album.thumbnail,
                                        onClick = { onNavigateToAlbum(album.browseId) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
fun StreamArtistSongItem(
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
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        ) {
            if (song.thumbnail.isNotEmpty()) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
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
                text = song.album?.name ?: song.artists.joinToString(", ") { it.name },
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

@Composable
fun StreamAlbumCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = appColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
