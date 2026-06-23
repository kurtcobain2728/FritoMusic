package com.frito.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.frito.music.data.models.Playlist
import com.frito.music.ui.components.BottomNavBar
import com.frito.music.ui.screens.*
import com.frito.music.ui.theme.FritoMusicTheme
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.DownloadViewModel
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.LocalAppColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()
            val downloadViewModel: DownloadViewModel = viewModel()

            val themeMode by themeViewModel.themeMode.collectAsState()
            val accentColor by themeViewModel.accentColor.collectAsState()
            val backgroundImageUri by themeViewModel.backgroundImageUri.collectAsState()
            val backgroundBlur by themeViewModel.backgroundBlur.collectAsState()
            val isDark = themeViewModel.isDarkThemeActive()

            FritoMusicTheme(
                themeMode = themeMode,
                accentColorValue = accentColor,
                backgroundImageUri = backgroundImageUri,
                isDark = isDark
            ) {
                var currentTab by remember { mutableStateOf("inicio") }
                var currentSubScreen by remember { mutableStateOf<String?>(null) }
                var showPlayerScreen by remember { mutableStateOf(false) }
                var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
                var selectedArtistId by remember { mutableStateOf<String?>(null) }
                var selectedAlbumId by remember { mutableStateOf<String?>(null) }

                val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
                val playlists by playerViewModel.playlists.collectAsState(initial = emptyList())
                val currentAudio by playerViewModel.currentAudio.collectAsState()

                val context = androidx.compose.ui.platform.LocalContext.current
                var backPressedTime by remember { mutableStateOf(0L) }

                androidx.activity.compose.BackHandler(enabled = true) {
                    if (showPlayerScreen) {
                        showPlayerScreen = false
                    } else if (currentSubScreen == "playlist_detail") {
                        currentSubScreen = "listas"
                        selectedPlaylist = null
                    } else if (currentSubScreen == "artist_detail" || currentSubScreen == "album_detail") {
                        currentSubScreen = "descargar"
                        selectedArtistId = null
                        selectedAlbumId = null
                    } else if (currentSubScreen != null) {
                        currentSubScreen = null
                    } else {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - backPressedTime < 2000) {
                            (context as? android.app.Activity)?.finish()
                        } else {
                            backPressedTime = currentTime
                            android.widget.Toast.makeText(context, "Presiona atrás de nuevo para salir", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val appColors = LocalAppColors.current

                Box(modifier = Modifier.fillMaxSize()) {
                    // Pintar fondo global si existe
                    if (backgroundImageUri != null) {
                        AsyncImage(
                            model = backgroundImageUri,
                            contentDescription = "Background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (backgroundBlur > 0f)
                                        Modifier.blur(radius = backgroundBlur.dp)
                                    else Modifier
                                )
                        )
                    }

                    Scaffold(
                        bottomBar = {
                            Column {
                                // MiniPlayer con animación suave de aparición/desaparición
                                AnimatedVisibility(
                                    visible = currentAudio != null,
                                    enter = fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                                            expandVertically(tween(300, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                                            shrinkVertically(tween(250, easing = FastOutSlowInEasing))
                                ) {
                                    MiniPlayer(
                                        viewModel = playerViewModel,
                                        onClick = { showPlayerScreen = true },
                                        onSwipeUp = { showPlayerScreen = true }
                                    )
                                }
                                if (currentSubScreen == null) {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(tween(200)),
                                        exit = fadeOut(tween(150))
                                    ) {
                                        BottomNavBar(
                                            currentTab = currentTab,
                                            onTabSelected = { currentTab = it }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        containerColor = appColors.background
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier.padding(innerPadding).fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            // Animación de subpantallas (Más → Favoritos, Ecualizador, etc.)
                            AnimatedContent(
                                targetState = currentSubScreen,
                                transitionSpec = {
                                    if (targetState != null) {
                                        // Entrando a una subpantalla: slide desde la derecha + fade in
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        ) + fadeIn(tween(250, easing = FastOutSlowInEasing)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(200, easing = FastOutSlowInEasing))
                                            )
                                    } else {
                                        // Volviendo a tabs: slide desde la izquierda + fade in
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        ) + fadeIn(tween(250, easing = FastOutSlowInEasing)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(200, easing = FastOutSlowInEasing))
                                            )
                                    }
                                },
                                label = "SubScreenAnimation"
                            ) { subScreen ->
                                if (subScreen != null) {
                                    when (subScreen) {
                                        "favoritos" -> FavoritesScreen(
                                            homeViewModel = homeViewModel,
                                            playerViewModel = playerViewModel,
                                            onBack = { currentSubScreen = null }
                                        )
                                        "listas" -> PlaylistsScreen(
                                            playerViewModel = playerViewModel,
                                            onBack = { currentSubScreen = null },
                                            onPlaylistClick = { playlist ->
                                                selectedPlaylist = playlist
                                                currentSubScreen = "playlist_detail"
                                            }
                                        )
                                        "playlist_detail" -> {
                                            selectedPlaylist?.let { playlist ->
                                                PlaylistDetailScreen(
                                                    playlist = playlist,
                                                    homeViewModel = homeViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onBack = {
                                                        currentSubScreen = "listas"
                                                        selectedPlaylist = null
                                                    }
                                                )
                                            }
                                        }
                                        "ecualizador" -> EqualizerScreen(playerViewModel = playerViewModel, onBack = { currentSubScreen = null })
                                        "apariencia" -> AppearanceScreen(themeViewModel = themeViewModel, onBack = { currentSubScreen = null })
                                        "donaciones" -> DonationsScreen(onBack = { currentSubScreen = null })
                                        "descargar" -> DownloadScreen(
                                            onBack = { currentSubScreen = null },
                                            onNavigateToArtist = { id ->
                                                selectedArtistId = id
                                                currentSubScreen = "artist_detail"
                                            },
                                            onNavigateToAlbum = { id ->
                                                selectedAlbumId = id
                                                currentSubScreen = "album_detail"
                                            },
                                            viewModel = downloadViewModel
                                        )
                                        "artist_detail" -> {
                                            selectedArtistId?.let { id ->
                                                ArtistDetailScreen(
                                                    artistId = id,
                                                    viewModel = downloadViewModel,
                                                    onNavigateToAlbum = { albumId ->
                                                        selectedAlbumId = albumId
                                                        currentSubScreen = "album_detail"
                                                    },
                                                    onBack = { currentSubScreen = "descargar" }
                                                )
                                            }
                                        }
                                        "album_detail" -> {
                                            selectedAlbumId?.let { id ->
                                                AlbumScreen(
                                                    albumId = id,
                                                    viewModel = downloadViewModel,
                                                    onBack = { currentSubScreen = "descargar" }
                                                )
                                            }
                                        }
                                        "extensiones" -> ExtensionsScreen(onBack = { currentSubScreen = null })
                                        else -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Pantalla en construcción", color = Color.White)
                                            }
                                        }
                                    }
                                } else {
                                    // Animación entre tabs principales con fade suave
                                    AnimatedContent(
                                        targetState = currentTab,
                                        transitionSpec = {
                                            fadeIn(tween(220, easing = FastOutSlowInEasing))
                                                .togetherWith(fadeOut(tween(150, easing = FastOutSlowInEasing)))
                                        },
                                        label = "TabAnimation"
                                    ) { tab ->
                                        when (tab) {
                                            "inicio" -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                            "biblioteca" -> LibraryScreen(
                                                homeViewModel = homeViewModel,
                                                playerViewModel = playerViewModel
                                            )
                                            "buscar" -> SearchScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                            "stream" -> StreamScreen()
                                            "mas" -> MoreScreen(
                                                favoritesCount = favorites.size,
                                                playlistsCount = playlists.size,
                                                onNavigateToFavorites = { currentSubScreen = "favoritos" },
                                                onNavigateToPlaylists = { currentSubScreen = "listas" },
                                                onNavigateToEqualizer = { currentSubScreen = "ecualizador" },
                                                onNavigateToAppearance = { currentSubScreen = "apariencia" },
                                                onNavigateToDonations = { currentSubScreen = "donaciones" },
                                                onNavigateToDownload = { currentSubScreen = "descargar" },
                                                onNavigateToExtensions = { currentSubScreen = "extensiones" }
                                            )
                                            else -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Player Overlay — spring + fade para sensación física
                    AnimatedVisibility(
                        visible = showPlayerScreen,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(tween(250, easing = FastOutSlowInEasing)),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight },
                            animationSpec = tween(360, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(250, easing = FastOutSlowInEasing))
                    ) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            onClose = { showPlayerScreen = false }
                        )
                    }
                }
            }
        }
    }
}
