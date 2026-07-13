### Task 8: Final Integration and Testing

**Files:**
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`
- Create: `app/src/main/java/com/frito/music/ui/screens/YouTubePlaylistDetailScreen.kt`

**Steps:**
- [x] **Step 1: Add navigation for new screens in MainActivity**

Add navigation for:
- StreamPlaylistsScreen
- YouTubePlaylistDetailScreen (if created)

- [x] **Step 2: Wire all components together**

Ensure:
- Tutorial shows on first visit
- Logout modal works
- Home content loads when logged in
- Playlists section accessible
- Add to playlist works from PlayerScreen

- [x] **Step 3: Verify full build**

Run: `./gradlew assembleDebug`

- [x] **Step 4: Commit**

```bash
git add .
git commit -m "feat: complete stream improvements integration"
```
