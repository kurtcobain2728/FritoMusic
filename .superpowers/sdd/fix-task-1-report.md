### Task 1: Fix Quality Not Passed to Worker

**Status:** DONE

**Changes Made:**

1. **DownloadViewModel.kt** (line 447):
   - Added `"quality" to selectedQuality` in `workDataOf()` inside `startDownload()`

2. **MusicDownloadWorker.kt** (lines 44, 61):
   - Added `val quality = inputData.getString("quality") ?: "320kbps"` to read quality from input
   - Updated `engine.getDownloadUrl(trackId, trackUrl, quality)` to pass quality

3. **ExtensionEngine.kt** (lines 242, 245, 250, 273, 284):
   - Added `quality: String = "320kbps"` parameter to `getDownloadUrl()` signature
   - Added `escapedQuality` variable for safe JS injection
   - Updated all 3 JavaScript calls to pass quality to extension methods

**Compilation:** PASSED (`gradlew :app:compileDebugKotlin`)
