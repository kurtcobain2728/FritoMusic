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
import androidx.compose.material.icons.filled.PlayArrow
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
import com.frito.music.ui.components.BenchoCarousel
import com.frito.music.ui.components.BenchoArtistCarousel
import com.frito.music.ui.components.BenchoAlbumCarousel
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
                    val isArtistShelf = shelf.id == "popular_artists" || shelf.title.contains("Artistas para ti", ignoreCase = true)
                    val isVideoShelf = shelf.id == "new_music_videos" || shelf.title.contains("video", ignoreCase = true) || shelf.title.contains("vídeo", ignoreCase = true)
                    val displayItems = if (isArtistShelf) {
                        val filtered = shelf.items.filterNot { it is ArtistItem && favoriteArtistIds.contains(it.id) }
                        if (filtered.isNotEmpty()) filtered else shelf.items
                    } else {
                        shelf.items
                    }

                    if (displayItems.isNotEmpty()) {
                        val shelfArtists = displayItems.filterIsInstance<ArtistItem>()
                        val shelfSongs = displayItems.filterIsInstance<SongItem>()
                        val shelfAlbums = displayItems.filterIsInstance<AlbumItem>()

                        // 1. "Artistas para ti" con el carrusel 3D orbital tipo tornamesa
                        if (isArtistShelf && shelfArtists.isNotEmpty()) {
                            item(key = "carousel_artists_${shelf.id}") {
                                BenchoArtistCarousel(
                                    artists = shelfArtists,
                                    onArtistClick = onArtistClick,
                                    onSeeAllArtists = onSeeAllArtists,
                                    title = shelf.title
                                )
                            }
                        } else if (!isArtistShelf && !isVideoShelf && shelfSongs.isNotEmpty() &&
                            (shelf.id == "recently_played" ||
                             shelf.id == "recommendations" ||
                             shelf.title.contains("vuelve", ignoreCase = true) ||
                             shelf.title.contains("recomendac", ignoreCase = true))) {
                            // 2. "Vuelve a escuchar" y "Recomendaciones para ti" con el carrusel 3D orbital
                            item(key = "carousel_songs_${shelf.id}") {
                                BenchoCarousel(
                                    songs = shelfSongs,
                                    onPlaySong = onPlaySong,
                                    title = shelf.title
                                )
                            }
                        } else if ((shelf.id == "new_releases_albums" ||
                                    shelf.title.contains("álbumes y singles", ignoreCase = true) ||
                                    shelf.title.contains("albumes y singles", ignoreCase = true) ||
                                    shelf.title.contains("lanzamientos", ignoreCase = true)) &&
                            shelfAlbums.isNotEmpty()) {
                            // 3. "Álbumes y singles nuevos" con el carrusel 3D orbital
                            item(key = "carousel_albums_${shelf.id}") {
                                BenchoAlbumCarousel(
                                    albums = shelfAlbums,
                                    onAlbumClick = onAlbumClick,
                                    title = shelf.title
                                )
                            }
                        } else if ((isVideoShelf ||
                                    shelf.id == "new_music_videos" ||
                                    shelf.title.contains("vídeos musicales", ignoreCase = true) ||
                                    shelf.title.contains("videos musicales", ignoreCase = true)) &&
                            shelfSongs.isNotEmpty()) {
                            // 4. "Vídeos musicales nuevos" con el carrusel 3D orbital e insignia VIDEO
                            item(key = "carousel_videos_${shelf.id}") {
                                BenchoCarousel(
                                    songs = shelfSongs,
                                    onPlaySong = onPlaySong,
                                    title = shelf.title,
                                    isVideo = true
                                )
                            }
                        } else {
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
                                    if (isArtistShelf) {
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
                            }
                        }
                    }
                }

                if (isLoading) {
                    item(key = "stream_loading_indicator") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF1DB954)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Cargando recomendaciones y artistas...",
                                fontSize = 13.sp,
                                color = appColors.textSecondary
                            )
                        }
                    }
                }
            } else {
                // ─── Fallback original si aún no se han calculado las shelves ───
                // ─── Escuchado recientemente (historial personal, requiere sesión) ───
                if (recentlyPlayed.isNotEmpty()) {
                    item {
                        BenchoCarousel(
                            songs = recentlyPlayed.take(20),
                            onPlaySong = onPlaySong,
                            title = "Vuelve a escuchar"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ─── Artistas para ti (recomendados por YT Music según tu cuenta, excluyendo favoritos) ───
                if (recommendedArtists.isNotEmpty()) {
                    item {
                        BenchoArtistCarousel(
                            artists = recommendedArtists.take(15),
                            onArtistClick = onArtistClick,
                            onSeeAllArtists = onSeeAllArtists,
                            title = "Artistas para ti"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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

@Composable
fun StreamVideoCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl.resize(width = 480),
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

            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "VIDEO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
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
