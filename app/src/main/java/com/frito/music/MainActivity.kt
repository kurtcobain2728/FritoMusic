package com.frito.music

import android.accounts.AccountManager
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import com.frito.music.ui.components.HomeSelectionBottomBar
import com.frito.music.ui.screens.*
import com.frito.music.ui.theme.FritoMusicTheme
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.AppAnimations

data class StreamNavEntry(
    val screen: String,
    val artistId: String? = null,
    val albumId: String? = null,
    val playlistId: String? = null
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: dibuja bajo status/navigation bar. Los insets se manejan
        // con WindowInsets en Compose (ver padding de headers en pantallas).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FritoMusicPrefs", android.content.Context.MODE_PRIVATE)
        val hasCompletedOnboardingInitial = prefs.getBoolean("has_completed_onboarding", false)

        // Initialize YouTube Login Manager and load saved session
        com.frito.music.data.repository.YouTubeLoginManager.init(this)
        com.frito.music.data.repository.YouTubeLoginManager.loadLoginToYouTube()
        com.frito.music.data.repository.FavoriteArtistsManager.init(this)
        com.frito.music.data.repository.StreamHistoryManager.init(this)
        com.frito.music.data.repository.HiddenItemsManager.init(this)
        com.frito.music.data.repository.CustomNamesManager.init(this)

        setContent {
            var showOnboarding by remember { mutableStateOf(!hasCompletedOnboardingInitial) }
            val themeViewModel: ThemeViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()
            val streamViewModel: StreamViewModel = viewModel()
            val onlineLibraryViewModel: OnlineLibraryViewModel = viewModel()

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
                if (showOnboarding) {
                    com.frito.music.ui.screens.OnboardingScreen(
                        onFinish = {
                            prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                            showOnboarding = false
                            homeViewModel.rescan()
                        }
                    )
                } else {
                    // rememberSaveable: el estado de navegación sobrevive rotaciones
                    var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("inicio") }
                    var currentSubScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                    var showPlayerScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) } // no parcelable: se pierde con rotación (aceptable)
                    var selectedStreamArtistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                    var selectedStreamAlbumId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                    var selectedStreamPlaylistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                    var isNavigatingBack by remember { mutableStateOf(false) }

                    val streamNavStack = androidx.compose.runtime.saveable.rememberSaveable(
                        saver = androidx.compose.runtime.saveable.listSaver(
                            save = { list ->
                                list.map { "${it.screen}|${it.artistId.orEmpty()}|${it.albumId.orEmpty()}|${it.playlistId.orEmpty()}" }
                            },
                            restore = { savedList ->
                                val list = mutableStateListOf<StreamNavEntry>()
                                savedList.forEach { str ->
                                    val parts = str.split("|")
                                    list.add(
                                        StreamNavEntry(
                                            screen = parts.getOrElse(0) { "" },
                                            artistId = parts.getOrNull(1)?.ifEmpty { null },
                                            albumId = parts.getOrNull(2)?.ifEmpty { null },
                                            playlistId = parts.getOrNull(3)?.ifEmpty { null }
                                        )
                                    )
                                }
                                list
                            }
                        )
                    ) {
                        mutableStateListOf<StreamNavEntry>()
                    }

                    val pushStreamScreen: (StreamNavEntry) -> Unit = { entry ->
                        isNavigatingBack = false
                        streamNavStack.add(entry)
                        currentSubScreen = entry.screen
                        if (entry.artistId != null) selectedStreamArtistId = entry.artistId
                        if (entry.albumId != null) selectedStreamAlbumId = entry.albumId
                        if (entry.playlistId != null) selectedStreamPlaylistId = entry.playlistId
                    }

                    val popStreamScreen: () -> Boolean = {
                        isNavigatingBack = true
                        if (streamNavStack.isNotEmpty()) {
                            streamNavStack.removeAt(streamNavStack.lastIndex)
                        }
                        if (streamNavStack.isNotEmpty()) {
                            val prev = streamNavStack.last()
                            currentSubScreen = prev.screen
                            selectedStreamArtistId = prev.artistId
                            selectedStreamAlbumId = prev.albumId
                            selectedStreamPlaylistId = prev.playlistId
                            true
                        } else {
                            currentSubScreen = null
                            selectedStreamArtistId = null
                            selectedStreamAlbumId = null
                            selectedStreamPlaylistId = null
                            false
                        }
                    }

                    LaunchedEffect(currentSubScreen) {
                        if (currentSubScreen == null) {
                            streamNavStack.clear()
                            selectedStreamArtistId = null
                            selectedStreamAlbumId = null
                            selectedStreamPlaylistId = null
                        }
                    }

                    var showYouTubeLogin by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                    val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
                    val playlists by playerViewModel.playlists.collectAsState(initial = emptyList())
                    val currentAudio by playerViewModel.currentAudio.collectAsState()
                    val isHomeSelectionMode by homeViewModel.isSelectionMode.collectAsState()
                    val selectedFolders by homeViewModel.selectedFolderPaths.collectAsState()
                    val selectedAudios by homeViewModel.selectedAudioPaths.collectAsState()

                    val context = androidx.compose.ui.platform.LocalContext.current
                    var backPressedTime by remember { mutableStateOf(0L) }

                    // Re-escanear la biblioteca cuando una descarga NUEVA termina con éxito,
                    // evitando rescaneos masivos en el arranque inicial.
                    androidx.compose.runtime.DisposableEffect(homeViewModel, context) {
                        val seenSucceeded = HashSet<String>()
                        var isInitialLoad = true
                        val observer = androidx.lifecycle.Observer<MutableList<androidx.work.WorkInfo>> { infos ->
                            val succeeded = infos.filter { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
                            if (isInitialLoad) {
                                succeeded.forEach { seenSucceeded.add(it.id.toString()) }
                                isInitialLoad = false
                                return@Observer
                            }
                            var hasNew = false
                            succeeded.forEach { info ->
                                if (seenSucceeded.add(info.id.toString())) {
                                    hasNew = true
                                }
                            }
                            if (hasNew) {
                                homeViewModel.rescan()
                            }
                        }
                        val liveData = androidx.work.WorkManager.getInstance(context)
                            .getWorkInfosByTagLiveData("download")
                        liveData.observeForever(observer)
                        onDispose { liveData.removeObserver(observer) }
                    }

                androidx.activity.compose.BackHandler(enabled = true) {
                    if (showPlayerScreen) {
                        showPlayerScreen = false
                    } else if (currentSubScreen == "playlist_detail") {
                        isNavigatingBack = true
                        currentSubScreen = "listas"
                    } else if (currentSubScreen?.startsWith("stream_") == true) {
                        if (!popStreamScreen()) {
                            currentSubScreen = null
                        }
                    } else if (currentSubScreen == "online_playlist_detail") {
                        isNavigatingBack = true
                        currentSubScreen = "listas"
                        selectedStreamPlaylistId = null
                    } else if (showYouTubeLogin) {
                        showYouTubeLogin = false
                    } else if (currentSubScreen != null) {
                        isNavigatingBack = true
                        currentSubScreen = null
                    } else if (currentTab == "stream" && (streamViewModel.searchResults.value != null || streamViewModel.isSearching.value)) {
                        streamViewModel.clearSearch()
                    } else if (currentTab == "inicio" &&
                        homeViewModel.currentNode.value != null &&
                        homeViewModel.currentNode.value?.path != "/") {
                        // Estamos en Home dentro de una carpeta: atrás sube de carpeta.
                        // (Un único BackHandler central evita la competencia entre
                        //  el de MainActivity y el de HomeScreen.)
                        homeViewModel.navigateUp()
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

                    // YouTube Login Screen
                    if (showYouTubeLogin) {
                        YouTubeLoginScreen(
                            onBack = { showYouTubeLogin = false },
                            onLoginSuccess = { showYouTubeLogin = false }
                        )
                    } else {
                    Scaffold(
                        // Con edge-to-edge activo, el fondo (color o imagen) se dibuja
                        // bajo las barras del sistema. Las pantallas ya traen su propio
                        // margen superior (48dp >= altura de la status bar), así que
                        // desactivamos el inset por defecto del Scaffold para no duplicar.
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                // La barra inferior se oculta/muestra con animación
                                // real según haya subpantalla abierta o estemos en modo selección múltiple
                                AnimatedVisibility(
                                    visible = currentSubScreen == null && !isHomeSelectionMode,
                                    enter = fadeIn(tween(AppAnimations.DURATION_FAST)) +
                                            expandVertically(tween(AppAnimations.DURATION_MEDIUM, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(tween(150)) +
                                            shrinkVertically(tween(AppAnimations.DURATION_FAST, easing = FastOutSlowInEasing))
                                ) {
                                    BottomNavBar(
                                        currentTab = currentTab,
                                        onTabSelected = {
                                            homeViewModel.exitSelectionMode()
                                            currentTab = it
                                        }
                                    )
                                }

                                // En modo selección múltiple, esta barra toma el lugar exacto de la barra de navegación
                                AnimatedVisibility(
                                    visible = currentSubScreen == null && isHomeSelectionMode,
                                    enter = fadeIn(tween(AppAnimations.DURATION_FAST)) +
                                            expandVertically(tween(AppAnimations.DURATION_MEDIUM, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(tween(150)) +
                                            shrinkVertically(tween(AppAnimations.DURATION_FAST, easing = FastOutSlowInEasing))
                                ) {
                                    val count = selectedFolders.size + selectedAudios.size
                                    HomeSelectionBottomBar(
                                        selectedCount = count,
                                        onCancel = {
                                            homeViewModel.exitSelectionMode()
                                        },
                                        onHideSelected = {
                                            homeViewModel.hideSelected()
                                        },
                                        onDeleteSelected = {
                                            homeViewModel.requestDeleteMultiple()
                                        }
                                    )
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
                            // ── Animación de subpantallas (Más → Favoritos, Ecualizador…) ──
                            // Slide lateral + escala de profundidad: la pantalla entra
                            // "creciendo" desde 0.97 y al volver se aleja suavemente.
                            AnimatedContent(
                                targetState = currentSubScreen,
                                transitionSpec = {
                                    val slideSpec = AppAnimations.screenSlideOffsetTween()
                                    val scaleSpec = tween<Float>(
                                        AppAnimations.DURATION_MEDIUM,
                                        easing = FastOutSlowInEasing
                                    )
                                    if (!isNavigatingBack && targetState != null) {
                                        // Entrando a una subpantalla: crece desde la derecha
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> (fullWidth * 0.45f).toInt() },
                                            animationSpec = slideSpec
                                        ) +
                                            scaleIn(initialScale = 0.97f, animationSpec = scaleSpec) +
                                            fadeIn(AppAnimations.quickFadeTween()))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> -(fullWidth * 0.18f).toInt() },
                                                    animationSpec = slideSpec
                                                ) +
                                                    scaleOut(targetScale = 0.96f, animationSpec = scaleSpec) +
                                                    fadeOut(tween(200, easing = FastOutSlowInEasing))
                                            )
                                    } else {
                                        // Volviendo hacia atrás: entra desde la izquierda y sale hacia la derecha
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> -(fullWidth * 0.45f).toInt() },
                                            animationSpec = slideSpec
                                        ) +
                                            scaleIn(initialScale = 0.97f, animationSpec = scaleSpec) +
                                            fadeIn(AppAnimations.quickFadeTween()))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> (fullWidth * 0.18f).toInt() },
                                                    animationSpec = slideSpec
                                                ) +
                                                    scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                                    fadeOut(tween(200, easing = FastOutSlowInEasing))
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
                                            onlineLibraryViewModel = onlineLibraryViewModel,
                                            streamViewModel = streamViewModel,
                                            onBack = { currentSubScreen = null }
                                        )
                                        "listas" -> PlaylistsScreen(
                                            playerViewModel = playerViewModel,
                                            onlineLibraryViewModel = onlineLibraryViewModel,
                                            onNavigateToOnlinePlaylist = { playlistId ->
                                                selectedStreamPlaylistId = playlistId
                                                currentSubScreen = "online_playlist_detail"
                                            },
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
                                        "gestor_descargas" -> DownloadsManagerScreen(onBack = { currentSubScreen = null })
                                        "ocultos" -> HiddenItemsScreen(homeViewModel = homeViewModel, onBack = { currentSubScreen = null })
                                        "stream_artist_detail" -> {
                                            selectedStreamArtistId?.let { id ->
                                                StreamArtistDetailScreen(
                                                    artistId = id,
                                                    streamViewModel = streamViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onNavigateToAlbum = { albumId ->
                                                        pushStreamScreen(StreamNavEntry(screen = "stream_album_detail", albumId = albumId))
                                                    },
                                                    onNavigateToArtist = { relatedArtistId ->
                                                        pushStreamScreen(StreamNavEntry(screen = "stream_artist_detail", artistId = relatedArtistId))
                                                    },
                                                    onBack = {
                                                        popStreamScreen()
                                                    }
                                                )
                                            }
                                        }
                                        "stream_album_detail" -> {
                                            selectedStreamAlbumId?.let { albumId ->
                                                StreamAlbumDetailScreen(
                                                    albumId = albumId,
                                                    streamViewModel = streamViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onBack = {
                                                        popStreamScreen()
                                                    }
                                                )
                                            }
                                        }
                                        "stream_playlists" -> {
                                            StreamPlaylistsScreen(
                                                streamViewModel = streamViewModel,
                                                onPlaylistClick = { playlistId ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_playlist_detail", playlistId = playlistId))
                                                }
                                            )
                                        }
                                        "stream_playlist_detail" -> {
                                            selectedStreamPlaylistId?.let { playlistId ->
                                                YouTubePlaylistDetailScreen(
                                                    playlistId = playlistId,
                                                    streamViewModel = streamViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onBack = {
                                                        popStreamScreen()
                                                    }
                                                )
                                            }
                                        }
                                        "online_playlist_detail" -> {
                                            selectedStreamPlaylistId?.let { pid ->
                                                OnlinePlaylistDetailScreen(
                                                    playlistId = pid,
                                                    onlineLibraryViewModel = onlineLibraryViewModel,
                                                    streamViewModel = streamViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onBack = {
                                                        isNavigatingBack = true
                                                        currentSubScreen = "listas"
                                                        selectedStreamPlaylistId = null
                                                    }
                                                )
                                            }
                                        }
                                        "stream_favorite_artists" -> {
                                            StreamFavoriteArtistsScreen(
                                                onArtistClick = { id ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_artist_detail", artistId = id))
                                                },
                                                onBack = { popStreamScreen() }
                                            )
                                        }
                                        "stream_all_artists" -> {
                                            StreamAllArtistsScreen(
                                                streamViewModel = streamViewModel,
                                                onArtistClick = { id ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_artist_detail", artistId = id))
                                                },
                                                onBack = { popStreamScreen() }
                                            )
                                        }
                                        else -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Pantalla en construcción", color = appColors.textPrimary)
                                            }
                                        }
                                    }
                                } else {
                                    // ── Transición entre tabs principal ──
                                    // Desliza según la dirección real del cambio
                                    // (inicio→mas desliza a la izquierda, y viceversa)
                                    // + escala sutil de profundidad.
                                    val tabOrder = remember {
                                        listOf("inicio", "biblioteca", "buscar", "stream", "mas")
                                    }
                                    AnimatedContent(
                                        targetState = currentTab,
                                        transitionSpec = {
                                            val from = tabOrder.indexOf(initialState).let { if (it < 0) 0 else it }
                                            val to = tabOrder.indexOf(targetState).let { if (it < 0) 0 else it }
                                            val forward = to >= from
                                            val slideSpec = AppAnimations.screenSlideOffsetTween()
                                            val scaleSpec = tween<Float>(
                                                AppAnimations.DURATION_MEDIUM,
                                                easing = FastOutSlowInEasing
                                            )
                                            (
                                                slideInHorizontally(
                                                    initialOffsetX = { w -> (if (forward) w else -w) / 5 },
                                                    animationSpec = slideSpec
                                                ) +
                                                    scaleIn(initialScale = 0.94f, animationSpec = scaleSpec) +
                                                    fadeIn(AppAnimations.quickFadeTween())
                                                ).togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { w -> (if (forward) -w else w) / 7 },
                                                    animationSpec = slideSpec
                                                ) +
                                                    scaleOut(targetScale = 0.96f, animationSpec = scaleSpec) +
                                                    fadeOut(tween(150, easing = FastOutSlowInEasing))
                                            )
                                        },
                                        label = "TabAnimation"
                                    ) { tab ->
                                        when (tab) {
                                            "inicio" -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel, isPlayerOpen = showPlayerScreen || currentAudio != null)
                                            "biblioteca" -> LibraryScreen(
                                                homeViewModel = homeViewModel,
                                                playerViewModel = playerViewModel
                                            )
                                            "buscar" -> SearchScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                            "stream" -> StreamScreen(
                                                streamViewModel = streamViewModel,
                                                playerViewModel = playerViewModel,
                                                onNavigateToArtist = { id ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_artist_detail", artistId = id))
                                                },
                                                onNavigateToAlbum = { albumId ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_album_detail", albumId = albumId))
                                                },
                                                onNavigateToLogin = {
                                                    showYouTubeLogin = true
                                                },
                                                onNavigateToPlaylists = {
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_playlists"))
                                                },
                                                onNavigateToPlaylistDetail = { playlistId ->
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_playlist_detail", playlistId = playlistId))
                                                },
                                                onNavigateToFavoriteArtists = {
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_favorite_artists"))
                                                },
                                                onNavigateToAllArtists = {
                                                    pushStreamScreen(StreamNavEntry(screen = "stream_all_artists"))
                                                }
                                            )
                                            "mas" -> MoreScreen(
                                                favoritesCount = favorites.size,
                                                playlistsCount = playlists.size,
                                                onNavigateToFavorites = { isNavigatingBack = false; currentSubScreen = "favoritos" },
                                                onNavigateToPlaylists = { isNavigatingBack = false; currentSubScreen = "listas" },
                                                onNavigateToEqualizer = { isNavigatingBack = false; currentSubScreen = "ecualizador" },
                                                onNavigateToAppearance = { isNavigatingBack = false; currentSubScreen = "apariencia" },
                                                onNavigateToDonations = { isNavigatingBack = false; currentSubScreen = "donaciones" },
                                                onNavigateToDownloadsManager = { isNavigatingBack = false; currentSubScreen = "gestor_descargas" },
                                                onNavigateToHidden = { isNavigatingBack = false; currentSubScreen = "ocultos" }
                                            )
                                            else -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel, isPlayerOpen = showPlayerScreen || currentAudio != null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // End if(showOnboarding) else

                    // Player Overlay — sube con spring físico y se va con easing suave
                    AnimatedVisibility(
                        visible = showPlayerScreen,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(AppAnimations.fadeTween()),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight },
                            animationSpec = AppAnimations.playerSlideTween()
                        ) + fadeOut(tween(250, easing = FastOutSlowInEasing))
                    ) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            streamViewModel = streamViewModel,
                            onlineLibraryViewModel = onlineLibraryViewModel,
                            onNavigateToArtist = { artistId ->
                                showPlayerScreen = false
                                pushStreamScreen(StreamNavEntry(screen = "stream_artist_detail", artistId = artistId))
                            },
                            onClose = { showPlayerScreen = false }
                        )
                    }
                    } // else (not showing YouTube Login)
                }
            }
        }
    }
}
