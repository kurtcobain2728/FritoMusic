# Plan de Implementación: Video Nativo, Calidad de Audio, Crossfade y Gestión de Caché

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar reproducción nativa de video (Canvas/Music Video sin WebView ni Error 152), selector de calidad de streaming, crossfade configurable para transiciones de pistas, y gestor de almacenamiento/caché en "Más", asegurando alta fidelidad visual y estabilidad.

**Architecture:**
- **Video:** Reemplazar el `WebView` con un `ExoPlayer` secundario acoplado a un `PlayerView` de Media3. Extraer streams `.mp4` directos vía `NewPipeExtractor` y `YouTubeRepository`. El reproductor de video se mantiene silenciado (`volume = 0f`), sincronizado en tiempo y estado con el reproductor principal de audio (`MusicService`).
- **Calidad de Streaming:** Nuevo gestor de preferencias `StreamQualityPreferences` (Baja ~64kbps, Normal ~128kbps, Alta ~256kbps [default]), con filtrado inteligente en `selectAudioFormatCandidates()` en `YouTubeRepository.kt`, reflejado con badge en `PlayerScreen.kt`.
- **Crossfade:** Administrador de volumen dinámico en `MusicService.kt` con rampa fade-out al terminar la pista y fade-in al iniciar la siguiente, configurado mediante `CrossfadePreferences` y la pantalla `CrossfadeSettingsScreen.kt`.
- **Gestión de Caché:** `StorageCacheManager` que supervisa `cacheDir/stream-cache` y `cacheDir/coil`. Permite configurar el límite LRU de música (256 MB a 8 GB, default 1 GB), calcular espacio ocupado y limpiar caché de música e imágenes de forma independiente con `StorageCacheScreen.kt`.
- **Widget Home Screen:** Bitmap caching en memoria para eliminar el parpadeo en cambios de canción y estado en `FritoMusicWidgetProvider.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer & PlayerView), Coil, NewPipeExtractor, Coroutines & StateFlow.

---

## Tareas

### Task 1: Extracción de Stream de Video y Reproductor Nativo de Video
- [ ] Modificar `innertube/src/main/kotlin/com/music/innertube/pages/NewPipe.kt` para agregar `getVideoStreamUrl(videoId: String): String?`.
- [ ] Modificar `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt` para agregar `getVideoStream(videoId: String): Result<String>`.
- [ ] Actualizar `PlayerScreen.kt`:
  - Reemplazar el `WebView` iframe por `AndroidView` con `androidx.media3.ui.PlayerView` o componente nativo de reproducción.
  - Sincronizar play/pause y seek con el audio de `PlayerViewModel`.
  - Agregar estado de carga y diseño pulido 16:9 con aura esmeralda cuando está reproduciendo.

### Task 2: Configuración de Calidad de Streaming
- [ ] Crear `app/src/main/java/com/frito/music/data/preferences/StreamQualityPreferences.kt`.
- [ ] Modificar `YouTubeRepository.kt` para ordenar formatos según la calidad seleccionada en `StreamQualityPreferences`.
- [ ] Crear `app/src/main/java/com/frito/music/ui/screens/StreamQualityScreen.kt`.
- [ ] Modificar `PlayerScreen.kt` para mostrar el badge de bitrate/calidad en streaming.

### Task 3: Configuración y Motor de Crossfade
- [ ] Crear `app/src/main/java/com/frito/music/data/preferences/CrossfadePreferences.kt`.
- [ ] Modificar `MusicService.kt` para integrar fade-out antes de terminar y fade-in al comenzar la pista.
- [ ] Crear `app/src/main/java/com/frito/music/ui/screens/CrossfadeSettingsScreen.kt`.

### Task 4: Gestión de Almacenamiento y Caché
- [ ] Crear `app/src/main/java/com/frito/music/data/preferences/StorageCacheManager.kt` para calcular tamaños de caché, limpiar y persistir el límite dinámico.
- [ ] Modificar `MusicService.kt` y `PlayerCacheHolder.kt` para soportar el límite de caché configurable (256 MB – 8 GB) y vaciado completo.
- [ ] Crear `app/src/main/java/com/frito/music/ui/screens/StorageCacheScreen.kt` con barras de progreso y botones de limpieza independientes.

### Task 5: Integración en "Más", Navegación y Widget
- [ ] Modificar `MoreScreen.kt` para incluir los accesos a "Calidad de Streaming", "Crossfade" y "Almacenamiento y Caché".
- [ ] Modificar `MainActivity.kt` para conectar las rutas `calidades`, `crossfade` y `almacenamiento`.
- [ ] Optimizar `FritoMusicWidgetProvider.kt` con memoria en caché Bitmap para evitar parpadeos.
