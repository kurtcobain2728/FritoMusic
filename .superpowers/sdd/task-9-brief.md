### Task 9: Implement Lyrics Display

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt`

**Interfaces:**
- Consumes: `StreamViewModel.currentLyrics`
- Produces: Real lyrics display in PlayerScreen overlay

- [ ] **Step 1: Add lyrics state to PlayerScreen**

```kotlin
val currentLyrics by streamViewModel.currentLyrics.collectAsState()
val isLoadingLyrics by streamViewModel.isLoadingLyrics.collectAsState()
```

- [ ] **Step 2: Update lyrics overlay to show real lyrics**

Find the lyrics overlay section and replace:
```kotlin
Text(text = "Letra de la Canción (Proximamente)", ...)
```

With:
```kotlin
@Composable
fun LyricsOverlay(
    lyrics: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    
    // ... existing overlay container code
    
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        }
        lyrics != null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        text = lyrics,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Letras no disponibles",
                    color = appColors.textSecondary,
                    fontSize = 16.sp
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt
git commit -m "feat: implement lyrics display in PlayerScreen"
```
