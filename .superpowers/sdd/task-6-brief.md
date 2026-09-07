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


---
CONSTRAINTS GLOBALES (obligatorio):
- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- NO ejecutar gradlew/assembleDebug: el usuario compila. Verifica estructura (llaves y par�ntesis balanceados) y que las referencias/imports existan.
- NO hacer commits git: el usuario no lo ha pedido.
- Reutilizar patrones existentes (StreamTrackItem, prompt de login de StreamScreen, playArtistSong/playAlbumSong con queueSongs).
- Sin dependencias nuevas. Sesi�n: YouTubeLoginManager.isLoggedIn(). videoId: YouTubeUrlParser.extractVideoId().
- Al terminar: escribir el reporte en el archivo indicado y devolver estado + archivos tocados + verificaci�n.
