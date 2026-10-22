# Port Flutter Features to Kotlin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the 4 requested features from the Flutter version into the native Kotlin Jetpack Compose app: (1) Multi-Platform Playlist Migrator, (2) Featured Playlists Tab in StreamPlaylistsScreen, (3) In-App Subscribe/Unsubscribe to Artist Channels, and (4) "Artistas que Sigues" YouTube Subscriptions Screen.

**Architecture:** 
- Business logic for web extraction and InnerTube calls sits in dedicated repositories/services (`PlaylistMigratorService`, `YouTubeRepository`, `InnerTube`).
- State flows live in ViewModels (`OnlineLibraryViewModel`, `StreamViewModel`).
- Modern Compose UI matches the established glassmorphic, fluid, emerald theme tokens (`AppAnimations`, `Glassmorphism`, `rememberAppHaptics`).
- Strictly zero changes or regressions to the Media3 playback engine, audio service, or local scanner.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/StateFlow, OkHttp, InnerTube (Kotlin module), Coil.

## Global Constraints
- **ABSOLUTELY NO GIT COMMANDS:** Never run `git add`, `git commit`, `git push`, or any other git operations.
- **NO GRADLE BUILDS:** Never run `./gradlew` builds.
- Preserve 100% of existing functionality, audio caching, and download worker logic.

---

### Task 1: Multi-Platform Playlist Migrator (`PlaylistMigratorService` & `PlaylistMigrateScreen`)

**Files:**
- Create: `app/src/main/java/com/frito/music/services/PlaylistMigratorService.kt`
- Create: `app/src/main/java/com/frito/music/ui/screens/PlaylistMigrateScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/MoreScreen.kt`
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`

- [x] **Step 1:** Create `PlaylistMigratorService.kt` with URL detection (Spotify, Deezer, Apple Music, YouTube Music, SoundCloud, Tidal), metadata extraction (HTML embed parser & Deezer API parser), background resolution of tracks via `YouTube.search(query, FILTER_SONG)`, and playlist creation on YouTube Music (`YouTube.createPlaylist` + `YouTube.addToPlaylist`).
- [x] **Step 2:** Create `PlaylistMigrateScreen.kt` with glassmorphic cards, clipboard paste button, platform badges, track list preview, real-time migration progress bar ("Buscando canción X de Y..."), and success confirmation.
- [x] **Step 3:** Add "Migrar Playlist" item in `MoreScreen.kt` (`Icons.Rounded.SwapHoriz`, emerald `#1DB954`).
- [x] **Step 4:** Wire `playlist_migrate` subscreen in `MainActivity.kt`.

---

### Task 2: Featured Playlists Tab in `StreamPlaylistsScreen`

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamPlaylistsScreen.kt`

- [x] **Step 1:** Add `getFeaturedPlaylists(): Result<List<PlaylistItem>>` in `YouTubeRepository.kt` querying popular themes (Top 50, Éxitos, Pop, Rock).
- [x] **Step 2:** Add `featuredPlaylists`, `isLoadingFeatured`, `loadFeaturedPlaylists()`, and `deleteUserPlaylist(id)` in `StreamViewModel.kt`.
- [x] **Step 3:** Refactor `StreamPlaylistsScreen.kt` into a modern 2-tab view:
  - Tab 1: **"YT"** (User playlists with delete confirmation dialog).
  - Tab 2: **"Destacadas"** (Featured playlists grid/cards with song count, title, and cover).

---

### Task 3: In-App Subscribe/Unsubscribe to Artist Channels

**Files:**
- Modify: `innertube/src/main/kotlin/com/music/innertube/YouTube.kt`
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/OnlineLibraryViewModel.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamArtistDetailScreen.kt`

- [x] **Step 1:** Expose `subscribeChannel(channelId)` and `unsubscribeChannel(channelId)` in `YouTube.kt` and `YouTubeRepository.kt`.
- [x] **Step 2:** Add `subscribedChannelIds: StateFlow<Set<String>>` and `toggleSubscribe(channelId, artistName)` in `OnlineLibraryViewModel.kt`.
- [x] **Step 3:** In `StreamArtistDetailScreen.kt`, add the interactive **"Suscribirse" / "Suscrito"** button with subscriber count, YouTube red accent styling, haptics, and feedback SnackBars.

---

### Task 4: Followed Artists Screen (`StreamFollowedArtistsScreen`)

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/OnlineLibraryViewModel.kt`
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamFollowedArtistsScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`

- [x] **Step 1:** Add `getSubscribedArtists(): Result<List<ArtistItem>>` in `YouTubeRepository.kt`.
- [x] **Step 2:** Add `subscribedArtists: StateFlow<List<ArtistItem>>` and `loadSubscribedArtists()` in `OnlineLibraryViewModel.kt`.
- [x] **Step 3:** Create `StreamFollowedArtistsScreen.kt` with glassmorphic cards, search filter, pull-to-refresh, empty state, and direct navigation to artist profile.
- [x] **Step 4:** Add "Artistas que sigues" button (`Icons.Rounded.PeopleAlt`) in `StreamScreen.kt` header.
- [x] **Step 5:** Wire `stream_followed_artists` in `MainActivity.kt`.
