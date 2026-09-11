package com.frito.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.R
import com.frito.music.utils.resize
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.repository.YouTubeLoginManager
import com.frito.music.ui.components.YouTubeLogoutModal
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem

enum class StreamTab {
    CANCIONES, ARTISTAS, PLAYLISTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToPlaylistDetail: (String) -> Unit = {},
    onNavigateToFavoriteArtists: () -> Unit = {},
    onNavigateToAllArtists: () -> Unit = {}
) {
    val appColors = LocalAppColors.current

    val searchResults by streamViewModel.searchResults.collectAsState()
    val artistResults by streamViewModel.artistResults.collectAsState()
    val playlistResults by streamViewModel.playlistResults.collectAsState()
    val isSearching by streamViewModel.isSearching.collectAsState()
    val errorMessage by streamViewModel.errorMessage.collectAsState()
    val playbackError by streamViewModel.playbackError.collectAsState()
    val homePage by streamViewModel.homePage.collectAsState()
    val explorePage by streamViewModel.explorePage.collectAsState()
    val isLoadingHome by streamViewModel.isLoadingHome.collectAsState()
    val recentlyPlayed by streamViewModel.recentlyPlayed.collectAsState()
    val homeShelves by streamViewModel.homeShelves.collectAsState()

    val accountAvatar by YouTubeLoginManager.accountAvatar.collectAsState()
    val accountName by YouTubeLoginManager.accountName.collectAsState()
    val accountEmail by YouTubeLoginManager.accountEmail.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(StreamTab.CANCIONES) }
    var showLogoutModal by remember { mutableStateOf(false) }

    val isLoggedIn by YouTubeLoginManager.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            if (accountAvatar.isEmpty() || accountName.isEmpty()) {
                YouTubeLoginManager.fetchAccountInfo()
            }
            // Al tener sesión activa, si aún no hay contenido o acabamos de entrar, refrescar el home
            if (streamViewModel.homeShelves.value.size < 2) {
                streamViewModel.loadHomeContent(forceRefresh = true)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Stream",
                color = appColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (isLoggedIn) {
                // Botón de Artistas Favoritos
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToFavoriteArtists() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Artistas favoritos",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Playlists button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToPlaylists() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Listas",
                        tint = appColors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Refresh button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { streamViewModel.loadHomeContent(forceRefresh = true) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar Stream",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Profile button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showLogoutModal = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    if (accountAvatar.isNotEmpty()) {
                        AsyncImage(
                            model = accountAvatar,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Cuenta",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        
        if (!isLoggedIn) {
            StreamLoginPrompt(
                onLoginClick = onNavigateToLogin
            )
        } else {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    streamViewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = "Buscar canciones, artistas...",
                        color = appColors.textSecondary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = appColors.textSecondary
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF333333))
                                .clickable { 
                                    searchQuery = "" 
                                    streamViewModel.clearSearch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = appColors.textPrimary,
                    unfocusedTextColor = appColors.textPrimary,
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Error de reproducción (resolución de URL): banner discreto, no bloquea la pantalla
            if (playbackError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3D1010))
                        .clickable { streamViewModel.clearPlaybackError() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No se pudo reproducir: ${playbackError}. Toca para cerrar.",
                        color = Color(0xFFEF9A9A),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Content
            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                            Button(
                                onClick = { streamViewModel.search(searchQuery) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1DB954)
                                )
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                searchResults == null && searchQuery.isEmpty() -> {
                    StreamHomeScreen(
                        homeShelves = homeShelves,
                        homePage = homePage,
                        explorePage = explorePage,
                        isLoading = isLoadingHome,
                        recentlyPlayed = recentlyPlayed,
                        onPlaySong = { song, sectionSongs ->
                            streamViewModel.playArtistSong(song, playerViewModel, queueSongs = sectionSongs)
                        },
                        onAlbumClick = { browseId ->
                            onNavigateToAlbum(browseId)
                        },
                        onArtistClick = { browseId ->
                            onNavigateToArtist(browseId)
                        },
                        onSeeAllArtists = onNavigateToAllArtists,
                        onRefresh = { streamViewModel.loadHomeContent(forceRefresh = true) },
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = appColors.textPrimary,
                        divider = { HorizontalDivider(color = Color(0xFF222222)) },
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                color = Color(0xFF1DB954),
                                height = 2.dp
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == StreamTab.CANCIONES,
                            onClick = { selectedTab = StreamTab.CANCIONES },
                            text = { 
                                Text(
                                    "Canciones", 
                                    fontWeight = if (selectedTab == StreamTab.CANCIONES) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            selectedContentColor = appColors.textPrimary,
                            unselectedContentColor = appColors.textSecondary
                        )
                        Tab(
                            selected = selectedTab == StreamTab.ARTISTAS,
                            onClick = { selectedTab = StreamTab.ARTISTAS },
                            text = { 
                                Text(
                                    "Artistas", 
                                    fontWeight = if (selectedTab == StreamTab.ARTISTAS) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            selectedContentColor = appColors.textPrimary,
                            unselectedContentColor = appColors.textSecondary
                        )
                        Tab(
                            selected = selectedTab == StreamTab.PLAYLISTS,
                            onClick = { selectedTab = StreamTab.PLAYLISTS },
                            text = { 
                                Text(
                                    "Playlists", 
                                    fontWeight = if (selectedTab == StreamTab.PLAYLISTS) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            selectedContentColor = appColors.textPrimary,
                            unselectedContentColor = appColors.textSecondary
                        )
                    }
                    
                    // Results
                    val results = searchResults
                    val artists = artistResults
                    if (results != null && selectedTab == StreamTab.CANCIONES) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            items(results, key = { it.videoId }) { track ->
                                StreamTrackItem(
                                    track = track,
                                    onClick = { streamViewModel.playTrack(track, playerViewModel, queue = results) }
                                )
                            }
                        }
                    } else if (artists != null && selectedTab == StreamTab.ARTISTAS) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            items(artists, key = { it.id }) { artist ->
                                StreamArtistItem(
                                    artist = artist,
                                    onClick = { onNavigateToArtist(artist.id) }
                                )
                            }
                        }
                    } else if (selectedTab == StreamTab.PLAYLISTS) {
                        val playlists = playlistResults
                        if (playlists != null && playlists.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    StreamPlaylistItem(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylistDetail(playlist.id) }
                                    )
                                }
                            }
                        } else if (playlists != null && playlists.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron listas de reproducción",
                                    color = appColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Logout Modal
    if (showLogoutModal) {
        YouTubeLogoutModal(
            accountName = accountName,
            accountEmail = accountEmail,
            accountAvatar = accountAvatar,
            onDismiss = { showLogoutModal = false },
            onLogout = {
                YouTubeLoginManager.logout()
            }
        )
    }
}

@Composable
fun StreamTrackItem(
    track: StreamableTrack,
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
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = track.thumbnailUrl.resize(width = 112),
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote, 
                    contentDescription = null, 
                    tint = appColors.textSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist}${track.album?.let { " • $it" } ?: ""}",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Play Button
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
fun StreamArtistItem(
    artist: ArtistItem,
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
        // Artist Image
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = artist.thumbnail.resize(width = 112),
                    contentDescription = artist.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Artist Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Artista",
                color = appColors.textSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Arrow indicator
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "View Artist",
            tint = appColors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun StreamPlaylistItem(
    playlist: PlaylistItem,
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
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = playlist.thumbnail.resize(width = 112),
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Playlist Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val subtitle = listOfNotNull(playlist.author?.name, playlist.songCountText).filter { it.isNotBlank() }.joinToString(" • ").ifEmpty { "Playlist" }
            Text(
                text = subtitle,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StreamLoginPrompt(
    onLoginClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Iniciar sesión",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Debes iniciar sesión si quieres escuchar música por streaming",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
