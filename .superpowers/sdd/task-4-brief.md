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


---
CONSTRAINTS GLOBALES (obligatorio):
- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- NO ejecutar gradlew/assembleDebug: el usuario compila. Verifica estructura (llaves y par�ntesis balanceados) y que las referencias/imports existan.
- NO hacer commits git: el usuario no lo ha pedido.
- Reutilizar patrones existentes (StreamTrackItem, prompt de login de StreamScreen, playArtistSong/playAlbumSong con queueSongs).
- Sin dependencias nuevas. Sesi�n: YouTubeLoginManager.isLoggedIn(). videoId: YouTubeUrlParser.extractVideoId().
- Al terminar: escribir el reporte en el archivo indicado y devolver estado + archivos tocados + verificaci�n.
