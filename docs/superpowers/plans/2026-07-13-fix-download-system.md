# Fix Download System Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans to implement this plan task-by-task.

**Goal:** Fix 5 critical/important bugs in the download system so downloads work correctly with proper quality, file format, and track names.

**Architecture:** Fix the data flow from DownloadViewModel → MusicDownloadWorker → StorageUtils to properly pass quality, use correct file extension, show track names, and fix fallback logic.

**Tech Stack:** Kotlin, WorkManager, ExtensionEngine (Rhino JS)

## Global Constraints

- Min SDK: 26
- Target SDK: 34
- Package: `com.frito.music`
- Use existing ExtensionEngine pattern
- Follow existing code style

---

### Task 1: Fix Quality Not Passed to Worker (Critical)

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/DownloadViewModel.kt`
- Modify: `app/src/main/java/com/frito/music/downloader/MusicDownloadWorker.kt`

**Interfaces:**
- Produces: `MusicDownloadWorker` receives `quality` in inputData
- Produces: `ExtensionEngine.getDownloadUrl()` receives quality parameter

- [ ] **Step 1: Add quality to workData in DownloadViewModel**

Find `startDownload()` method and add quality to workData:

```kotlin
val workData = workDataOf(
    "trackId" to trackId,
    "extensionId" to extensionId,
    "trackName" to trackName,
    "artistName" to artistName,
    "albumName" to albumName,
    "trackUrl" to trackUrl,
    "quality" to selectedQuality  // ADD THIS LINE
)
```

- [ ] **Step 2: Read quality in MusicDownloadWorker**

In `doWork()`, add:

```kotlin
val quality = inputData.getString("quality") ?: "320kbps"
```

- [ ] **Step 3: Pass quality to getDownloadUrl**

Update the call to include quality:

```kotlin
val downloadUrl = engine.getDownloadUrl(trackId, trackUrl, quality)
```

- [ ] **Step 4: Update ExtensionEngine.getDownloadUrl to accept quality**

Modify the method signature:

```kotlin
fun getDownloadUrl(trackId: String, trackUrl: String?, quality: String = "320kbps"): String? {
    // ... existing code
    // Pass quality to JS extension if supported
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/viewmodels/DownloadViewModel.kt
git add app/src/main/java/com/frito/music/downloader/MusicDownloadWorker.kt
git add app/src/main/java/com/frito/music/extensions/engine/ExtensionEngine.kt
git commit -m "fix: pass quality parameter to download worker"
```

---

### Task 2: Fix File Extension Based on Quality (Critical)

**Files:**
- Modify: `app/src/main/java/com/frito/music/downloader/StorageUtils.kt`

**Interfaces:**
- Produces: `StorageUtils.createAudioFileStream()` accepts quality parameter
- Produces: Correct file extension (.mp3, .flac, .m4a) based on quality

- [ ] **Step 1: Add quality parameter to createAudioFileStream**

Update the method signature:

```kotlin
fun createAudioFileStream(
    context: Context,
    trackName: String,
    artistName: String,
    albumName: String,
    quality: String = "320kbps"  // ADD THIS
): Pair<OutputStream, Uri>? {
```

- [ ] **Step 2: Determine file extension based on quality**

Add logic to determine extension:

```kotlin
val (extension, mimeType) = when {
    quality.contains("FLAC", ignoreCase = true) || quality.contains("Hi-Res", ignoreCase = true) -> 
        "flac" to "audio/flac"
    quality.contains("320", ignoreCase = true) -> 
        "mp3" to "audio/mpeg"
    quality.contains("256", ignoreCase = true) -> 
        "m4a" to "audio/mp4"
    quality.contains("128", ignoreCase = true) -> 
        "mp3" to "audio/mpeg"
    else -> 
        "mp3" to "audio/mpeg"
}

val fileName = "$safeTrack.$extension"
```

- [ ] **Step 3: Update MusicDownloadWorker to pass quality**

```kotlin
val outputStreamResult = StorageUtils.createAudioFileStream(
    context = applicationContext,
    trackName = trackName,
    artistName = artistName,
    albumName = albumName,
    quality = quality  // ADD THIS
)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/downloader/StorageUtils.kt
git add app/src/main/java/com/frito/music/downloader/MusicDownloadWorker.kt
git commit -m "fix: use correct file extension based on quality (FLAC, MP3, M4A)"
```

---

### Task 3: Fix Fallback Logic (Important)

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/DownloadViewModel.kt`

**Interfaces:**
- Produces: Correct fallback logic - only use Deezer when ALL lists are empty

- [ ] **Step 1: Find the fallback logic**

Search for:
```kotlin
if (extResults.tracks.isEmpty() || extResults.albums.isEmpty() || extResults.artists.isEmpty()) {
```

- [ ] **Step 2: Fix the logic**

Change `||` to `&&`:

```kotlin
if (extResults.tracks.isEmpty() && extResults.albums.isEmpty() && extResults.artists.isEmpty()) {
```

This way, Deezer fallback only triggers when the extension returns NO results at all (not when one category is empty).

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/viewmodels/DownloadViewModel.kt
git commit -m "fix: correct fallback logic - use AND instead of OR"
```

---

### Task 4: Show Track Name in Downloads Manager (Important)

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/DownloadsManagerScreen.kt`

**Interfaces:**
- Consumes: `workInfo.progress.getString("trackName")`
- Produces: Display track name instead of UUID

- [ ] **Step 1: Find where track name is displayed**

Search for:
```kotlin
Text(text = "Descarga ${workInfo.id.toString().take(6)}...")
```

- [ ] **Step 2: Replace with track name from progress data**

```kotlin
val trackName = workInfo.progress.getString("trackName") 
    ?: workInfo.outputData.getString("trackName")
    ?: "Descarga ${workInfo.id.toString().take(6)}"

Text(
    text = trackName,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

- [ ] **Step 3: Verify track name is passed in MusicDownloadWorker**

In `MusicDownloadWorker.kt`, ensure trackName is in progress:

```kotlin
setProgress(workDataOf(
    "progress" to progressPercent,
    "speed" to speedMBps,
    "downloadedMB" to downloadedMB,
    "totalMB" to totalMB,
    "trackName" to trackName  // ADD THIS IF NOT PRESENT
))
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/DownloadsManagerScreen.kt
git add app/src/main/java/com/frito/music/downloader/MusicDownloadWorker.kt
git commit -m "fix: show track name instead of UUID in downloads manager"
```

---

### Task 5: Fix StorageBridge Persistence (Important)

**Files:**
- Modify: `app/src/main/java/com/frito/music/extensions/engine/ExtensionBridges.kt`

**Interfaces:**
- Produces: `StorageBridge` persists data to SharedPreferences
- Produces: Data survives between download sessions

- [ ] **Step 1: Add context to StorageBridge**

```kotlin
class StorageBridge(private val context: Context, private val extensionId: String) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
    }
```

- [ ] **Step 2: Update get/set to use SharedPreferences**

```kotlin
operator fun get(key: String): String? {
    return prefs.getString(key, null)
}

operator fun set(key: String, value: String) {
    prefs.edit().putString(key, value).apply()
}

fun remove(key: String) {
    prefs.edit().remove(key).apply()
}
```

- [ ] **Step 3: Update ExtensionEngine to pass context**

In `ExtensionEngine.kt`, when creating StorageBridge:

```kotlin
val storage = StorageBridge(context, extensionId)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/extensions/engine/ExtensionBridges.kt
git add app/src/main/java/com/frito/music/extensions/engine/ExtensionEngine.kt
git commit -m "fix: persist StorageBridge data to SharedPreferences"
```

---

### Task 6: Final Verification

**Files:**
- Test: Manual testing

- [ ] **Step 1: Build the app**

Run: `./gradlew assembleDebug`

- [ ] **Step 2: Test download with different qualities**

1. Open app
2. Go to "Más" → "Descargar Música"
3. Select Spotify extension
4. Search for a track
5. Select FLAC quality
6. Download the track
7. Verify file is saved as .flac (not .mp3)

- [ ] **Step 3: Verify track name shows in downloads manager**

1. Go to "Más" → "Gestor de Descargas"
2. Verify track name is shown (not UUID)

- [ ] **Step 4: Commit final state**

```bash
git add .
git commit -m "fix: complete download system fixes"
```
