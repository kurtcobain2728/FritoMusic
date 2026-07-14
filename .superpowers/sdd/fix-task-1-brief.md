### Task 1: Fix Quality Not Passed to Worker

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/viewmodels/DownloadViewModel.kt`
- Modify: `app/src/main/java/com/frito/music/downloader/MusicDownloadWorker.kt`
- Modify: `app/src/main/java/com/frito/music/extensions/engine/ExtensionEngine.kt`

**Bug:** Quality selection is cosmetic - not passed to worker or extension.

- [ ] **Step 1: Add quality to workData in DownloadViewModel.startDownload()**

Find the `workDataOf()` call and add:
```kotlin
"quality" to selectedQuality
```

- [ ] **Step 2: Read quality in MusicDownloadWorker.doWork()**

Add:
```kotlin
val quality = inputData.getString("quality") ?: "320kbps"
```

- [ ] **Step 3: Pass quality to ExtensionEngine.getDownloadUrl()**

Update the call to include quality parameter.

- [ ] **Step 4: Update ExtensionEngine.getDownloadUrl() signature**

Add quality parameter with default value.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 6: Commit**

```bash
git commit -m "fix: pass quality parameter to download worker"
```
