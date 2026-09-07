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


---
CONSTRAINTS GLOBALES (obligatorio):
- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- NO ejecutar gradlew/assembleDebug: el usuario compila. Verifica estructura (llaves y par�ntesis balanceados) y que las referencias/imports existan.
- NO hacer commits git: el usuario no lo ha pedido.
- Reutilizar patrones existentes (StreamTrackItem, prompt de login de StreamScreen, playArtistSong/playAlbumSong con queueSongs).
- Sin dependencias nuevas. Sesi�n: YouTubeLoginManager.isLoggedIn(). videoId: YouTubeUrlParser.extractVideoId().
- Al terminar: escribir el reporte en el archivo indicado y devolver estado + archivos tocados + verificaci�n.
