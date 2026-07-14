### Task 3: YouTube Home API Methods

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt`

**Interfaces:**
- Produces: `YouTubeRepository.getHome()`, `YouTubeRepository.getExplore()`
- Produces: `StreamViewModel.homePage`, `StreamViewModel.explorePage`, `StreamViewModel.isLoadingHome`

**Context:** This task adds API methods for fetching YouTube Music home content and explore/trending content.

- [ ] **Step 1: Add API methods to YouTubeRepository**

Add these methods:
```kotlin
suspend fun getHome(): Result<HomePage> = runCatching {
    YouTube.home().getOrThrow()
}

suspend fun getExplore(): Result<ExplorePage> = runCatching {
    YouTube.explore().getOrThrow()
}
```

- [ ] **Step 2: Add state to StreamViewModel**

Add state flows and loadHomeContent() method.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt
git add app/src/main/java/com/frito/music/ui/viewmodels/StreamViewModel.kt
git commit -m "feat: add YouTube home and explore API methods"
```
