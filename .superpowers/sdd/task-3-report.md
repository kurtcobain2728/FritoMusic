# Task 3 Report: FavoritesScreen con pestañas Offline | Online

## Status
COMPLETED — estructura verificada (sin compilar; el usuario compila). Sin commits.

## Files modified
1. `app/src/main/java/com/frito/music/ui/screens/FavoritesScreen.kt`
   - Firma de `FavoritesScreen` ampliada con `onlineLibraryViewModel: OnlineLibraryViewModel` y `streamViewModel: StreamViewModel`.
   - Raíz reestructurada: gradiente + back button + `TabRow` (Offline | Online) + `HorizontalPager` (2 páginas).
   - Contenido offline extraído a `FavoritesOfflineContent` (header, shuffle/play, lista, empty state — textualmente igual).
   - Nuevo `FavoritesOnlineContent` (login prompt, loading, vacío/error, LazyColumn con `playArtistSong` + queue).
   - Nuevo `OnlineLikedSongRow` (thumbnail/AsyncImage, título, artistas, corazón para like/unlike optimista).
   - Imports: `androidx.compose.material3.*` (wildcard), `androidx.compose.foundation.pager.*`, `rememberCoroutineScope`, `LaunchedEffect`, `coil.compose.AsyncImage`, `TextOverflow`, `AudioFile`, `SongItem`, `OnlineLibraryViewModel`, `StreamViewModel`.

2. `app/src/main/java/com/frito/music/MainActivity.kt`
   - Import añadido: `com.frito.music.ui.viewmodels.OnlineLibraryViewModel` (línea 51).
   - `val onlineLibraryViewModel: OnlineLibraryViewModel = viewModel()` junto a los demás (línea 103).
   - Ruta `"favoritos"` ahora pasa `onlineLibraryViewModel` y `streamViewModel` (líneas 322-328).

## Verification (no compile)
- Balance de llaves/paréntesis/corchetes (PowerShell):
  - FavoritesScreen.kt: braces 67/67, parens 187/187, brackets 1/1.
  - MainActivity.kt: braces 135/135, parens 221/221, brackets 0/0.
- Grep MainActivity.kt: `OnlineLibraryViewModel` importado (L51), declarado (L103), pasado a FavoritesScreen (L325).
- Lectura completa de FavoritesScreen.kt: TabRow con 2 tabs, HorizontalPager con 2 páginas (Offline→FavoritesOfflineContent, Online→FavoritesOnlineContent), imports correctos.
- Referencias verificadas en el codebase:
  - `StreamViewModel.playArtistSong(song: SongItem, playerViewModel: PlayerViewModel, queueSongs: List<SongItem>? = null)` existe (StreamViewModel.kt:216).
  - `OnlineLibraryViewModel.likedSongs/likedSongIds/isLoadingLiked/onlineError/loadLikedSongs/likeSong` existen.
  - `YouTubeLoginManager.isLoggedIn()` existe (YouTubeLoginManager.kt:22).
  - `SongItem.thumbnail` es `String` no-null; se usa `song.thumbnail.isNotEmpty()` como en StreamHomeScreen.kt:205 / StreamArtistDetailScreen.kt:248.
  - `AudioFileRowUI` está en el mismo paquete (LibraryScreen.kt:173) — sin import extra.
  - `HorizontalPager`/`rememberPagerState` ya usados en OnboardingScreen/StreamTutorialScreen — sin dependencia nueva.

## Self-review
- El contenido offline se movió completo a `FavoritesOfflineContent`:
  - Header (cover corazón 120dp + "Favoritos" + contador) ✓
  - Botones Shuffle (toggle + random) y Play ✓
  - LazyColumn de favoritos (key = path, AudioFileRowUI, play en index) ✓
  - Empty state ("Toca el corazón...") ✓
  - Gradiente raíz condicional (isDark 0.22f/0.14f) se conserva idéntico al original (no el 0.22f fijo del brief, para no cambiar la UI).
- Se descartó el import `itemsIndexed` (ya no se usaba ni en el original; solo advertencia de lint, no rompe compilación).
- `coil.compose.AsyncImage` se importa como `AsyncImage` (convención del proyecto) en lugar del nombre totalmente cualificado del brief — funcionalmente idéntico.

## Concerns
- Ninguno bloqueante. Puntos a notar:
  - `Modifier.weight(1f)` del HorizontalPager y `weight(1f)` del empty state offline dependen de Column con altura acotada (fillMaxSize) — correcto.
  - No se ejecutó `gradlew assembleDebug` (restricción global): la validación es estructural + referencias.
  - Se usa `error!!` en el branch `likedSongs.isEmpty()` siguiendo el brief (idéntico a otros patrones del proyecto).