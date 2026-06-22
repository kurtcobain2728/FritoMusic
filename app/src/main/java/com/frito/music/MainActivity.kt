package com.frito.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.frito.music.ui.components.BottomNavBar
import com.frito.music.ui.screens.HomeScreen
import com.frito.music.ui.screens.LibraryScreen
import com.frito.music.ui.screens.MoreScreen
import com.frito.music.ui.screens.SearchScreen
import com.frito.music.ui.theme.FritoMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FritoMusicTheme {
                var currentTab by remember { mutableStateOf("inicio") }
                var currentSubScreen by remember { mutableStateOf<String?>(null) }
                var showPlayerScreen by remember { mutableStateOf(false) }

                if (showPlayerScreen) {
                    com.frito.music.ui.screens.PlayerScreen(onClose = { showPlayerScreen = false })
                } else if (currentSubScreen == "favoritos") {
                    com.frito.music.ui.screens.FavoritesScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "listas") {
                    com.frito.music.ui.screens.PlaylistsScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "ecualizador") {
                    com.frito.music.ui.screens.EqualizerScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "apariencia") {
                    com.frito.music.ui.screens.AppearanceScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "donaciones") {
                    com.frito.music.ui.screens.DonationsScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "descargar") {
                    com.frito.music.ui.screens.DownloadScreen(onBack = { currentSubScreen = null })
                } else if (currentSubScreen == "extensiones") {
                    com.frito.music.ui.screens.ExtensionsScreen(onBack = { currentSubScreen = null })
                } else {
                    Scaffold(
                        bottomBar = { 
                            Column {
                                com.frito.music.ui.screens.MiniPlayer(onClick = { showPlayerScreen = true })
                                BottomNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it }
                                ) 
                            }
                        },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = currentTab,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
                            label = "TabAnimation"
                        ) { tab ->
                            when (tab) {
                                "inicio" -> HomeScreen()
                                "biblioteca" -> LibraryScreen()
                                "buscar" -> SearchScreen()
                                "mas" -> MoreScreen(
                                    onNavigateToFavorites = { currentSubScreen = "favoritos" },
                                    onNavigateToPlaylists = { currentSubScreen = "listas" },
                                    onNavigateToEqualizer = { currentSubScreen = "ecualizador" },
                                    onNavigateToAppearance = { currentSubScreen = "apariencia" },
                                    onNavigateToDonations = { currentSubScreen = "donaciones" },
                                    onNavigateToDownload = { currentSubScreen = "descargar" },
                                    onNavigateToExtensions = { currentSubScreen = "extensiones" }
                                )
                                else -> HomeScreen()
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
