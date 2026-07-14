# Stream Improvements Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans to implement this plan task-by-task.

**Goal:** Implement 5 improvements to the Stream experience: tutorial, logout modal, recommended content, YouTube playlists, and add-to-playlist.

**Architecture:** Extend existing Stream system with new screens, modals, and YouTube API integration.

**Tech Stack:** Kotlin, Jetpack Compose, Media3, InnerTube API

## Global Constraints

- Min SDK: 26
- Target SDK: 34
- Package: `com.frito.music`
- UI follows Material 3 with `LocalAppColors` theme
- Use existing `YouTubeRepository` pattern for new API calls

---

### Task 1: Stream Tutorial Screen

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamTutorialScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`
- Modify: `app/src/main/java/com/frito/music/data/repository/YouTubeLoginManager.kt`

**Interfaces:**
- Produces: `StreamTutorialScreen` composable with 3 steps
- Produces: `YouTubeLoginManager.hasSeenTutorial()` / `setTutorialSeen()`

- [ ] **Step 1: Add tutorial preference to YouTubeLoginManager**

```kotlin
fun hasSeenTutorial(): Boolean {
    return prefs?.getBoolean("has_seen_stream_tutorial", false) ?: false
}

fun setTutorialSeen() {
    prefs?.edit()?.putBoolean("has_seen_stream_tutorial", true)?.apply()
}
```

- [ ] **Step 2: Create StreamTutorialScreen**

```kotlin
package com.frito.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

data class TutorialStep(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)

@Composable
fun StreamTutorialScreen(
    onFinish: () -> Unit
) {
    val appColors = LocalAppColors.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        TutorialStep(
            icon = Icons.Default.PlayArrow,
            title = "Bienvenido a Stream",
            description = "Escucha tu música favorita directamente desde YouTube Music, sin necesidad de descargarla."
        ),
        TutorialStep(
            icon = Icons.Default.AccountCircle,
            title = "Inicia Sesión",
            description = "Para acceder a todo el contenido, inicia sesión con tu cuenta de Google tocando el ícono de arriba."
        ),
        TutorialStep(
            icon = Icons.Default.CheckCircle,
            title = "¡Listo!",
            description = "Ahora puedes buscar canciones, artistas y reproducir música en streaming. ¡Disfruta!"
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.background)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = steps[currentStep].icon,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = steps[currentStep].title,
                    color = appColors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = steps[currentStep].description,
                    color = appColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Step indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    steps.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentStep) Color(0xFF1DB954)
                                    else appColors.textSecondary.copy(alpha = 0.3f)
                                )
                        )
                        if (index < steps.size - 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onFinish) {
                        Text("Omitir", color = appColors.textSecondary)
                    }

                    Button(
                        onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                onFinish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Text(
                            if (currentStep < steps.size - 1) "Siguiente" else "¡Entendido!",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Add tutorial check in StreamScreen**

Add at the beginning of StreamScreen:
```kotlin
var showTutorial by remember { mutableStateOf(!YouTubeLoginManager.hasSeenTutorial()) }

if (showTutorial) {
    StreamTutorialScreen(
        onFinish = {
            YouTubeLoginManager.setTutorialSeen()
            showTutorial = false
        }
    )
}
```

- [ ] **Step 4: Add login text next to icon in StreamScreen header**

Update the header to show "Iniciar sesión" text:
```kotlin
// Login button with text
val isLoggedIn = YouTubeLoginManager.isLoggedIn()
Row(
    modifier = Modifier
        .clickable { onNavigateToLogin() }
        .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = if (isLoggedIn) "Cuenta" else "Iniciar sesión",
        tint = if (isLoggedIn) Color(0xFF1DB954) else appColors.textSecondary
    )
    if (!isLoggedIn) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Iniciar sesión",
            color = appColors.textSecondary,
            fontSize = 12.sp
        )
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamTutorialScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git add app/src/main/java/com/frito/music/data/repository/YouTubeLoginManager.kt
git commit -m "feat: add stream tutorial for new users"
```

---

### Task 2: Logout Modal

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/YouTubeLogoutModal.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `YouTubeLogoutModal` composable
- Consumes: `YouTubeLoginManager.getAccountName()`, `YouTubeLoginManager.getAccountEmail()`

- [ ] **Step 1: Create YouTubeLogoutModal**

```kotlin
package com.frito.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.data.repository.YouTubeLoginManager
import com.frito.music.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLogoutModal(
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val appColors = LocalAppColors.current
    val accountName = YouTubeLoginManager.getAccountName()
    val accountEmail = YouTubeLoginManager.getAccountEmail()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Account info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = accountName.ifEmpty { "Usuario" },
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (accountEmail.isNotEmpty()) {
                        Text(
                            text = accountEmail,
                            color = appColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout button
            Button(
                onClick = {
                    YouTubeLoginManager.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cerrar Sesión",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cancel button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancelar",
                    color = appColors.textSecondary,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 2: Update StreamScreen to show logout modal**

Add state and modal:
```kotlin
var showLogoutModal by remember { mutableStateOf(false) }

// In the login button click handler:
if (isLoggedIn) {
    showLogoutModal = true
} else {
    onNavigateToLogin()
}

// Add the modal:
if (showLogoutModal) {
    YouTubeLogoutModal(
        onDismiss = { showLogoutModal = false },
        onLogout = {
            showLogoutModal = false
            // Refresh the screen
        }
    )
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/YouTubeLogoutModal.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add logout modal for YouTube account"
```

---

### Task 3: YouTube Home API Methods

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt`

**Interfaces:**
- Produces: `YouTubeRepository.getHome()`, `YouTubeRepository.getExplore()`
- Produces: `StreamViewModel.homePage`, `StreamViewModel.isLoadingHome`

- [ ] **Step 1: Add API methods to YouTubeRepository**

```kotlin
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.ExplorePage

suspend fun getHome(): Result<HomePage> = runCatching {
    YouTube.home().getOrThrow()
}

suspend fun getExplore(): Result<ExplorePage> = runCatching {
    YouTube.explore().getOrThrow()
}
```

- [ ] **Step 2: Add state to StreamViewModel**

```kotlin
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.ExplorePage

private val _homePage = MutableStateFlow<HomePage?>(null)
val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

private val _explorePage = MutableStateFlow<ExplorePage?>(null)
val explorePage: StateFlow<ExplorePage?> = _explorePage.asStateFlow()

private val _isLoadingHome = MutableStateFlow(false)
val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

fun loadHomeContent() {
    viewModelScope.launch {
        _isLoadingHome.value = true

        YouTubeRepository.getHome()
            .onSuccess { _homePage.value = it }
            .onFailure { /* Handle error */ }

        YouTubeRepository.getExplore()
            .onSuccess { _explorePage.value = it }
            .onFailure { /* Handle error */ }

        _isLoadingHome.value = false
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt
git add app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt
git commit -m "feat: add YouTube home and explore API methods"
```

---

### Task 4: Stream Home Screen (Recommended Content)

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `StreamHomeScreen` composable with horizontal sections
- Consumes: `StreamViewModel.homePage`, `StreamViewModel.explorePage`

- [ ] **Step 1: Create StreamHomeScreen**

```kotlin
package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.SongItem
import com.music.innertube.models.AlbumItem

@Composable
fun StreamHomeScreen(
    streamViewModel: StreamViewModel,
    onPlaySong: (SongItem) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val appColors = LocalAppColors.current
    val homePage by streamViewModel.homePage.collectAsState()
    val explorePage by streamViewModel.explorePage.collectAsState()
    val isLoading by streamViewModel.isLoadingHome.collectAsState()

    LaunchedEffect(Unit) {
        streamViewModel.loadHomeContent()
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Tendencias
            homePage?.let { page ->
                page.sections.forEach { section ->
                    if (section.items.isNotEmpty()) {
                        item {
                            Text(
                                text = section.title,
                                color = appColors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(section.items.take(10)) { item ->
                                    when (item) {
                                        is SongItem -> SongCard(
                                            song = item,
                                            onClick = { onPlaySong(item) }
                                        )
                                        is AlbumItem -> AlbumCard(
                                            album = item,
                                            onClick = { onAlbumClick(item.browseId) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Nuevos Lanzamientos
            explorePage?.let { page ->
                // Similar structure for explore content
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
                    model = song.thumbnail,
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
            if (album.thumbnail != null) {
                AsyncImage(
                    model = album.thumbnail,
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
        Text(
            text = album.artists?.joinToString(", ") { it.name } ?: "Álbum",
            color = appColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

- [ ] **Step 2: Update StreamScreen to show home content when logged in**

```kotlin
val isLoggedIn = YouTubeLoginManager.isLoggedIn()

if (isLoggedIn && searchQuery.isEmpty()) {
    StreamHomeScreen(
        streamViewModel = streamViewModel,
        onPlaySong = { song -> streamViewModel.playArtistSong(song, playerViewModel) },
        onAlbumClick = { albumId -> onNavigateToAlbum(albumId) }
    )
} else if (!isLoggedIn && searchQuery.isEmpty()) {
    // Show login prompt
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = appColors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Inicia sesión para ver recomendaciones",
                color = appColors.textSecondary,
                fontSize = 16.sp
            )
        }
    }
} else {
    // Show search results
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add stream home screen with recommended content"
```

---

### Task 5: YouTube Playlists API Methods

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt`

**Interfaces:**
- Produces: `YouTubeRepository.getUserPlaylists()`, `YouTubeRepository.getPlaylistSongs()`, `YouTubeRepository.createYouTubePlaylist()`
- Produces: `StreamViewModel.userPlaylists`, `StreamViewModel.selectedPlaylistSongs`

- [ ] **Step 1: Add playlist API methods to YouTubeRepository**

```kotlin
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.PlaylistPage

suspend fun getUserPlaylists(): Result<List<PlaylistItem>> = runCatching {
    val result = YouTube.library("FEmusic_liked_playlists").getOrThrow()
    result.items.filterIsInstance<PlaylistItem>()
}

suspend fun getPlaylistSongs(playlistId: String): Result<PlaylistPage> = runCatching {
    YouTube.playlist(playlistId).getOrThrow()
}

suspend fun createYouTubePlaylist(title: String): Result<String> = runCatching {
    YouTube.createPlaylist(title) ?: throw Exception("Failed to create playlist")
}

suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit> = runCatching {
    YouTube.addToPlaylist(playlistId, videoId)
}
```

- [ ] **Step 2: Add state to StreamViewModel**

```kotlin
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.PlaylistPage

private val _userPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
val userPlaylists: StateFlow<List<PlaylistItem>> = _userPlaylists.asStateFlow()

private val _selectedPlaylistSongs = MutableStateFlow<PlaylistPage?>(null)
val selectedPlaylistSongs: StateFlow<PlaylistPage?> = _selectedPlaylistSongs.asStateFlow()

private val _isLoadingPlaylists = MutableStateFlow(false)
val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

fun loadUserPlaylists() {
    viewModelScope.launch {
        _isLoadingPlaylists.value = true

        YouTubeRepository.getUserPlaylists()
            .onSuccess { _userPlaylists.value = it }
            .onFailure { _errorMessage.value = it.message }

        _isLoadingPlaylists.value = false
    }
}

fun loadPlaylistSongs(playlistId: String) {
    viewModelScope.launch {
        _isLoadingPlaylists.value = true

        YouTubeRepository.getPlaylistSongs(playlistId)
            .onSuccess { _selectedPlaylistSongs.value = it }
            .onFailure { _errorMessage.value = it.message }

        _isLoadingPlaylists.value = false
    }
}

fun createYouTubePlaylist(title: String) {
    viewModelScope.launch {
        YouTubeRepository.createYouTubePlaylist(title)
            .onSuccess {
                loadUserPlaylists() // Refresh list
            }
            .onFailure { _errorMessage.value = it.message }
    }
}

fun addToYouTubePlaylist(playlistId: String, videoId: String) {
    viewModelScope.launch {
        YouTubeRepository.addToPlaylist(playlistId, videoId)
            .onSuccess { /* Show success */ }
            .onFailure { _errorMessage.value = it.message }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt
git add app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt
git commit -m "feat: add YouTube playlists API methods"
```

---

### Task 6: Stream Playlists Section

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamPlaylistsScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `StreamPlaylistsScreen` composable
- Consumes: `StreamViewModel.userPlaylists`

- [ ] **Step 1: Create StreamPlaylistsScreen**

```kotlin
package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.PlaylistItem

@Composable
fun StreamPlaylistsScreen(
    streamViewModel: StreamViewModel,
    onPlaylistClick: (String) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val appColors = LocalAppColors.current
    val playlists by streamViewModel.userPlaylists.collectAsState()
    val isLoading by streamViewModel.isLoadingPlaylists.collectAsState()

    LaunchedEffect(Unit) {
        streamViewModel.loadUserPlaylists()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mis Listas de YouTube",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCreatePlaylist) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Crear lista",
                    tint = Color(0xFF1DB954)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        } else if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tienes listas de reproducción",
                    color = appColors.textSecondary
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(playlist: PlaylistItem, onClick: () -> Unit) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (playlist.thumbnail != null) {
                AsyncImage(
                    model = playlist.thumbnail,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.songCountText ?: "",
                color = appColors.textSecondary,
                fontSize = 14.sp
            )
        }
    }
}
```

- [ ] **Step 2: Add playlists button to StreamScreen header**

```kotlin
// In the header row, add a "Listas" button
if (isLoggedIn) {
    TextButton(onClick = { showPlaylists = true }) {
        Text("Listas", color = Color(0xFF1DB954))
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamPlaylistsScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add YouTube playlists section to stream"
```

---

### Task 7: Add to YouTube Playlist Modal

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/AddToYouTubePlaylistModal.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt`

**Interfaces:**
- Produces: `AddToYouTubePlaylistModal` composable
- Consumes: `StreamViewModel.userPlaylists`, `StreamViewModel.addToYouTubePlaylist()`

- [ ] **Step 1: Create AddToYouTubePlaylistModal**

```kotlin
package com.frito.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.PlaylistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToYouTubePlaylistModal(
    videoId: String,
    streamViewModel: StreamViewModel,
    onDismiss: () -> Unit,
    onPlaylistCreated: () -> Unit
) {
    val appColors = LocalAppColors.current
    val playlists by streamViewModel.userPlaylists.collectAsState()
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        streamViewModel.loadUserPlaylists()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Agregar a playlist",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Create new playlist button
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF1DB954)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear nueva playlist", color = Color(0xFF1DB954))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlists list
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylistId = playlist.id }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedPlaylistId == playlist.id,
                            onCheckedChange = { selectedPlaylistId = playlist.id },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF1DB954)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playlist.title,
                            color = appColors.textPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add button
            Button(
                onClick = {
                    selectedPlaylistId?.let { playlistId ->
                        streamViewModel.addToYouTubePlaylist(playlistId, videoId)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPlaylistId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Agregar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Create playlist dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                streamViewModel.createYouTubePlaylist(title)
                showCreateDialog = false
                onPlaylistCreated()
            }
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val appColors = LocalAppColors.current
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.background,
        title = {
            Text("Nueva Playlist", color = appColors.textPrimary)
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre", color = appColors.textSecondary) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title) },
                enabled = title.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = appColors.textSecondary)
            }
        }
    )
}
```

- [ ] **Step 2: Add "+" button to PlayerScreen**

In PlayerScreen, add a button that shows the modal:
```kotlin
var showAddToPlaylist by remember { mutableStateOf(false) }

// Add button in the controls area
IconButton(onClick = { showAddToPlaylist = true }) {
    Icon(
        imageVector = Icons.Default.PlaylistAdd,
        contentDescription = "Agregar a playlist",
        tint = appColors.textSecondary
    )
}

// Show modal
if (showAddToPlaylist) {
    currentAudio?.let { audio ->
        AddToYouTubePlaylistModal(
            videoId = audio.path.substringAfterLast("/"), // Extract video ID
            streamViewModel = streamViewModel,
            onDismiss = { showAddToPlaylist = false },
            onPlaylistCreated = { showAddToPlaylist = false }
        )
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/AddToYouTubePlaylistModal.kt
git add app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt
git commit -m "feat: add YouTube playlist modal to player screen"
```

---

### Task 8: Final Integration and Testing

**Files:**
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`

**Steps:**
- [ ] **Step 1: Add navigation for new screens in MainActivity**
- [ ] **Step 2: Wire all components together**
- [ ] **Step 3: Verify full build**

Run: `./gradlew assembleDebug`

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "feat: complete stream improvements integration"
```
