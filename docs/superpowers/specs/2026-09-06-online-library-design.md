# Diseño: Favoritos y Playlists Online (YouTube Music) en FritoMusic

**Fecha:** 2026-09-06  
**Estado:** Aprobado

---

## 1. Resumen Ejecutivo

Agregar el acceso a la **biblioteca online de YouTube Music** (canciones "Me gusta" y playlists de la cuenta) dentro de las pantallas existentes de **Favoritos** y **Listas de Reproducción**, usando un sistema de **pestañas deslizables** (Offline | Online).

### Regla de oro del ❤️ (contexto)
- **Canción local sonando** → se guarda en favoritos **offline** (SharedPreferences, comportamiento actual).
- **Canción online sonando** → se da "Me gusta" en **YouTube Music** (like real en la cuenta).

### Interacción
- Cada pantalla (Favoritos y Listas) tiene **pestañas arriba** "Offline | Online".
- **Swipe**: deslizar a la derecha estando en Offline → va a Online. Deslizar a la izquierda estando en Online → vuelve a Offline. Pestañas y gesto sincronizados.

---

## 2. Componentes / Arquitectura

### Nuevo `OnlineLibraryViewModel`

ViewModel nuevo (no mezclar con `StreamViewModel`, que ya tiene bastante). Centraliza toda la interacción con la biblioteca de YouTube Music.

**Repositorio:** nuevas funciones en `YouTubeRepository` (app) que envuelven las ya existentes del módulo `innertube`.

| Función (ViewModel) | Llamada subyacente | Descripción |
|---|---|---|
| `loadLikedSongs()` | `YouTube.library("FEmusic_liked_songs")` | Canciones "Me gusta" → `List<SongItem>` |
| `likeSong(videoId, liked)` | `YouTube.likeVideo(videoId, liked)` | Dar/quitar like, actualiza lista local |
| `loadOnlinePlaylists()` | `YouTube.library("FEmusic_liked_playlists")` | Playlists de la cuenta → `List<PlaylistItem>` |
| `loadOnlinePlaylistSongs(id)` | `YouTube.playlist(id)` | Canciones de una playlist |
| `createOnlinePlaylist(title)` | `YouTube.createPlaylist(title)` | Crear playlist nueva |
| `deleteOnlinePlaylist(id)` | `YouTube.deletePlaylist(id)` | Borrar playlist |
| `addToOnlinePlaylist(pid, videoId)` | `YouTube.addToPlaylist(pid, videoId)` | Añadir canción |
| `removeFromOnlinePlaylist(pid, videoId, setVideoId)` | `YouTube.removeFromPlaylist(...)` | Quitar canción |

**Estados que expone** (StateFlows):
- `likedSongs: StateFlow<List<SongItem>>`
- `onlinePlaylists: StateFlow<List<PlaylistItem>>`
- `playlistSongs: StateFlow<PlaylistPage?>` (detalle abierto)
- `isLoadingOnline: StateFlow<Boolean>`
- `onlineError: StateFlow<String?>`
- `likedSongIds: StateFlow<Set<String>>` (derivado, para el ❤️ del player)

**Lazy load + refresh:** al entrar a la pestaña Online, se cargan los datos. Tienen `refreshX()` para volver a cargar tras una acción.

---

## 3. Pantalla Favoritos (con pestañas + swipe)

### Layout
```
FavoritesScreen (nueva versión)
┌────────────────────────────────────┐
│  ← (back)  Favoritos               │
│  ┌──────────┬───────────┐          │
│  │ Offline  │  Online   │ ← TabRow │
│  └──────────┴───────────┘          │
│  ┌──────────────────────────────┐  │
│  │ HorizontalPager              │  │
│  │  Página 0 = Favoritos offline│  │
│  │  Página 1 = Favoritos online │  │
│  └──────────────────────────────┘  │
└────────────────────────────────────┘
```

- **Tab 0 — Offline**: el contenido actual de `FavoritesScreen` (canciones locales marcadas, shuffle/play, header con gradiente).
- **Tab 1 — Online**: 
  - Sin sesión → prompt de login (reutiliza el patrón de `StreamScreen`: ícono + "Inicia sesión" + botón).
  - Con sesión → lista de `likedSongs` como filas estilo `StreamTrackItem`, tap reproduce con **cola** (reutiliza `playArtistSong` con `queueSongs`), corazón relleno = quitar like.
  - Estados: loading, error, empty ("Sin canciones en tu biblioteca aún").

### Gestos
- `HorizontalPager` de Compose maneja el swipe; `TabRow` arriba sincronizado vía `pagerState.currentPage` y `scrollToPage()`.

---

## 4. Pantalla Listas de Reproducción (con pestañas + swipe)

### Layout
```
PlaylistsScreen (nueva versión)
┌────────────────────────────────────┐
│  ← (back)  Listas                  │
│  ┌──────────┬───────────┐          │
│  │ Offline  │  Online   │ ← TabRow │
│  └──────────┴───────────┘          │
│  HorizontalPager                    │
│   Página 0 = Playlists offline     │
│   Página 1 = Playlists online      │
└────────────────────────────────────┘
```

- **Tab 0 — Offline**: contenido actual (crear lista local, tarjetas de playlists locales, tap → detalle local).
- **Tab 1 — Online**:
  - Sin sesión → prompt de login.
  - Con sesión → tarjetas de `onlinePlaylists` (carátula + nombre + conteo). Botón FAB/acción "+ Crear playlist" → diálogo de nombre → `createOnlinePlaylist`.
  - Cada playlist: menú contextual (tres puntos) con **"Borrar"** (confirmación).
  - Tap → pantalla de **detalle online** con canciones, botones reproducir todo/aleatorio, y por canción: **quitar de playlist** y **añadir a otra**.

### Detalle de playlist online
- Reutiliza/adapta `YouTubePlaylistDetailScreen` existente.
- Acciones extra: quitar canción de la playlist (con `setVideoId` de `SongItem`), volver a cargar tras cambios.

---

## 5. ❤️ del reproductor (contexto-aware)

`PlayerViewModel.toggleFavorite()` actual:
```kotlin
fun toggleFavorite() {
    _currentAudio.value?.path?.let { favoritesRepository.toggleFavorite(it) }
}
```

**Nuevo comportamiento** en `PlayerScreen`/`PlayerViewModel`:
1. Si `currentAudio.path` empieza con `http` → canción **online**:
   - Se llama a `OnlineLibraryViewModel.likeSong(videoId, !isLiked)`
   - El estado del corazón sale de `likedSongIds` (del videoId).
2. Si es **local** → comportamiento actual (offline).
3. `isCurrentFavorite` se vuelve un `combine` de: audio local → `favorites.contains(path)`; audio online → `likedSongIds.contains(videoId extraído de path)`.

---

## 6. Datos y Flujo

### Dónde se instancia `OnlineLibraryViewModel`
- `MainActivity` (como los demás): `val onlineLibraryViewModel: OnlineLibraryViewModel = viewModel()`.
- Se pasa a `FavoritesScreen`, `PlaylistsScreen` y al overlay `PlayerScreen`.

### Detección de sesión
- Reutiliza `YouTubeLoginManager.isLoggedIn()` (igual que `StreamScreen`).

### Extracción de videoId de un path online
- El path de una canción stream es la URL de YouTube (`...?v=ID` o la URL resuelta). Para like y para heart state se necesita el `videoId`. `StreamableTrack.videoId` ya se guarda; en `PlayerViewModel` el `AudioFile` solo tiene `path`. **Solución:** `AudioFile` ya usa `path` para stream; extraer `videoId` con el helper existente `YouTubeUrlParser` (en `innertube/utils`) o guardar el videoId en el `mediaId` (ya lo guardamos como `q{generación}-i{índice}`). Mejor: **`StreamViewModel` mantiene `videoIdOfCurrentAudio: StateFlow<String?>`** que se actualiza al reproducir; el corazón lo consulta.

---

## 7. Manejo de errores

- Cualquier fallo de red en online → `onlineError` mostrado como texto en la pestaña (reintentar).
- Like fallido → Toast "No se pudo actualizar en YouTube Music" + revertir estado optimista.
- Operaciones de playlist fallidas → Toast con mensaje.
- Sin sesión → prompt de login, no errores crudos.

---

## 8. Verificación

### Compilación
```bash
./gradlew assembleDebug
```

### Pruebas manuales
1. **Favoritos**: pestañas Offline/Online, swipe en ambas direcciones.
2. **Favoritos Online**: logueado → aparecen likes de la cuenta. Dar ❤️ a canción online del player → aparece al volver.
3. **Canción local** → ❤️ sigue siendo offline (se ve en pestaña Offline).
4. **Playlists Online**: crear → aparece; abrir detalle; quitar canción; borrar playlist (con confirmación).
5. **Sin sesión**: ambas pestañas Online muestran login prompt.
6. Rotación: el estado de pestaña/lista no se pierde.

---

## 9. Fuera de alcance (YAGNI)

- Playlist offline → publicar a YouTube Music.
- Favoritos online → descargar en lote.
- Sincronización bidireccional automática offline↔online.
- Continuación/paginación de listas largas de liked songs (solo primeras ~100).