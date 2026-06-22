package com.frito.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.animation.togetherWith
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

                val context = androidx.compose.ui.platform.LocalContext.current
                var backPressedTime by remember { mutableStateOf(0L) }

                androidx.activity.compose.BackHandler(enabled = true) {
                    if (showPlayerScreen) {
                        showPlayerScreen = false
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

                Box(modifier = Modifier.fillMaxSize()) {
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
                        when (subScreen) {
                            "favoritos" -> com.frito.music.ui.screens.FavoritesScreen(onBack = { currentSubScreen = null })
                            "listas" -> com.frito.music.ui.screens.PlaylistsScreen(onBack = { currentSubScreen = null })
                            "ecualizador" -> com.frito.music.ui.screens.EqualizerScreen(onBack = { currentSubScreen = null })
                            "apariencia" -> com.frito.music.ui.screens.AppearanceScreen(onBack = { currentSubScreen = null })
                            "donaciones" -> com.frito.music.ui.screens.DonationsScreen(onBack = { currentSubScreen = null })
                            "descargar" -> com.frito.music.ui.screens.DownloadScreen(onBack = { currentSubScreen = null })
                            "extensiones" -> com.frito.music.ui.screens.ExtensionsScreen(onBack = { currentSubScreen = null })
                            else -> {
                                Scaffold(
                                    bottomBar = { 
                                        Column {
                                            com.frito.music.ui.screens.MiniPlayer(
                                                onClick = { showPlayerScreen = true },
                                                onSwipeUp = { showPlayerScreen = true }
                                            )
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

                    // Player Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPlayerScreen,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(400)),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(400))
                    ) {
                        com.frito.music.ui.screens.PlayerScreen(onClose = { showPlayerScreen = false })
                    }
                }
            }
        }
    }
}
