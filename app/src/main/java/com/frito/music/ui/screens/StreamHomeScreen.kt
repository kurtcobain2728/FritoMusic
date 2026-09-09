package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.frito.music.utils.resize
import com.frito.music.data.repository.FavoriteArtistsManager
import com.frito.music.ui.components.FritoPullRefresh
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.data.models.HomeShelf
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage

@Composable
fun StreamHomeScreen(
    homeShelves: List<HomeShelf> = emptyList(),
    homePage: HomePage? = null,
    explorePage: ExplorePage? = null,
    isLoading: Boolean = false,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSeeAllArtists: () -> Unit = {},
    onRefresh: () -> Unit = {},
    recentlyPlayed: List<SongItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val favoriteArtists by FavoriteArtistsManager.favoriteArtists.collectAsState()
    val favoriteArtistIds = remember(favoriteArtists) {
        favoriteArtists.map { it.id }.filter { it.isNotBlank() }.toSet()
    }
    val listState = rememberSaveable(key = "stream_home_list_state", saver = LazyListState.Saver) {
        LazyListState()
    }

    // Artistas recomendados de respaldo (excluyendo los que ya son favoritos)
    val recommendedArtists = remember(homePage, favoriteArtistIds) {
        homePage?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<ArtistItem>()
            ?.filterNot { favoriteArtistIds.contains(it.id) }
            ?.distinctBy { it.id }
            .orEmpty()
    }

    if (isLoading && homeShelves.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
        return
    }

    FritoPullRefresh(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (homeShelves.isNotEmpty()) {
                // ─── Renderizado dinámico de carruseles curados por el algoritmo ───
                homeShelves.forEach { shelf ->
                    // EXCLUSIÓN INTELIGENTE: Filtrar artistas favoritos de las recomendaciones si hay alternativas
                    val displayItems = if (shelf.id == "popular_artists" || shelf.title.contains("Artistas para ti", ignoreCase = true)) {
                        val filtered = shelf.items.filterNot { it is ArtistItem && favoriteArtistIds.contains(it.id) }
                        if (filtered.isNotEmpty()) filtered else shelf.items
                    } else {
                        shelf.items
                    }

                    if (displayItems.isNotEmpty()) {
                        item(key = shelf.id) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = shelf.title,
                                    color = appColors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (shelf.id == "popular_artists" || shelf.title.contains("Artistas para ti", ignoreCase = true)) {
                                    Text(
                                        text = "Ver todo",
                                        color = Color(0xFF1DB954),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable { onSeeAllArtists() }
                                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val shelfSongs = displayItems.filterIsInstance<SongItem>()
                                items(
                                    displayItems,
                                    key = { item ->
                                        when (item) {
                                            is SongItem -> "${shelf.id}_song_${item.id}"
                                            is AlbumItem -> "${shelf.id}_album_${item.browseId}"
                                            is ArtistItem -> "${shelf.id}_artist_${item.id}"
                                            else -> "${shelf.id}_${item.hashCode()}"
                                        }
                                    }
                                ) { item ->
                                    when (item) {
                                        is SongItem -> SongCard(
                                            song = item,
                                            onClick = { onPlaySong(item, shelfSongs) }
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
                }
            } else {
                // ─── Fallback original si aún no se han calculado las shelves ───
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
                            items(recentQueue, key = { it.id }) { song ->
                                SongCard(
                                    song = song,
                                    onClick = { onPlaySong(song, recentQueue) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ─── Artistas para ti (recomendados por YT Music según tu cuenta, excluyendo favoritos) ───
                if (recommendedArtists.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Artistas para ti",
                                color = appColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Ver todo",
                                color = Color(0xFF1DB954),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { onSeeAllArtists() }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recommendedArtists.take(15), key = { it.id }) { artist ->
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
                            items(
                                section.items.take(10),
                                key = { item ->
                                    when (item) {
                                        is SongItem -> "${section.title}_song_${item.id}"
                                        is AlbumItem -> "${section.title}_album_${item.browseId}"
                                        is ArtistItem -> "${section.title}_artist_${item.id}"
                                        else -> "${section.title}_${item.hashCode()}"
                                    }
                                }
                            ) { item ->
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
                                items(page.newReleaseAlbums.take(10), key = { it.browseId }) { album ->
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
                    model = song.thumbnail.resize(width = 320),
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
                    model = artist.thumbnail.resize(width = 240),
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
                    model = album.thumbnail.resize(width = 320),
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
