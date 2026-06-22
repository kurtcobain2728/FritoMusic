package com.frito.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frito.music.data.models.Playlist
import com.frito.music.ui.components.BottomNavBar
import com.frito.music.ui.screens.*
import com.frito.music.ui.theme.FritoMusicTheme
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.FritoMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()

            val themeMode by themeViewModel.themeMode.collectAsState()
            val accentColor by themeViewModel.accentColor.collectAsState()
            val backgroundImageUri by themeViewModel.backgroundImageUri.collectAsState()
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

                val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
                val playlists by playerViewModel.playlists.collectAsState(initial = emptyList())

                val context = androidx.compose.ui.platform.LocalContext.current
                var backPressedTime by remember { mutableStateOf(0L) }

                androidx.activity.compose.BackHandler(enabled = true) {
                    if (showPlayerScreen) {
                        showPlayerScreen = false
                    } else if (currentSubScreen == "playlist_detail") {
                        currentSubScreen = "listas"
                        selectedPlaylist = null
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

                // Colores del tema actual
                val appColors = com.frito.music.ui.theme.LocalAppColors.current

                Box(modifier = Modifier.fillMaxSize()) {
                    // Pintar fondo global si existe
                    if (backgroundImageUri != null) {
                        AsyncImage(
                            model = backgroundImageUri,
                            contentDescription = "Background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Scaffold(
                        bottomBar = {
                            Column {
                                com.frito.music.ui.screens.MiniPlayer(
                                    viewModel = playerViewModel,
                                    onClick = { showPlayerScreen = true },
                                    onSwipeUp = { showPlayerScreen = true }
                                )
                                if (currentSubScreen == null) {
                                    BottomNavBar(
                                        currentTab = currentTab,
                                        onTabSelected = { currentTab = it }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        containerColor = appColors.background
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier.padding(innerPadding).fillMaxSize(),
                            color = Color.Transparent // Surface transparente para ver Scaffold bg
                        ) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = currentSubScreen,
                                transitionSpec = {
                                    if (targetState != null) {
                                        slideIntoContainer(towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = androidx.compose.animation.core.tween(300)).togetherWith(
                                        slideOutOfContainer(towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = androidx.compose.animation.core.tween(300)))
                                    } else {
                                        slideIntoContainer(towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = androidx.compose.animation.core.tween(300)).togetherWith(
                                        slideOutOfContainer(towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = androidx.compose.animation.core.tween(300)))
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
                                        "descargar" -> DownloadScreen(onBack = { currentSubScreen = null })
                                        "extensiones" -> ExtensionsScreen(onBack = { currentSubScreen = null })
                                        else -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Pantalla en construcción", color = Color.White)
                                            }
                                        }
                                    }
                                } else {
                                    androidx.compose.animation.Crossfade(
                                        targetState = currentTab,
                                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
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

                    // Player Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPlayerScreen,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(400)),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(400))
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
