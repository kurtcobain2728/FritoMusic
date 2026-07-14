# Diseño: Streaming de YouTube Music en FritoMusic

**Fecha:** 2026-07-12  
**Estado:** Aprobado  
**Alcance:** Streaming básico + letras sincronizadas

---

## 1. Resumen Ejecutivo

Integrar funcionalidad de streaming de YouTube Music en FritoMusic, permitiendo buscar y reproducir canciones en línea con reproducción en segundo plano y letras sincronizadas.

**Fuente de música:** YouTube Music InnerTube API (copiado de Echo-Music)  
**Enfoque:** Módulo Gradle independiente `innertube/`

---

## 2. Arquitectura General

```
┌─────────────────────────────────────────────────────────┐
│                    FritoMusic App                        │
├─────────────────────────────────────────────────────────┤
│  StreamScreen ← StreamViewModel ← innertube module      │
│       ↓              ↓                    ↓              │
│  SearchUI      PlayerViewModel    YouTubeRepository      │
│       ↓              ↓                    ↓              │
│  ResultsUI     MusicService       InnerTube API          │
│                      ↓                    ↓              │
│                  ExoPlayer        URL Resolution         │
│                      ↓                    ↓              │
│              MediaSession         SimpleCache            │
└─────────────────────────────────────────────────────────┘
```

### Componentes Nuevos

| Componente | Propósito |
|------------|-----------|
| `innertube/` | Módulo Gradle con API de YouTube Music |
| `StreamViewModel` | Maneja búsqueda, resultados, estado de streaming |
| `YouTubeRepository` | Capa de abstracción sobre InnerTube |
| `StreamScreen` (rediseñada) | UI de búsqueda y resultados |
| `StreamableTrack` | Modelo para tracks de streaming |
| `LyricsRepository` | Obtención de letras via InnerTube |

### Componentes Modificados

| Componente | Cambio |
|------------|--------|
| `PlayerViewModel` | Aceptar URLs remotas además de `file://` |
| `MusicService` | Soporte streaming HTTP/HTTPS + SimpleCache |
| `PlayerScreen` | Info de calidad dinámica + letras reales |

---

## 3. Módulo InnerTube

### Alcance

Extraer el módulo `innertube/` de Echo-Music con las siguientes funcionalidades:

**Incluido:**
- Búsqueda de canciones, álbumes, artistas, playlists
- Obtención de stream URLs (con signature cipher)
- Navegación de playlists y álbumes
- Obtención de letras

**NO incluido:**
- Google Cast
- Listen Together
- Echo Brain (AI queue)
- Reconocimiento musical (Shazam)
- Scrobbling (Last.fm/ListenBrainz)

### Dependencias

- Ktor Client (HTTP)
- Kotlinx Serialization (JSON)
- NewPipe Extractor (signature deobfuscation)

### Archivos Principales

- `YouTube.kt` - Cliente HTTP para API InnerTube
- `YouTubeClient.kt` - Definiciones de clientes (ANDROID_VR, WEB_REMIX, etc.)
- `YouTubePlayer.kt` - Respuestas del endpoint `player`
- `models/` - Data classes para respuestas de la API

---

## 4. Flujo de Streaming

### Pipeline de Reproducción

```
1. Usuario busca "canción X" en StreamScreen
2. StreamViewModel → YouTubeRepository.search(query)
3. InnerTube API → lista de resultados (SearchResult)
4. UI muestra resultados (título, artista, duración, thumbnail)
5. Usuario selecciona un track
6. StreamViewModel → YouTubeRepository.getStreamUrl(videoId)
7. InnerTube player endpoint → resuelve URL de audio
   - Intenta con ANDROID_VR primero
   - Fallback a otros clientes si falla
   - Aplica signature cipher si es necesario
8. PlayerViewModel.playStream(url, metadata)
9. MusicService crea MediaItem con URI HTTP
10. ExoPlayer inicia streaming con buffer de 50s
11. Cache local (SimpleCache) almacena chunks descargados
```

### Manejo de Errores

| Error | Acción |
|-------|--------|
| Sin internet | Mostrar mensaje + botón reintentar |
| URL expirada | Re-resolver automáticamente |
| Fallo de cliente | Fallback al siguiente cliente |
| Rate limiting | Mostrar mensaje de espera |

---

## 5. UI y Pantallas

### StreamScreen (Rediseñada)

Diseño basado en `DownloadScreen` con identidad propia:

```
┌─────────────────────────────────────────┐
│ 🎵 Stream                               │  ← Header
├─────────────────────────────────────────┤
│ 🔍 Buscar en YouTube Music...        ✕ │  ← Búsqueda redondeada
├─────────────────────────────────────────┤
│ [Trending] [Canciones] [Playlists]      │  ← Tabs de categoría
├─────────────────────────────────────────┤
│ [Thumb] Canción 1            ▶ Play     │  ← Resultado canción
│         Artista 1 • 3:45                │
│ [Thumb] Canción 2            ▶ Play     │
│         Artista 2 • 4:12                │
│ [Thumb] Playlist 1            →         │  ← Resultado playlist
│         15 canciones                    │
└─────────────────────────────────────────┘
│ [MiniPlayer]                            │
└─────────────────────────────────────────┘
```

### Componentes UI Nuevos

- `StreamSearchBar` - Campo de búsqueda con debounce (estilo DownloadScreen)
- `StreamResultItem` - Card de resultado (thumbnail, título, artista, duración)
- `StreamResultList` - Lista lazy de resultados
- `StreamCategoryTabs` - Tabs para filtrar (Trending, Canciones, Playlists)

### Estados de la UI

- **Vacío:** Icono de música + texto "Busca tu canción favorita"
- **Loading:** CircularProgressIndicator
- **Resultados:** Lista de tracks/playlists
- **Error:** Mensaje de error + botón reintentar

---

## 6. Reproducción en Segundo Plano

### Estado Actual

Ya implementado en FritoMusic:
- `MusicService` es `MediaSessionService` con `foregroundServiceType="mediaPlayback"`
- Permisos declarados (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`)
- Controles de notificación funcionan

### Cambios Necesarios

1. **Permitir URI HTTP/HTTPS** en `PlayerViewModel.playAudios()`
2. **Configurar buffer** para streaming (50s)
3. **Agregar SimpleCache** para cachear streams

### Configuración de Cache

```kotlin
// En MusicService:
SimpleCache("stream-cache", LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L)) // 500MB
CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
```

### Cache de URLs

- Almacenar URLs resueltas en memoria con TTL (expiración)
- Re-resolver automáticamente si la URL expira

---

## 7. Letras Sincronizadas

### Implementación

1. Crear `LyricsRepository` que use InnerTube para obtener letras
2. Agregar estado de letras al `StreamViewModel`
3. Modificar overlay de letras en `PlayerScreen` para mostrar letras reales
4. Soporte para letras sincronizadas (highlight de línea actual)

### UI de Letras

- Overlay existente en `PlayerScreen` ya tiene el contenedor
- Cambiar "Letra de la Canción (Proximamente)" por letras reales
- Scroll automático a la línea actual si hay sincronización

---

## 8. Modelo de Datos

### StreamableTrack

```kotlin
data class StreamableTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val thumbnailUrl: String,
    val isStream: Boolean = true
)
```

### Conversión a AudioFile

Para reutilizar el `PlayerViewModel` existente, convertir `StreamableTrack` a `AudioFile`:

```kotlin
fun StreamableTrack.toAudioFile(streamUrl: String) = AudioFile(
    id = videoId.hashCode().toLong(),
    title = title,
    artist = artist,
    path = streamUrl,  // URL HTTP en vez de file://
    durationMs = durationMs,
    sizeBytes = 0,  // Desconocido para streaming
    albumUri = thumbnailUrl,
    album = album ?: "",
    dateAdded = System.currentTimeMillis()
)
```

---

## 9. Permisos

Ya declarados en FritoMusic:
- `INTERNET` ✓
- `FOREGROUND_SERVICE` ✓
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` ✓
- `WAKE_LOCK` ✓
- `POST_NOTIFICATIONS` ✓

No se requieren permisos adicionales.

---

## 10. Dependencias Nuevas

Agregar a `build.gradle.kts`:

```kotlin
// InnerTube module dependencies
implementation("io.ktor:ktor-client-core:3.4.0")
implementation("io.ktor:ktor-client-okhttp:3.4.0")
implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
implementation("org.schabi:newpipe-extractor:0.25.2")
```

---

## 11. Archivos a Crear

| Archivo | Propósito |
|---------|-----------|
| `innertube/build.gradle.kts` | Configuración del módulo |
| `innertube/src/main/kotlin/.../YouTube.kt` | Cliente API |
| `innertube/src/main/kotlin/.../YouTubeClient.kt` | Definiciones de clientes |
| `innertube/src/main/kotlin/.../YouTubePlayer.kt` | Respuestas player |
| `innertube/src/main/kotlin/.../models/` | Data classes |
| `app/.../data/network/yt/YouTubeRepository.kt` | Repositorio de YouTube |
| `app/.../data/network/yt/LyricsRepository.kt` | Repositorio de letras |
| `app/.../ui/viewmodels/StreamViewModel.kt` | ViewModel de streaming |
| `app/.../ui/screens/StreamScreen.kt` | UI principal (rediseñada) |

---

## 12. Archivos a Modificar

| Archivo | Cambio |
|---------|--------|
| `settings.gradle.kts` | Agregar módulo `innertube` |
| `app/build.gradle.kts` | Agregar dependencia al módulo |
| `app/.../ui/viewmodels/PlayerViewModel.kt` | Aceptar URLs remotas |
| `app/.../service/MusicService.kt` | Soporte streaming + cache |
| `app/.../ui/screens/PlayerScreen.kt` | Info calidad dinámica + letras |
| `app/.../MainActivity.kt` | Conectar StreamScreen con ViewModels |

---

## 13. Criterios de Aceptación

- [ ] Buscar canciones en YouTube Music desde StreamScreen
- [ ] Reproducir canciones en streaming con ExoPlayer
- [ ] Reproducción en segundo plano con controles de notificación
- [ ] Cache de streams para reproducción offline parcial
- [ ] Mostrar letras sincronizadas en PlayerScreen
- [ ] Manejo de errores (sin internet, URL expirada, etc.)
- [ ] UI consistente con el estilo de DownloadScreen

---

## 14. Riesgos y Mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| YouTube bloquea requests | Sistema de fallback con múltiples clientes |
| URLs expiran rápido | Cache con TTL + re-resolución automática |
| Rate limiting | Mostrar mensaje de espera al usuario |
| Cambios en InnerTube API | Mantener módulo separado para fácil actualización |

---

## 15. Próximos Pasos

1. Crear plan de implementación detallado
2. Extraer módulo InnerTube de Echo-Music
3. Implementar StreamViewModel y YouTubeRepository
4. Rediseñar StreamScreen
5. Modificar PlayerViewModel para URLs remotas
6. Agregar SimpleCache a MusicService
7. Implementar LyricsRepository
8. Testing y validación
