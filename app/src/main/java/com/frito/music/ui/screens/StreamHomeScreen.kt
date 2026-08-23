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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.frito.music.ui.theme.LocalAppColors
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage

@Composable
fun StreamHomeScreen(
    homePage: HomePage?,
    explorePage: ExplorePage?,
    isLoading: Boolean,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    recentlyPlayed: List<SongItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    // Artistas recomendados: agregados de todos los estantes del home de YT Music.
    // Antes se descartaban silenciosamente en el `else -> {}`.
    val recommendedArtists = homePage?.sections
        ?.flatMap { it.items }
        ?.filterIsInstance<ArtistItem>()
        ?.distinctBy { it.id }
        .orEmpty()

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ─── Escuchado recientemente (historial personal, requiere sesión) ───
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text(
                    text = "Escuchado recientemente",
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val recentQueue = recentlyPlayed.take(10)
                    items(recentQueue) { song ->
                        SongCard(
                            song = song,
                            onClick = { onPlaySong(song, recentQueue) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ─── Artistas para ti (recomendados por YT Music según tu cuenta) ───
        if (recommendedArtists.isNotEmpty()) {
            item {
                Text(
                    text = "Artistas para ti",
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendedArtists.take(15)) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = {
                                if (artist.id.isNotBlank()) onArtistClick(artist.id)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Tendencias - from homePage
        homePage?.sections?.forEach { section ->
            item {
                Text(
                    text = section.title,
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val sectionSongs = section.items.filterIsInstance<SongItem>().take(10)
                    items(section.items.take(10)) { item ->
                        when (item) {
                            is SongItem -> SongCard(
                                song = item,
                                onClick = { onPlaySong(item, sectionSongs) }
                            )
                            is AlbumItem -> AlbumCard(
                                album = item,
                                onClick = { onAlbumClick(item.browseId) }
                            )
                            is ArtistItem -> ArtistCard(
                                artist = item,
                                onClick = {
                                    if (item.id.isNotBlank()) onArtistClick(item.id)
                                }
                            )
                            else -> {}
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Nuevos Lanzamientos - from explorePage
        explorePage?.let { page ->
            if (page.newReleaseAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "Nuevos Lanzamientos",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(page.newReleaseAlbums.take(10)) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album.browseId) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun SongCard(song: SongItem, onClick: () -> Unit) {
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
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artists.joinToString(", ") { it.name },
            color = appColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCard(artist: ArtistItem, onClick: () -> Unit) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = artist.thumbnail,
                    contentDescription = artist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumCard(album: AlbumItem, onClick: () -> Unit) {
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
            if (album.thumbnail.isNotEmpty()) {
                AsyncImage(
                    model = album.thumbnail,
                    contentDescription = album.title,
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
            text = album.title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        album.artists?.firstOrNull()?.let { artist ->
            Text(
                text = artist.name,
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
