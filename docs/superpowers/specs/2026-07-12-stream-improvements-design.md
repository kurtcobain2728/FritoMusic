# Diseño: Mejoras de Stream en FritoMusic

**Fecha:** 2026-07-12  
**Estado:** Aprobado

---

## 1. Resumen Ejecutivo

Cinco mejoras para la experiencia de Stream en FritoMusic:

1. **Tutorial para nuevos usuarios** - Guía visual paso a paso
2. **Modal de cerrar sesión** - Gestión de cuenta desde Stream
3. **Contenido recomendado** - Tendencias, nuevos lanzamientos, historial
4. **Listas de reproducción de YouTube** - Acceso a playlists del usuario
5. **Agregar a playlist** - Modal para agregar canciones a playlists de YouTube

---

## 2. Tutorial para Nuevos Usuarios

### Flujo
1. Primera vez que el usuario entra a Stream → se muestra tutorial
2. 3 pasos: Bienvenido → Iniciar sesión → ¡Listo!
3. Cada paso con botones "Siguiente" y "Omitir"
4. Se guarda en preferencias que ya vio el tutorial

### UI
- Overlay semi-transparente oscuro
- Card con ilustración y texto
- Botones de navegación
- Highlight del botón de login en paso 2

### Persistencia
- `SharedPreferences`: `has_seen_stream_tutorial = true`

---

## 3. Modal de Cerrar Sesión

### Flujo
1. Usuario logueado toca ícono de usuario (verde)
2. Aparece bottom sheet con info de cuenta
3. Botón "Cerrar sesión" (rojo) y "Cancelar"
4. Al cerrar: limpia cookies, actualiza UI

### UI
- Bottom sheet Material3
- Nombre y email de la cuenta
- Botón rojo para cerrar sesión

---

## 4. Contenido Recomendado

### Estructura (logueado)
```
Header: [Stream] [Listas] [👤 Login]
Search Bar
Tabs: [Canciones] [Artistas] [Playlists]

Secciones horizontales:
- Tendencias
- Nuevos Lanzamientos
- Basado en tu historial
- Artistas que te gustan
```

### Estructura (no logueado)
```
Header: [Stream] [👤 Iniciar sesión]
Search Bar
Tabs: [Canciones] [Artistas] [Playlists]

Mensaje: "Inicia sesión para ver recomendaciones"
```

### Endpoints
- `YouTube.home()` - Página principal con recomendaciones
- `YouTube.explore()` - Tendencias y nuevos lanzamientos
- `YouTube.charts()` - Charts/tendencias

---

## 5. Listas de Reproducción de YouTube

### UI
- Botón "Listas" en header de Stream
- LazyRow horizontal con playlists del usuario
- Card: thumbnail + nombre + cantidad de canciones
- Al tocar → vista de detalle con canciones
- Botón "+" para crear nueva playlist

### Endpoints
- `YouTube.library("FEmusic_liked_playlists")` - Listar playlists
- `YouTube.playlist(playlistId)` - Ver canciones
- `YouTube.createPlaylist(title)` - Crear nueva

---

## 6. Agregar Canción a Playlist

### Flujo
1. En PlayerScreen, botón "+" → modal
2. Modal muestra playlists de YouTube del usuario
3. Checkbox para seleccionar
4. Botón "Agregar" y "Crear nueva playlist"

### UI
- Bottom sheet con lista de playlists
- Checkbox para seleccionar
- Botón "Agregar" y "Crear nueva"

### Endpoints
- `YouTube.addToPlaylist(playlistId, videoId)` - Agregar canción

---

## 7. Archivos a Crear

| Archivo | Propósito |
|---------|-----------|
| `StreamTutorialScreen.kt` | Tutorial paso a paso |
| `StreamHomeScreen.kt` | Contenido recomendado |
| `StreamPlaylistsScreen.kt` | Listas de reproducción |
| `AddToYouTubePlaylistModal.kt` | Modal para agregar a playlist |
| `YouTubePlaylistDetailScreen.kt` | Detalle de playlist |
| `CreateYouTubePlaylistModal.kt` | Modal para crear playlist |

---

## 8. Archivos a Modificar

| Archivo | Cambio |
|---------|--------|
| `StreamScreen.kt` | Agregar botones, secciones, lógica |
| `StreamViewModel.kt` | Agregar estados para playlists, recomendaciones |
| `YouTubeRepository.kt` | Agregar métodos para playlists |
| `PlayerScreen.kt` | Agregar botón "+" con modal |
| `MainActivity.kt` | Agregar navegación a nuevas pantallas |

---

## 9. Criterios de Aceptación

- [ ] Tutorial se muestra solo la primera vez
- [ ] Modal de cerrar sesión funciona correctamente
- [ ] Contenido recomendado se carga cuando logueado
- [ ] Listas de reproducción se muestran correctamente
- [ ] Se puede agregar canción a playlist de YouTube
- [ ] Se puede crear nueva playlist desde el modal
- [ ] Diseño consistente con la app

---

## 10. Próximos Pasos

1. Crear plan de implementación detallado
2. Implementar tutorial
3. Implementar modal de cerrar sesión
4. Implementar contenido recomendado
5. Implementar listas de reproducción
6. Implementar agregar a playlist
7. Testing y validación
