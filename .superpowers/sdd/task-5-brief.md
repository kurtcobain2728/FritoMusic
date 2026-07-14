### Task 5: YouTube Playlists API Methods

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt`

**Interfaces:**
- Produces: `YouTubeRepository.getUserPlaylists()`, `YouTubeRepository.getPlaylistSongs()`, `YouTubeRepository.createYouTubePlaylist()`, `YouTubeRepository.addToPlaylist()`
- Produces: `StreamViewModel.userPlaylists`, `StreamViewModel.selectedPlaylistSongs`

**Context:** This task adds API methods for YouTube playlists: list user playlists, get playlist songs, create playlist, add song to playlist.

- [ ] **Step 1: Add playlist API methods to YouTubeRepository**

Add these methods:
```kotlin
suspend fun getUserPlaylists(): Result<List<PlaylistItem>>
suspend fun getPlaylistSongs(playlistId: String): Result<PlaylistPage>
suspend fun createYouTubePlaylist(title: String): Result<String>
suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit>
```

- [ ] **Step 2: Add state to StreamViewModel**

Add userPlaylists, selectedPlaylistSongs, isLoadingPlaylists states and methods.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt
git add app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt
git commit -m "feat: add YouTube playlists API methods"
```
