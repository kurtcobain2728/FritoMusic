package com.frito.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.data.models.AudioFile
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.SongItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    streamViewModel: StreamViewModel,
    onBack: () -> Unit
) {
    // Reactivo: se actualiza solo cuando el escaneo o un rescan (descarga nueva) termina
    val allAudios by homeViewModel.allAudios.collectAsState()
    val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
    val appColors = LocalAppColors.current

    // Cachear el filtrado para evitar recalcular en cada recomposición
    val favoriteAudios = remember(allAudios, favorites) {
        allAudios.filter { favorites.contains(it.path) }.sortedBy { it.title }
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Gradiente derivado del acento (antes: banda rojiza fija que
                // chocaba con temas claros y con "Color predominante")
                Brush.verticalGradient(
                    colors = if (appColors.backgroundImageUri != null) {
                        listOf(appColors.accent.copy(alpha = 0.3f), Color.Transparent)
                    } else {
                        listOf(appColors.accent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f), appColors.background)
                    },
                    startY = 0f,
                    endY = 800f
                )
            )
    ) {
        // Top Bar / Back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(appColors.surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pestañas Offline | Online
        val pagerState = rememberPagerState(pageCount = { 2 })
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = appColors.textPrimary,
            divider = { HorizontalDivider(color = Color(0xFF222222)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = appColors.accent,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Offline", fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Online", fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page == 0) {
                FavoritesOfflineContent(favoriteAudios = favoriteAudios, playerViewModel = playerViewModel, appColors = appColors)
            } else {
                FavoritesOnlineContent(
                    onlineLibraryViewModel = onlineLibraryViewModel,
                    streamViewModel = streamViewModel,
                    playerViewModel = playerViewModel,
                    appColors = appColors
                )
            }
        }
    }
}

@Composable
private fun FavoritesOfflineContent(
    favoriteAudios: List<AudioFile>,
    playerViewModel: PlayerViewModel,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Header Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(appColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite Cover",
                    tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Favoritos",
                color = appColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${favoriteAudios.size} canciones",
                color = appColors.textSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Play / Shuffle Buttons if not empty
            if (favoriteAudios.isNotEmpty()) {
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
                                if (!playerViewModel.shuffleModeEnabled.value) {
                                    playerViewModel.toggleShuffle()
                                }
                                val randomIndex = favoriteAudios.indices.random()
                                playerViewModel.playAudios(favoriteAudios, randomIndex)
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
                                playerViewModel.playAudios(favoriteAudios, 0)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (favoriteAudios.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "Empty Favorites",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Toca el corazón en cualquier canción para\nagregarla aquí",
                    color = appColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            // List of favorites
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = favoriteAudios,
                    key = { it.path }
                ) { audio ->
                    val index = favoriteAudios.indexOf(audio)
                    AudioFileRowUI(song = audio, isFavorite = true, appColors = appColors, onClick = {
                        playerViewModel.playAudios(favoriteAudios, index)
                    })
                }
            }
        }
    }
}

@Composable
private fun FavoritesOnlineContent(
    onlineLibraryViewModel: OnlineLibraryViewModel,
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    appColors: com.frito.music.ui.theme.AppColors
) {
    val likedSongs by onlineLibraryViewModel.likedSongs.collectAsState()
    val likedIds by onlineLibraryViewModel.likedSongIds.collectAsState()
    val isLoading by onlineLibraryViewModel.isLoadingLiked.collectAsState()
    val error by onlineLibraryViewModel.onlineError.collectAsState()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn()
            ) {
                onlineLibraryViewModel.loadLikedSongs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = appColors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Inicia sesión para ver tus favoritos de YouTube Music", color = appColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        }
        isLoading && likedSongs.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
        }
        likedSongs.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (error != null) error!! else "No tienes canciones marcadas", color = if (error != null) Color.Red else appColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
            ) {
                items(likedSongs, key = { it.id }) { song ->
                    OnlineLikedSongRow(
                        song = song,
                        isLiked = likedIds.contains(song.id),
                        onToggleLike = { onlineLibraryViewModel.likeSong(song.id, !likedIds.contains(song.id)) },
                        onClick = { streamViewModel.playArtistSong(song, playerViewModel, queueSongs = likedSongs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineLikedSongRow(
    song: SongItem,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
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
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (song.thumbnail.isNotEmpty()) {
                AsyncImage(model = song.thumbnail, contentDescription = song.title, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = appColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString(", ") { it.name }, color = appColors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isLiked) "Quitar de Me gusta" else "Me gusta",
            tint = if (isLiked) Color(0xFFFF6B6B) else appColors.textSecondary,
            modifier = Modifier
                .size(28.dp)
                .clickable { onToggleLike() }
        )
    }
}