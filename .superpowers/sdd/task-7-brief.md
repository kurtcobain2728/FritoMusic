### Task 7: Refresco y verificación final

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/FavoritesScreen.kt` (opcional: refrescar likes al volver)

**Interfaces:**
- Consumes: nada nuevo

- [ ] **Step 1: Refrescar likes al entrar a la pestaña Online** (ya se hace con `LaunchedEffect(Unit)` en `FavoritesOnlineContent`). Añadir además refresco al `ON_RESUME` para que un like hecho desde el player aparezca al volver:

```kotlin
    // en FavoritesOnlineContent, reemplazar el LaunchedEffect por:
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onlineLibraryViewModel.loadLikedSongs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

- [ ] **Step 2: Verificación completa**

Run: `.\gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Pruebas en dispositivo:
1. Favoritos: pestañas Offline/Online + swipe en ambas direcciones.
2. Favoritos Online: logueado → aparecen likes; ❤️ en player a canción online → aparece al volver.
3. Canción local → ❤️ offline (pestaña Offline).
4. Playlists Online: crear → aparece; abrir detalle; quitar canción.
5. Sin sesión: pestañas Online muestran prompt de login.
6. Rotación no pierde pestaña (pagerState con rememberSaveable si es necesario).

---
CONSTRAINTS GLOBALES (obligatorio):
- minSdk 26, targetSdk 34, Compose BOM 2024.02.00
- NO ejecutar gradlew/assembleDebug: el usuario compila. Verifica estructura (llaves y par�ntesis balanceados) y que las referencias/imports existan.
- NO hacer commits git: el usuario no lo ha pedido.
- Reutilizar patrones existentes (StreamTrackItem, prompt de login de StreamScreen, playArtistSong/playAlbumSong con queueSongs).
- Sin dependencias nuevas. Sesi�n: YouTubeLoginManager.isLoggedIn(). videoId: YouTubeUrlParser.extractVideoId().
- Al terminar: escribir el reporte en el archivo indicado y devolver estado + archivos tocados + verificaci�n.
