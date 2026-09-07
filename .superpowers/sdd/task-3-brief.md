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


---
CONSTRAINTS GLOBALES (obligatorio):
- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- NO ejecutar gradlew/assembleDebug: el usuario compila. Verifica estructura (llaves y par�ntesis balanceados) y que las referencias/imports existan.
- NO hacer commits git: el usuario no lo ha pedido.
- Reutilizar patrones existentes (StreamTrackItem, prompt de login de StreamScreen, playArtistSong/playAlbumSong con queueSongs).
- Sin dependencias nuevas. Sesi�n: YouTubeLoginManager.isLoggedIn(). videoId: YouTubeUrlParser.extractVideoId().
- Al terminar: escribir el reporte en el archivo indicado y devolver estado + archivos tocados + verificaci�n.
