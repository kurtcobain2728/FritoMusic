# SDD Progress: Biblioteca Online

Task 1: complete (OnlineLibraryViewModel + liked songs, review clean; minors: like revert no sync _likedSongs, race on rapid toggles, getLikedPlaylists dup getUserPlaylists)
Task 2: complete (playlists CRUD, review clean; minors: loadOnlinePlaylists no reset onlineError, mensajes error similares)
Task 3: complete (FavoritesScreen tabs, review clean; minor: LaunchedEffect refetch each visit, error!! vs ?:, key dup risk)
Task 4: complete (PlaylistsScreen tabs, review clean tras fix indentación BackHandler; minor: weight(1f) redundante)
Task 5: complete (OnlinePlaylistDetailScreen, fix page.playlist.title + quitar scope sin uso, re-review Approved)
Task 6: complete (heart contexto-aware, review Approved)
Task 7: complete (refresco ON_RESUME, review Approved)
MINORS ACUMULADOS (para triage en revisión final): T1 like revert no sincroniza _likedSongs; T1 carrera en toggles rapidos; T1 getLikedPlaylists duplica getUserPlaylists; T2 loadOnlinePlaylists no resetea onlineError; T4 weight(1f) redundante en online content; T6 estado heart inline no hoisted; T7 observer ON_RESUME añadido aunque no logueado
Final review: Ready to compile. Fixes aplicados: T1 likeSong sincroniza _likedSongs; T7 observer ON_RESUME gateado por isLoggedIn.
