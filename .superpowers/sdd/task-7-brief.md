### Task 7: Add to YouTube Playlist Modal

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/AddToYouTubePlaylistModal.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt`

**Interfaces:**
- Produces: `AddToYouTubePlaylistModal` composable
- Consumes: `StreamViewModel.userPlaylists`, `StreamViewModel.addToYouTubePlaylist()`

**Context:** When user taps "+" on a streaming song, show modal with YouTube playlists to add to.

- [ ] **Step 1: Create AddToYouTubePlaylistModal**

Create with:
- ModalBottomSheet
- "Agregar a playlist" title
- "Crear nueva playlist" button
- LazyColumn with playlists and checkboxes
- "Agregar" button

- [ ] **Step 2: Add "+" button to PlayerScreen**

Add PlaylistAdd icon button that shows the modal.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/AddToYouTubePlaylistModal.kt
git add app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt
git commit -m "feat: add YouTube playlist modal to player screen"
```
