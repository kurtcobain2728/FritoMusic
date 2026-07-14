### Task 1: Stream Tutorial Screen

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/screens/StreamTutorialScreen.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`
- Modify: `app/src/main/java/com/frito/music/data/repository/YouTubeLoginManager.kt`

**Interfaces:**
- Produces: `StreamTutorialScreen` composable with 3 steps
- Produces: `YouTubeLoginManager.hasSeenTutorial()` / `setTutorialSeen()`

**Context:** This is the first task in a plan to improve the Stream experience. The tutorial should show3 steps to new users explaining how to use Stream.

- [ ] **Step 1: Add tutorial preference to YouTubeLoginManager**

Add these methods to `YouTubeLoginManager.kt`:

```kotlin
fun hasSeenTutorial(): Boolean {
    return prefs?.getBoolean("has_seen_stream_tutorial", false) ?: false
}

fun setTutorialSeen() {
    prefs?.edit()?.putBoolean("has_seen_stream_tutorial", true)?.apply()
}
```

- [ ] **Step 2: Create StreamTutorialScreen**

Create `StreamTutorialScreen.kt` with:
- 3 tutorial steps: Bienvenido, Iniciar Sesión, ¡Listo!
- Step indicators (dots)
- Buttons: "Omitir" and "Siguiente"/"¡Entendido!"
- Semi-transparent overlay
- Card with icon, title, description

- [ ] **Step 3: Update StreamScreen to show tutorial**

At the beginning of StreamScreen, add:
```kotlin
var showTutorial by remember { mutableStateOf(!YouTubeLoginManager.hasSeenTutorial()) }

if (showTutorial) {
    StreamTutorialScreen(
        onFinish = {
            YouTubeLoginManager.setTutorialSeen()
            showTutorial = false
        }
    )
}
```

- [ ] **Step 4: Add login text next to icon in header**

Update the login button in header to show "Iniciar sesión" text when not logged in.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamTutorialScreen.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git add app/src/main/java/com/frito/music/data/repository/YouTubeLoginManager.kt
git commit -m "feat: add stream tutorial for new users"
```
