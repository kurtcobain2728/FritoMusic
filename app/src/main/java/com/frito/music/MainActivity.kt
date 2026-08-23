package com.frito.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.frito.music.ui.screens.*
import com.frito.music.ui.theme.FritoMusicTheme
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.DownloadViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.AppAnimations

class MainActivity : ComponentActivity() {

    /**
     * Procesa el deep link spotiflac://session-grant?...&state={extensionId}&grant={grant}
     * que llega tras la verificación de sesión firmada en el navegador.
     */
    private fun handleSessionGrantIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "spotiflac" && uri.host == "session-grant") {
            val extensionId = uri.getQueryParameter("state")
            val grant = uri.getQueryParameter("grant")
            if (!extensionId.isNullOrEmpty() && !grant.isNullOrEmpty()) {
                com.frito.music.extensions.session.SignedSessionManager
                    .setPendingGrant(this, extensionId, grant)
            }
            // Limpiar el data para no reprocesarlo en recreaciones
            intent.data = null
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSessionGrantIntent(intent)
    }

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

        // Deep link de verificación de sesión (si la app se abrió desde el navegador)
        handleSessionGrantIntent(intent)

        setContent {
            var showOnboarding by remember { mutableStateOf(!hasCompletedOnboardingInitial) }
            val themeViewModel: ThemeViewModel = viewModel()
            val homeViewModel: HomeViewModel = viewModel()
            val playerViewModel: PlayerViewModel = viewModel()
            val downloadViewModel: DownloadViewModel = viewModel()
            val streamViewModel: StreamViewModel = viewModel()

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
                        }
                    )
                } else {
                // rememberSaveable: el estado de navegación sobrevive rotaciones
                var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("inicio") }
                var currentSubScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var showPlayerScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) } // no parcelable: se pierde con rotación (aceptable)
                var selectedArtistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var selectedAlbumId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var selectedStreamArtistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var selectedStreamAlbumId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var selectedStreamPlaylistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
                var verificationTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // se reconstruye vía deep link / banner
                var showYouTubeLogin by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
                val playlists by playerViewModel.playlists.collectAsState(initial = emptyList())
                val currentAudio by playerViewModel.currentAudio.collectAsState()

                val context = androidx.compose.ui.platform.LocalContext.current
                var backPressedTime by remember { mutableStateOf(0L) }

                // Re-escanear la biblioteca cuando una descarga termina con éxito,
                // para que la canción nueva aparezca sin reiniciar la app.
                androidx.compose.runtime.DisposableEffect(homeViewModel, context) {
                    val seenSucceeded = HashSet<String>()
                    val observer = object : androidx.lifecycle.Observer<MutableList<androidx.work.WorkInfo>> {
                        override fun onChanged(infos: MutableList<androidx.work.WorkInfo>) {
                            infos.filter { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
                                .forEach { info ->
                                    if (seenSucceeded.add(info.id.toString())) {
                                        homeViewModel.rescan()
                                    }
                                }
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
                        currentSubScreen = "listas"
                        selectedPlaylist = null
                    } else if (currentSubScreen == "artist_detail" || currentSubScreen == "album_detail" || currentSubScreen == "session_verification") {
                        currentSubScreen = "descargar"
                        selectedArtistId = null
                        selectedAlbumId = null
                        verificationTarget = null
                    } else if (currentSubScreen == "stream_artist_detail") {
                        currentSubScreen = null
                        selectedStreamArtistId = null
                    } else if (currentSubScreen == "stream_album_detail") {
                        currentSubScreen = "stream_artist_detail"
                        selectedStreamAlbumId = null
                    } else if (currentSubScreen == "stream_playlist_detail") {
                        currentSubScreen = "stream_playlists"
                        selectedStreamPlaylistId = null
                    } else if (currentSubScreen == "stream_playlists") {
                        currentSubScreen = null
                    } else if (showYouTubeLogin) {
                        showYouTubeLogin = false
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
                                // real según haya subpantalla abierta
                                AnimatedVisibility(
                                    visible = currentSubScreen == null,
                                    enter = fadeIn(tween(AppAnimations.DURATION_FAST)) +
                                            expandVertically(tween(AppAnimations.DURATION_MEDIUM, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(tween(150)) +
                                            shrinkVertically(tween(AppAnimations.DURATION_FAST, easing = FastOutSlowInEasing))
                                ) {
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
                                    if (targetState != null) {
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
                                        // Volviendo a tabs: se aleja hacia la izquierda
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
                                            onNavigateToVerification = { extId, url ->
                                                verificationTarget = extId to url
                                                currentSubScreen = "session_verification"
                                            },
                                            viewModel = downloadViewModel
                                        )
                                        "session_verification" -> {
                                            verificationTarget?.let { (extId, url) ->
                                                com.frito.music.ui.screens.SessionVerificationScreen(
                                                    extensionId = extId,
                                                    authUrl = url,
                                                    onBack = { currentSubScreen = "descargar" },
                                                    onCompleted = {
                                                        currentSubScreen = "descargar"
                                                        downloadViewModel.refreshSessionState()
                                                    }
                                                )
                                            }
                                        }
                                        "gestor_descargas" -> DownloadsManagerScreen(onBack = { currentSubScreen = null })
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
                                        "stream_artist_detail" -> {
                                            selectedStreamArtistId?.let { id ->
                                                StreamArtistDetailScreen(
                                                    artistId = id,
                                                    streamViewModel = streamViewModel,
                                                    playerViewModel = playerViewModel,
                                                    onNavigateToAlbum = { albumId ->
                                                        selectedStreamAlbumId = albumId
                                                        currentSubScreen = "stream_album_detail"
                                                    },
                                                    onBack = {
                                                        currentSubScreen = null
                                                        selectedStreamArtistId = null
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
                                                        currentSubScreen = "stream_artist_detail"
                                                        selectedStreamAlbumId = null
                                                    }
                                                )
                                            }
                                        }
                                        "stream_playlists" -> {
                                            StreamPlaylistsScreen(
                                                streamViewModel = streamViewModel,
                                                onPlaylistClick = { playlistId ->
                                                    selectedStreamPlaylistId = playlistId
                                                    currentSubScreen = "stream_playlist_detail"
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
                                                        currentSubScreen = "stream_playlists"
                                                        selectedStreamPlaylistId = null
                                                    }
                                                )
                                            }
                                        }
                                        "extensiones" -> ExtensionsScreen(onBack = { currentSubScreen = null })
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
                                            "inicio" -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                            "biblioteca" -> LibraryScreen(
                                                homeViewModel = homeViewModel,
                                                playerViewModel = playerViewModel
                                            )
                                            "buscar" -> SearchScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
                                            "stream" -> StreamScreen(
                                                streamViewModel = streamViewModel,
                                                playerViewModel = playerViewModel,
                                                onNavigateToArtist = { id ->
                                                    selectedStreamArtistId = id
                                                    currentSubScreen = "stream_artist_detail"
                                                },
                                                onNavigateToLogin = {
                                                    showYouTubeLogin = true
                                                },
                                                onNavigateToPlaylists = {
                                                    currentSubScreen = "stream_playlists"
                                                }
                                            )
                                            "mas" -> MoreScreen(
                                                favoritesCount = favorites.size,
                                                playlistsCount = playlists.size,
                                                onNavigateToFavorites = { currentSubScreen = "favoritos" },
                                                onNavigateToPlaylists = { currentSubScreen = "listas" },
                                                onNavigateToEqualizer = { currentSubScreen = "ecualizador" },
                                                onNavigateToAppearance = { currentSubScreen = "apariencia" },
                                                onNavigateToDonations = { currentSubScreen = "donaciones" },
                                                onNavigateToDownload = { currentSubScreen = "descargar" },
                                                onNavigateToDownloadsManager = { currentSubScreen = "gestor_descargas" },
                                                onNavigateToExtensions = { currentSubScreen = "extensiones" }
                                            )
                                            else -> HomeScreen(homeViewModel = homeViewModel, playerViewModel = playerViewModel)
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
                            onClose = { showPlayerScreen = false }
                        )
                    }
                    } // else (not showing YouTube Login)
                }
            }
        }
    }
}
