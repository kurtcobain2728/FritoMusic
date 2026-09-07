# Biblioteca Online (Favoritos + Playlists YouTube Music) — Plan de Implementación

> **Para agentes:** REQUIRED SUB-SKILL: usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans. Pasos con checkbox (`- [ ]`). No hacer commits git (el usuario no lo ha pedido); checkpoints: `gradlew assembleDebug` + prueba en dispositivo.

**Goal:** Añadir a Favoritos y Listas de Reproducción dos pestañas deslizables (Offline | Online) donde Online muestra la biblioteca real de YouTube Music (liked songs y playlists), con like/playlist CRUD y corazón del player sensible al contexto.

**Architecture:** Nuevo `OnlineLibraryViewModel` que envuelve funciones ya existentes del módulo `innertube` (`YouTube.library/likeVideo/playlist/createPlaylist/addToPlaylist/removeFromPlaylist/deletePlaylist`). Las pantallas `FavoritesScreen` y `PlaylistsScreen` se reestructuran con `HorizontalPager` (Compose) + `TabRow` sincronizado. El ❤️ del `PlayerScreen` decide según `currentAudio.path` (http → online; si no → offline).

**Tech Stack:** Kotlin, Jetpack Compose (`HorizontalPager`, `TabRow`), innertube module (ya tiene toda la API), coroutines/StateFlow, WorkManager no aplica.

## Global Constraints

- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- No commits git salvo petición explícita.
- Reutilizar patrones existentes: `StreamTrackItem` (fila), prompt de login de `StreamScreen`, `playArtistSong(song, playerViewModel, queueSongs)` para reproducir con cola.
- No añadir dependencias nuevas (HorizontalPager ya viene en foundation de la BOM).
- Detección de sesión: `YouTubeLoginManager.isLoggedIn()`.
- videoId desde URL: `YouTubeUrlParser.extractVideoId(url)` (ya existe en innertube).

---

### Task 1: `OnlineLibraryViewModel` — estado y liked songs

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/viewmodels/OnlineLibraryViewModel.kt`
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt` (añadir `getLikedSongs()` y `getLikedPlaylists()`)

**Interfaces:**
- Consumes: `YouTube.library(browseId)` (innertube), `YouTube.likeVideo(videoId, liked)` (innertube), `YouTubeLoginManager.isLoggedIn()`
- Produces:
  - `val likedSongs: StateFlow<List<SongItem>>`
  - `val likedSongIds: StateFlow<Set<String>>`
  - `val isLoadingLiked: StateFlow<Boolean>`
  - `val onlineError: StateFlow<String?>`
  - `fun loadLikedSongs()` — refresh
  - `fun likeSong(videoId: String, liked: Boolean)`

- [ ] **Step 1: Añadir métodos al repositorio** en `YouTubeRepository.kt` (añadir tras `getMusicHistory()`):

```kotlin
    /** Canciones "Me gusta" de la cuenta de YouTube Music. */
    suspend fun getLikedSongs(): Result<List<SongItem>> = runCatching {
        val page = YouTube.library("FEmusic_liked_songs").getOrThrow()
        page.items.filterIsInstance<SongItem>()
    }

    /** Playlists guardadas de la cuenta de YouTube Music. */
    suspend fun getLikedPlaylists(): Result<List<PlaylistItem>> = runCatching {
        val page = YouTube.library("FEmusic_liked_playlists").getOrThrow()
        page.items.filterIsInstance<PlaylistItem>()
    }
```

- [ ] **Step 2: Crear el ViewModel** — `OnlineLibraryViewModel.kt`:

```kotlin
package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineLibraryViewModel : ViewModel() {

    private val _likedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val likedSongs: StateFlow<List<SongItem>> = _likedSongs.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    fun loadLikedSongs() {
        viewModelScope.launch {
            _isLoadingLiked.value = true
            _onlineError.value = null
            val result = withContext(Dispatchers.IO) { YouTubeRepository.getLikedSongs() }
            result
                .onSuccess { songs ->
                    _likedSongs.value = songs
                    _likedSongIds.value = songs.map { it.id }.toSet()
                }
                .onFailure { e ->
                    _onlineError.value = e.message ?: "No se pudieron cargar tus canciones"
                }
            _isLoadingLiked.value = false
        }
    }

    /** Cambio optimista: actualiza localmente y luego llama a la API. */
    fun likeSong(videoId: String, liked: Boolean) {
        val current = _likedSongIds.value.toMutableSet()
        if (liked) current.add(videoId) else current.remove(videoId)
        _likedSongIds.value = current
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.likeVideo(videoId, liked)
            }
            result.onFailure {
                // revertir optimista
                val rev = _likedSongIds.value.toMutableSet()
                if (liked) rev.remove(videoId) else rev.add(videoId)
                _likedSongIds.value = rev
                _onlineError.value = "No se pudo actualizar en YouTube Music"
            }
        }
    }

    fun clearOnlineError() { _onlineError.value = null }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (el ViewModel nuevo compila aunque aún no se use).

---

### Task 2: `OnlineLibraryViewModel` — playlists online

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/OnlineLibraryViewModel.kt`

**Interfaces:**
- Consumes: `YouTubeRepository.getLikedPlaylists()`, `YouTube.playlist(id)`, `YouTube.createPlaylist`, `YouTube.deletePlaylist`, `YouTube.addToPlaylist`, `YouTube.removeFromPlaylist`
- Produces:
  - `val onlinePlaylists: StateFlow<List<PlaylistItem>>`
  - `val playlistSongs: StateFlow<PlaylistPage?>`
  - `val isLoadingPlaylists: StateFlow<Boolean>`
  - `fun loadOnlinePlaylists()`, `fun loadPlaylistSongs(id)`, `fun createOnlinePlaylist(title)`, `fun deleteOnlinePlaylist(id)`, `fun addToOnlinePlaylist(pid, videoId)`, `fun removeFromOnlinePlaylist(pid, videoId, setVideoId)`, `fun clearPlaylistSongs()`

- [ ] **Step 1: Añadir estado de playlists al ViewModel** (tras `clearOnlineError()`):

```kotlin
    private val _onlinePlaylists = MutableStateFlow<List<com.music.innertube.models.PlaylistItem>>(emptyList())
    val onlinePlaylists: StateFlow<List<com.music.innertube.models.PlaylistItem>> = _onlinePlaylists.asStateFlow()

    private val _playlistSongs = MutableStateFlow<com.music.innertube.pages.PlaylistPage?>(null)
    val playlistSongs: StateFlow<com.music.innertube.pages.PlaylistPage?> = _playlistSongs.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    fun loadOnlinePlaylists() {
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            val result = withContext(Dispatchers.IO) { YouTubeRepository.getLikedPlaylists() }
            result.onSuccess { _onlinePlaylists.value = it }
                .onFailure { _onlineError.value = it.message ?: "No se pudieron cargar tus listas" }
            _isLoadingPlaylists.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            _playlistSongs.value = null
            _onlineError.value = null
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getPlaylistSongs(playlistId)
            }
            result.onSuccess { _playlistSongs.value = it }
                .onFailure { _onlineError.value = it.message ?: "No se pudo abrir la lista" }
        }
    }

    fun clearPlaylistSongs() { _playlistSongs.value = null }

    fun createOnlinePlaylist(title: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.createYouTubePlaylist(title)
            }
            result.onSuccess { loadOnlinePlaylists() }
                .onFailure { _onlineError.value = it.message ?: "No se pudo crear la lista" }
        }
    }

    fun deleteOnlinePlaylist(playlistId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.deletePlaylist(playlistId)
            }
            result.onSuccess { loadOnlinePlaylists() }
                .onFailure { _onlineError.value = it.message ?: "No se pudo borrar la lista" }
        }
    }

    fun addToOnlinePlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.addToPlaylist(playlistId, videoId)
            }
            result.onFailure { _onlineError.value = it.message ?: "No se pudo añadir la canción" }
        }
    }

    fun removeFromOnlinePlaylist(playlistId: String, videoId: String, setVideoId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.removeFromPlaylist(playlistId, videoId, setVideoId)
            }
            result.onSuccess { loadPlaylistSongs(playlistId) }
                .onFailure { _onlineError.value = it.message ?: "No se pudo quitar la canción" }
        }
    }
```

- [ ] **Step 2: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 3: FavoritesScreen con pestañas Offline | Online

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/FavoritesScreen.kt` (envolver el contenido actual en pestañas)
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt` (pasar `onlineLibraryViewModel`, `streamViewModel`)

**Interfaces:**
- Consumes: `OnlineLibraryViewModel.likedSongs/likedSongIds/isLoadingLiked/onlineError/loadLikedSongs/likeSong`, `StreamViewModel.playArtistSong(song, playerViewModel, queueSongs)`
- Produces: `FavoritesScreen(..., onlineLibraryViewModel, streamViewModel)` con pestaña Online funcional

- [ ] **Step 1: Reestructurar `FavoritesScreen`** — envolver con pager. Cambiar la firma:

```kotlin
@Composable
fun FavoritesScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    streamViewModel: StreamViewModel,
    onBack: () -> Unit
) {
```

Dentro, tras declarar estados, añadir el pager. Reemplazar el `Column` raíz actual (que pinta gradiente + header + lista) por:

```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (appColors.backgroundImageUri != null) {
                        listOf(appColors.accent.copy(alpha = 0.3f), Color.Transparent)
                    } else {
                        listOf(appColors.accent.copy(alpha = 0.22f), appColors.background)
                    },
                    startY = 0f,
                    endY = 800f
                )
            )
    ) {
        // Top Bar / Back (igual que antes)
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
```

Necesita `val scope = rememberCoroutineScope()` y `rememberPagerState`/`HorizontalPager`/`TabRow`/`tabIndicatorOffset`/`TabRowDefaults` (imports de `androidx.compose.foundation.pager.*` y `androidx.compose.material3.*`). FavoritesScreen ya importa material3.Icon/Text sueltos → cambiar a `androidx.compose.material3.*`.

- [ ] **Step 2: Extraer el contenido offline actual a un composable**

Todo el contenido actual (header favoritos + botones shuffle/play + LazyColumn de favoritos) va a `FavoritesOfflineContent`. El código de header y lista se mantiene textualmente igual, solo se mueve al nuevo composable:

```kotlin
@Composable
private fun FavoritesOfflineContent(
    favoriteAudios: List<AudioFile>,
    playerViewModel: PlayerViewModel,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        // (header icono corazón, título "Favoritos", contador, botones shuffle/play: copiar tal cual del FavoritesScreen actual)
        // (LazyColumn de favoritos: copiar tal cual)
    }
}
```

- [ ] **Step 3: Crear `FavoritesOnlineContent`**

```kotlin
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

    LaunchedEffect(Unit) {
        onlineLibraryViewModel.loadLikedSongs()
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
```

- [ ] **Step 4: Crear `OnlineLikedSongRow`** (fila con corazón para unlike)

```kotlin
@Composable
private fun OnlineLikedSongRow(
    song: com.music.innertube.models.SongItem,
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
                coil.compose.AsyncImage(model = song.thumbnail, contentDescription = song.title, modifier = Modifier.fillMaxSize())
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
```

- [ ] **Step 5: Cablear en MainActivity** — la llamada a `FavoritesScreen` (en la ruta `"favoritos"`) pasa a:

```kotlin
"favoritos" -> FavoritesScreen(
    homeViewModel = homeViewModel,
    playerViewModel = playerViewModel,
    onlineLibraryViewModel = onlineLibraryViewModel,
    streamViewModel = streamViewModel,
    onBack = { currentSubScreen = null }
)
```

Y crear/obtener `onlineLibraryViewModel` junto a los demás ViewModels:

```kotlin
val onlineLibraryViewModel: OnlineLibraryViewModel = viewModel()
```

(añadir import `com.frito.music.ui.viewmodels.OnlineLibraryViewModel`)

- [ ] **Step 6: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 4: PlaylistsScreen con pestañas Offline | Online

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlaylistsScreen.kt`
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`

**Interfaces:**
- Consumes: `OnlineLibraryViewModel.onlinePlaylists/isLoadingPlaylists/onlineError/loadOnlinePlaylists/createOnlinePlaylist/deleteOnlinePlaylist/loadPlaylistSongs/clearPlaylistSongs`, `StreamViewModel`
- Produces: `PlaylistsScreen(..., onlineLibraryViewModel, streamViewModel, onNavigateToOnlinePlaylist: (String) -> Unit)`

- [ ] **Step 1: Reestructurar `PlaylistsScreen`** — misma técnica que Favorites (pager + tabs). Cambiar firma:

```kotlin
@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onNavigateToOnlinePlaylist: (String) -> Unit,
    onBack: () -> Unit,
    onPlaylistClick: (com.frito.music.data.models.Playlist) -> Unit
) {
```

Añadir `val scope = rememberCoroutineScope()` y el bloque de pestañas + pager idéntico al de Favorites (página 0 = offline actual, página 1 = `PlaylistsOnlineContent`). El contenido offline actual (header + crear + lista) va al composable `PlaylistsOfflineContent`.

- [ ] **Step 2: Crear `PlaylistsOnlineContent`**

```kotlin
@Composable
private fun PlaylistsOnlineContent(
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onNavigateToOnlinePlaylist: (String) -> Unit,
    appColors: com.frito.music.ui.theme.AppColors
) {
    val playlists by onlineLibraryViewModel.onlinePlaylists.collectAsState()
    val isLoading by onlineLibraryViewModel.isLoadingPlaylists.collectAsState()
    val error by onlineLibraryViewModel.onlineError.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onlineLibraryViewModel.loadOnlinePlaylists()
    }

    when {
        !com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Inicia sesión para ver tus listas de YouTube Music", color = appColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                // Botón crear playlist online
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(appColors.accent)
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Crear", tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear lista online", color = com.frito.music.ui.theme.textColorForBackground(appColors.accent), fontWeight = FontWeight.SemiBold)
                    }
                }

                when {
                    isLoading && playlists.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = appColors.accent) }
                    playlists.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(error ?: "No tienes listas en YouTube Music", color = if (error != null) Color.Red else appColors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(32.dp))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(appColors.surface)
                                    .clickable { onNavigateToOnlinePlaylist(pl.id) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(appColors.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.title, color = appColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pl.songCountText ?: "", color = appColors.textSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nueva lista en YouTube Music", color = appColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre", color = appColors.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = appColors.textPrimary, unfocusedTextColor = appColors.textPrimary, focusedBorderColor = appColors.accent, unfocusedBorderColor = appColors.textSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onlineLibraryViewModel.createOnlinePlaylist(newName.trim())
                    showCreate = false
                    newName = ""
                }) { Text("Guardar", color = appColors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar", color = appColors.textSecondary) } },
            containerColor = appColors.surface
        )
    }
}
```

- [ ] **Step 3: Cablear en MainActivity** — ruta `"listas"`:

```kotlin
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
```

Añadir ruta nueva `"online_playlist_detail"` (BackHandler incluido):

```kotlin
"online_playlist_detail" -> {
    selectedStreamPlaylistId?.let { pid ->
        OnlinePlaylistDetailScreen(
            playlistId = pid,
            onlineLibraryViewModel = onlineLibraryViewModel,
            streamViewModel = streamViewModel,
            playerViewModel = playerViewModel,
            onBack = { currentSubScreen = "listas"; selectedStreamPlaylistId = null }
        )
    }
}
```

- [ ] **Step 4: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (fallará la referencia a `OnlinePlaylistDetailScreen` hasta Task 5; si quieres compilar ya, crea un stub o haz las Tasks 4-5 juntas antes de compilar).

---

### Task 5: Detalle de playlist online + quitar canciones

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/OnlinePlaylistDetailScreen.kt`

**Interfaces:**
- Consumes: `OnlineLibraryViewModel.playlistSongs/onlineError/loadPlaylistSongs/removeFromOnlinePlaylist/clearPlaylistSongs`, `StreamViewModel.playAlbumSong(song, playerViewModel, queueSongs)`
- Produces: pantalla de detalle con canciones, play-all, y quitar canción

- [ ] **Step 1: Crear la pantalla**

```kotlin
package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.music.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistDetailScreen(
    playlistId: String,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val page by onlineLibraryViewModel.playlistSongs.collectAsState()
    val error by onlineLibraryViewModel.onlineError.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistId) {
        onlineLibraryViewModel.clearPlaylistSongs()
        onlineLibraryViewModel.loadPlaylistSongs(playlistId)
    }

    val songs = remember(page) { page?.songs ?: emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "Lista", color = appColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = appColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.background)
            )
        },
        containerColor = appColors.background
    ) { padding ->
        when {
            page == null && error == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
            songs.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Lista vacía", color = if (error != null) Color.Red else appColors.textSecondary, fontSize = 16.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                streamViewModel.playAlbumSong(songs.first(), playerViewModel, queueSongs = songs)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.accent),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent))
                        Spacer(Modifier.width(8.dp))
                        Text("Reproducir todo", color = com.frito.music.ui.theme.textColorForBackground(appColors.accent), fontWeight = FontWeight.Bold)
                    }
                }
                itemsIndexed(songs) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { streamViewModel.playAlbumSong(song, playerViewModel, queueSongs = songs) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text((index + 1).toString(), color = appColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(28.dp))
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.thumbnail.isNotEmpty()) AsyncImage(model = song.thumbnail, contentDescription = song.title, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists.joinToString(", ") { it.name }, color = appColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Quitar de la lista",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(24.dp).clickable {
                                val sid = song.setVideoId
                                if (sid != null) {
                                    onlineLibraryViewModel.removeFromOnlinePlaylist(playlistId, song.id, sid)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 6: ❤️ del reproductor sensible al contexto

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/PlayerViewModel.kt`

**Interfaces:**
- Consumes: `OnlineLibraryViewModel.likedSongIds/likeSong`, `YouTubeUrlParser.extractVideoId(url)`
- Produces: corazón que marca offline (local) u online (like YT) según `currentAudio.path`

- [ ] **Step 1: Exponer el like online en PlayerScreen.** `PlayerScreen` recibe un nuevo parámetro:

```kotlin
fun PlayerScreen(
    viewModel: PlayerViewModel,
    streamViewModel: StreamViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onClose: () -> Unit
) {
```

En el cuerpo, calcular estado del corazón:

```kotlin
    val likedSongIds by onlineLibraryViewModel.likedSongIds.collectAsState()

    val currentPath = currentAudio?.path.orEmpty()
    val isOnline = currentPath.startsWith("http")
    val currentVideoId = remember(currentPath) {
        com.music.innertube.utils.YouTubeUrlParser.extractVideoId(currentPath)
    }
    val isOnlineLiked = currentVideoId != null && likedSongIds.contains(currentVideoId)
```

Y sustituir el Icon de favorito (líneas ~240-247 actuales) por:

```kotlin
                val favIsActive = if (isOnline) isOnlineLiked else isCurrentFavorite
                val favTint = if (favIsActive) Color(0xFFFF6B6B) else appColors.textPrimary
                Icon(
                    imageVector = if (favIsActive) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = favTint,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable {
                            if (isOnline && currentVideoId != null) {
                                onlineLibraryViewModel.likeSong(currentVideoId, !isOnlineLiked)
                            } else {
                                viewModel.toggleFavorite()
                            }
                        }
                )
```

- [ ] **Step 2: Pasar `onlineLibraryViewModel` al PlayerScreen en MainActivity**

```kotlin
PlayerScreen(
    viewModel = playerViewModel,
    streamViewModel = streamViewModel,
    onlineLibraryViewModel = onlineLibraryViewModel,
    onClose = { showPlayerScreen = false }
)
```

- [ ] **Step 3: Verificar compilación**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 7: Refresco y verificación final

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/FavoritesScreen.kt` (opcional: refrescar likes al volver)

**Interfaces:**
- Consumes: nada nuevo

- [ ] **Step 1: Refrescar likes al entrar a la pestaña Online** (ya se hace con `LaunchedEffect(Unit)` en `FavoritesOnlineContent`). Añadir además refresco al `ON_RESUME` para que un like hecho desde el player aparezca al volver:

```kotlin
    // en FavoritesOnlineContent, reemplazar el LaunchedEffect por:
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onlineLibraryViewModel.loadLikedSongs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

- [ ] **Step 2: Verificación completa**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Pruebas en dispositivo:
1. Favoritos: pestañas Offline/Online + swipe en ambas direcciones.
2. Favoritos Online: logueado → aparecen likes; ❤️ en player a canción online → aparece al volver.
3. Canción local → ❤️ offline (pestaña Offline).
4. Playlists Online: crear → aparece; abrir detalle; quitar canción.
5. Sin sesión: pestañas Online muestran prompt de login.
6. Rotación no pierde pestaña (pagerState con rememberSaveable si es necesario).