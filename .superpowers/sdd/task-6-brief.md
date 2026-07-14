### Task 6: Stream Playlists Section

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamPlaylistsScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `StreamPlaylistsScreen` composable
- Consumes: `StreamViewModel.userPlaylists`

**Context:** Add a "Listas" button in Stream header that shows user's YouTube playlists.

- [ ] **Step 1: Create StreamPlaylistsScreen**

Create with:
- Header "Mis Listas de YouTube" with "+" button
- LazyColumn with PlaylistItem composables
- Each item shows thumbnail, name, song count

- [ ] **Step 2: Add playlists button to StreamScreen header**

Add "Listas" button next to title when logged in.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamPlaylistsScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add YouTube playlists section to stream"
```
