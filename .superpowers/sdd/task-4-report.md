# Task 4 Report: PlaylistsScreen con pestañas Offline | Online

## Fix Report: BackHandler indentation in MainActivity.kt

- **What changed:** Indentation ONLY, on the BackHandler region (lines 182-188 of `MainActivity.kt`). The `stream_playlist_detail` / `online_playlist_detail` / `stream_playlists` `else if` headers were re-indented to 20 spaces (previously 0 / 36) and their statement bodies to 24 spaces (previously 40), matching the file's established convention seen in sibling branches (`stream_artist_detail`, `stream_album_detail`, `playlist_detail`). No logic, conditions, string values, or branch order were touched.
- **Verification output:**
  - Brace/paren/bracket balance BEFORE: `{`/`}` 140/140, `(`/`)` 223/223, `[`/`]` 0/0.
  - Brace/paren/bracket balance AFTER: `{`/`}` 140/140, `(`/`)` 223/223, `[`/`]` 0/0. Identical.
  - Trimmed-content comparison of the 7 edited lines vs pre-edit state: `content identical to pre-edit (trimmed): True`.
  - Leading-space check after fix: L182=20, L183=24, L184=24, L185=20, L186=24, L187=24, L188=20 (headers 20, bodies 24) — consistent with sibling branches.
- **Confirmation:** No logic changed; only leading whitespace on those lines was altered.

## Status
COMPLETE — implementation present in working tree and verified (no compile; per constraints).

## Files modified
- `app/src/main/java/com/frito/music/ui/screens/PlaylistsScreen.kt`
- `app/src/main/java/com/frito/music/MainActivity.kt`

## What was done

### PlaylistsScreen.kt
- Changed signature to:
  `PlaylistsScreen(playerViewModel, onlineLibraryViewModel, onNavigateToOnlinePlaylist, onBack, onPlaylistClick)`.
- Added `val scope = rememberCoroutineScope()` and the TabRow + HorizontalPager block mirroring `FavoritesScreen.kt` exactly (2 tabs "Offline"|"Online", SecondaryIndicator with `tabIndicatorOffset`, `animateScrollToPage`).
- Page 0 -> `PlaylistsOfflineContent` (extracted from original body).
- Page 1 -> `PlaylistsOnlineContent`.
- `PlaylistsOfflineContent`: preserved all original offline content — gradient (moved to parent Column), back button (moved to parent), header (icon, "Listas de Reproducción", count, "Crear lista" button), create-local-list AlertDialog, empty state, and the offline LazyColumn list. `playerViewModel.playlists`, `createPlaylist`.
- `PlaylistsOnlineContent`: uses `OnlineLibraryViewModel.onlinePlaylists/isLoadingPlaylists/onlineError`, `loadOnlinePlaylists()`, `createOnlinePlaylist()`. Login guard via `YouTubeLoginManager.isLoggedIn()`, loading spinner, empty state, online create dialog, and clickable playlist rows navigating via `onNavigateToOnlinePlaylist(pl.id)`. Uses `pl.title` and `pl.songCountText`.

### MainActivity.kt
- Route `"listas"` updated to new `PlaylistsScreen` signature; `onNavigateToOnlinePlaylist` sets `selectedStreamPlaylistId` and routes to `"online_playlist_detail"`.
- Added `"online_playlist_detail"` route using `OnlinePlaylistDetailScreen` (created in Task 5; expected not to exist yet) with `onlineLibraryViewModel`, `streamViewModel`, `playerViewModel`, and onBack -> `"listas"` + clears `selectedStreamPlaylistId`.
- BackHandler chain: added branch for `"online_playlist_detail"` returning to `"listas"` and clearing `selectedStreamPlaylistId` (mirrors `"stream_playlist_detail"` pattern).

## Verification (no compile)
- Brace/paren/bracket balance (regex count):
  - `PlaylistsScreen.kt`: braces 88/88, parens 222/222, brackets 1/1. Balanced.
  - `MainActivity.kt`: braces 140/140, parens 223/223, brackets 0/0. Balanced.
- Read `PlaylistsScreen.kt` fully: TabRow 2 tabs + HorizontalPager 2 pages present; offline content fully preserved (gradient, back, header, create-local-list dialog + button, offline list, empty state); online content uses `OnlineLibraryViewModel`.
- Grep `OnlineLibraryViewModel.kt`: `onlinePlaylists` (StateFlow<List<PlaylistItem>>), `isLoadingPlaylists`, `onlineError`, `loadOnlinePlaylists()`, `createOnlinePlaylist(title)`, `deleteOnlinePlaylist`, `loadPlaylistSongs`, `clearPlaylistSongs` all exist.
- `com.music.innertube.models.PlaylistItem` has `.title`, `.songCountText`, `.id` (confirmed in `innertube/.../models/YTItem.kt:62`).
- `onlineLibraryViewModel` declared in MainActivity (line 103, from Task 3); `OnlineLibraryViewModel` imported (line 51); `com.frito.music.ui.screens.*` import (line 45) covers `OnlinePlaylistDetailScreen`.
- No gradlew run (per constraints). `OnlinePlaylistDetailScreen` reference will not resolve until Task 5 — expected.

## Self-review
- Restructure faithfully mirrors `FavoritesScreen.kt` (the reference pattern), including the gradient, back button, and pager/tab indicator styling.
- No offline behavior dropped: gradient, back, header (title + count + create button), create dialog, list, and empty state all retained in `PlaylistsOfflineContent`.
- Reuses existing `selectedStreamPlaylistId` state var for the new route (as required).
- No new dependencies; uses HorizontalPager already in Compose BOM.

## Concerns
- `OnlinePlaylistDetailScreen` does not exist yet (Task 5) — the `"online_playlist_detail"` route in MainActivity will not compile until Task 5 is done. Expected per brief.
- No compile was run (per global constraints); structural verification only.
