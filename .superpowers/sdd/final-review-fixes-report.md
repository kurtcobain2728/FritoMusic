# Final Review Fixes Report

## Fix 1 — T1: like revert syncs `_likedSongs`

**File:** `app\src\main\java\com\frito\music\ui\viewmodels\OnlineLibraryViewModel.kt` (in `likeSong`)

**Changed:** Added an optimistic removal of the song from `_likedSongs` when unliking, so the liked-songs list stays consistent with the heart state (which is driven by `_likedSongIds`).

```kotlin
fun likeSong(videoId: String, liked: Boolean) {
    val current = _likedSongIds.value.toMutableSet()
    if (liked) current.add(videoId) else current.remove(videoId)
    _likedSongIds.value = current
    // Mantener la lista coherente: si se quita el like, eliminar de likedSongs
    if (!liked) {
        _likedSongs.value = _likedSongs.value.filterNot { it.id == videoId }
    }
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
```

Note: on failure, `_likedSongIds` is reverted but `_likedSongs` is not re-added (song reappears on next `loadLikedSongs`) — kept simple per the fix.

## Fix 2 — T7: gate ON_RESUME observer on login

**File:** `app\src\main\java\com\frito\music\ui\screens\FavoritesScreen.kt` (in `FavoritesOnlineContent`)

**Changed:** Guarded the `DisposableEffect` LifecycleEventObserver's `ON_RESUME` handler so `loadLikedSongs()` only runs when logged in. DisposableEffect keys and `onDispose` left unchanged.

```kotlin
val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
        com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn()
    ) {
        onlineLibraryViewModel.loadLikedSongs()
    }
}
lifecycleOwner.lifecycle.addObserver(observer)
onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
```

## Verification

- Brace/paren balance (both files):
  - `OnlineLibraryViewModel.kt` — braces 44/44, parens 57/57 ✅
  - `FavoritesScreen.kt` — braces 70/70, parens 191/191 ✅
- Read back changed sections in both files: logic correct ✅
- Grep `OnlineLibraryViewModel.kt` for `filterNot` → appears once (line 52, inside `likeSong`) ✅
- Grep `FavoritesScreen.kt` for `isLoggedIn()` inside the observer → present (line 293) ✅
- Confirmed `YouTubeLoginManager.isLoggedIn()` exists (returns `Boolean`) ✅

## Confirmation

Both fixes applied, verified, and syntactically balanced.
