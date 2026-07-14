# Plan de Implementación: Mejoras de Stream

**Objetivo:** Implementar 5 mejoras para la experiencia de Stream en FritoMusic

**Arquitectura:** Extensiones al sistema existente de Stream, reutilizando componentes y patrones actuales

**Tech Stack:** Kotlin, Jetpack Compose, Media3, InnerTube API

---

## Tarea 1: Tutorial para Nuevos Usuarios

**Archivos a crear:**
- `app/.../ui/screens/StreamTutorialScreen.kt`

**Archivos a modificar:**
- `app/.../ui/screens/StreamScreen.kt`
- `app/.../data/repository/YouTubeLoginManager.kt`

**Pasos:**
1. Crear `StreamTutorialScreen` con 3 pasos
2. Agregar preferencia `has_seen_stream_tutorial`
3. Mostrar tutorial en StreamScreen si no lo ha visto
4. Guardar preferencia al completar

---

## Tarea 2: Modal de Cerrar Sesión

**Archivos a crear:**
- `app/.../ui/components/YouTubeLogoutModal.kt`

**Archivos a modificar:**
- `app/.../ui/screens/StreamScreen.kt`
- `app/.../data/repository/YouTubeLoginManager.kt`

**Pasos:**
1. Crear `YouTubeLogoutModal` (bottom sheet)
2. Mostrar info de cuenta (nombre, email)
3. Agregar botón "Cerrar sesión"
4. Limpiar cookies al cerrar sesión
5. Actualizar UI (ícono vuelve a gris)

---

## Tarea 3: Contenido Recomendado

**Archivos a crear:**
- `app/.../ui/screens/StreamHomeScreen.kt`

**Archivos a modificar:**
- `app/.../ui/viewmodels/StreamViewModel.kt`
- `app/.../data/network/yt/YouTubeRepository.kt`
- `app/.../ui/screens/StreamScreen.kt`

**Pasos:**
1. Agregar métodos en YouTubeRepository: `getHome()`, `getExplore()`, `getCharts()`
2. Agregar estados en StreamViewModel
3. Crear `StreamHomeScreen` con secciones horizontales
4. Mostrar StreamHomeScreen cuando logueado
5. Mostrar mensaje cuando no logueado

---

## Tarea 4: Listas de Reproducción de YouTube

**Archivos a crear:**
- `app/.../ui/screens/StreamPlaylistsScreen.kt`
- `app/.../ui/screens/YouTubePlaylistDetailScreen.kt`
- `app/.../ui/components/CreateYouTubePlaylistModal.kt`

**Archivos a modificar:**
- `app/.../ui/viewmodels/StreamViewModel.kt`
- `app/.../data/network/yt/YouTubeRepository.kt`
- `app/.../ui/screens/StreamScreen.kt`
- `app/.../MainActivity.kt`

**Pasos:**
1. Agregar métodos en YouTubeRepository: `getPlaylists()`, `getPlaylistSongs()`, `createPlaylist()`
2. Agregar estados en StreamViewModel
3. Crear `StreamPlaylistsScreen` con LazyRow
4. Crear `YouTubePlaylistDetailScreen`
5. Crear `CreateYouTubePlaylistModal`
6. Agregar botón "Listas" en header de Stream
7. Agregar navegación en MainActivity

---

## Tarea 5: Agregar Canción a Playlist

**Archivos a crear:**
- `app/.../ui/components/AddToYouTubePlaylistModal.kt`

**Archivos a modificar:**
- `app/.../ui/screens/PlayerScreen.kt`
- `app/.../ui/viewmodels/StreamViewModel.kt`
- `app/.../data/network/yt/YouTubeRepository.kt`

**Pasos:**
1. Agregar método en YouTubeRepository: `addToPlaylist()`
2. Crear `AddToYouTubePlaylistModal`
3. Modificar PlayerScreen para mostrar modal al darle "+"
4. Agregar lógica para agregar canción a playlist seleccionada

---

## Orden de Implementación

1. **Tarea 1** - Tutorial (base para UX)
2. **Tarea 2** - Modal cerrar sesión (necesario para autenticación)
3. **Tarea 3** - Contenido recomendado (requiere autenticación)
4. **Tarea 4** - Listas de reproducción (requiere autenticación)
5. **Tarea 5** - Agregar a playlist (requiere listas)

---

## Dependencias

- Tareas 1 y 2 son independientes
- Tarea 3 requiere Tarea 2 (autenticación)
- Tarea 4 requiere Tarea 2 (autenticación)
- Tarea 5 requiere Tarea 4 (listas)

---

## Criterios de Verificación

- [ ] Tutorial se muestra solo la primera vez
- [ ] Modal de cerrar sesión funciona
- [ ] Contenido recomendado se carga
- [ ] Listas de reproducción se muestran
- [ ] Se puede agregar canción a playlist
- [ ] Se puede crear nueva playlist
- [ ] Diseño consistente
- [ ] Build exitoso
- [ ] Testing manual en dispositivo
