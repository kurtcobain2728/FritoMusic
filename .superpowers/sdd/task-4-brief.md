### Task 4: Stream Home Screen

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `StreamHomeScreen` composable with horizontal sections
- Consumes: `StreamViewModel.homePage`, `StreamViewModel.explorePage`

**Context:** When user is logged in, show recommended content (trending, new releases, history) instead of "search" prompt.

- [ ] **Step 1: Create StreamHomeScreen**

Create with horizontal sections:
- Tendencias (from homePage)
- Nuevos Lanzamientos (from explorePage)
- Basado en tu historial
- Artistas que te gustan

Use LazyRow for horizontal scrolling, SongCard and AlbumCard composables.

- [ ] **Step 2: Update StreamScreen to show home content when logged in**

Show StreamHomeScreen when logged in and no search query.
Show login prompt when not logged in.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add stream home screen with recommended content"
```
