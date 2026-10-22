# Informe: Limpieza del código muerto + Sistema PoToken (Flutter)

**Fecha:** 2026-10-15
**Proyecto:** `musicfritoFlutter/` (migración Flutter del reproductor FritoMusic, originalmente en Kotlin)
**Verificación:** `flutter analyze` → **0 errores** (solo quedan avisos/infos preexistentes, p. ej. `withOpacity` deprecado).

---

## 1. Qué se hizo en esta pasada

### 1.1 Eliminación de código muerto (4 archivos)

| Archivo eliminado | Motivo |
|---|---|
| `lib/data/network/lossless_client.dart` | Cliente Qobuz/FLAC sin conectar a ningún sitio (0 imports en todo el proyecto). |
| `lib/services/player_cache_holder.dart` | Huérfano: nadie lo usaba (la limpieza de caché se resolvía en `FritoAudioHandler`). |
| `lib/ui/screens/spotify_login_screen.dart` | Huérfano: la pantalla nunca se instanciaba desde ningún sitio. |
| `lib/ui/screens/stream_tutorial_screen.dart` | Huérfano: la pantalla nunca se instanciaba. |

Se verificó antes con búsquedas globales que **ningún archivo importaba** a estos cuatro. El `SpotifyLoginManager` (repositorio) **se conserva**: sí se usa desde `StreamProvider` para playlists de Spotify.

*Pendiente a futuro (no tocado a propósito):* el selector de calidad de descarga sigue siendo "etiqueta" (320/160/128/FLAC) sin efecto real en lo que se descarga.

### 1.2 Reparación de `AppConstants` (`lib/core/constants.dart`)

**Problema:** el archivo definía claves de SharedPreferences "para coincidir con Kotlin exactamente" (`frito_theme_prefs`, `youtube_login`…), pero **ningún archivo las usaba**; cada repositorio tenía sus literales mágicos. Además, esas claves eran inútiles en Flutter: el plugin `shared_preferences` guarda en su propio XML (`FlutterSharedPreferences`) con prefijo `flutter.` y **no puede leer** los XML que creaba la app Kotlin.

**Solución:** `AppConstants` reescrito como **fuente única de verdad** con las claves que la app Flutter **realmente usa**, y refactorizados **12 archivos** para que las usen:

- `lib/data/repository/`: `playlist_repository`, `favorites_repository`, `hidden_items_manager`, `favorite_artists_manager`, `custom_names_manager`, `stream_history_manager`, `youtube_login_manager`, `spotify_login_manager`
- `lib/providers/theme_provider.dart`
- `lib/services/download_service.dart` y `equalizer_service.dart` (canal `com.frito.music/platform`)
- `lib/ui/screens/appearance_screen.dart` y `youtube_login_screen.dart` (canal nativo)
- `lib/app.dart` (flag `has_completed_onboarding`) y `lib/main.dart` (canal de notificación de audio)

**Importante:** los **valores** de las claves no cambiaron → **no se pierde ningún dato** de los usuarios que ya usan la app Flutter.

### 1.3 Sistema PoToken real (fix del corte de música online a los 30-60 s)

**Problema:** `po_token_generator.dart` era un stub que devolvía `null`. Sin PoToken, los clientes móviles (IOS/ANDROID_VR) que usa la cascada de resolución devuelven URLs de googlevideo **de vida muy corta**: la canción suena 30-60 s y muere. La versión Kotlin original sí tenía PoToken (WebView + BotGuard) y la migración Flutter lo dejó sin portar.

**Solución (port fiel de `app/src/main/java/com/frito/music/utils/potoken/`):**

1. **`lib/utils/potoken/po_token_generator.dart` (reescrito completo):**
   - `PoTokenWebViewHost`: widget **oculto 1×1** que aloja un `WebViewController` (webview_flutter), montado en el `Stack` raíz de `app.dart`. Sin red propia: solo ejecuta JS.
   - Carga `assets/potoken/po_token.html` (que ya existía) e inyecta la señal `loaded`.
   - Peticiones JNN en Dart (Dio): `POST https://www.youtube.com/api/jnn/v1/Create` → BotGuard en el WebView → `POST /GenerateIT` → integrity token (con expiración y margen de 10 min, igual que Kotlin).
   - `parseChallengeData` (incluye descramble +97 por byte), `parseIntegrityTokenData`, `stringToU8`, `u8ToBase64` (URL-safe) portados de `JavaScriptUtil.kt`.
   - Reutiliza el minter entre videos; se recrea al expirar o fallar; un token por `videoId` + un token de sesión por `sessionId`. Si el WebView falla → devuelve `null` (la app sigue funcionando sin tokens, como antes).
2. **`innertube_client.dart`:** nuevo `ensureVisitorData()` para obtener un `visitorData` de invitado (necesario para vincular el PoToken cuando no hay sesión).
3. **`stream_client_utils.dart`:** nueva `appendPoToken(url, token)` → añade `?pot=` a las URLs web.
4. **`youtube_repository.dart`:** nuevo paso **WEB_REMIX + PoToken** en `getStreamUrl()`, **promovido antes de IOS/ANDROID_VR** (es la vía estable: URLs de horas vs. segundos). El token de jugador va en el request `player` y el de sesión se adjunta `?pot=` a la URL; la URL se valida antes de entregarla.
5. **`audio_handler.dart` (red de seguridad):** si el stream muere **a mitad de canción** (idle inesperado de just_audio), se resuelve una URL fresca, se invalida la caché y **se reanuda la posición exacta** (máx. 2 intentos por pista; luego salta a la siguiente). Compensado con `_stopRequested` para no disparar con el botón Detener.

---

## 2. Cómo probarlo (en dispositivo Android)

1. Compilar e instalar: `flutter run` (o build release).
2. Abrir la pestaña **Stream** y reproducir cualquier canción.
3. Verificar que **supera los 60 segundos y termina completa**.
4. En los logs se debe ver: `[PoToken] Inicializado; integrity token expira en ...s` la primera vez (tarda ~2-5 s la primera canción; después es instantáneo).
5. Si se corta aun así: `[FritoAudioHandler] Stream murió (idle inesperado); intentando recuperar...` → debe reanudarse solo.
6. Canción local (biblioteca) → debe seguir igual que antes (no se tocó nada del flujo local).

---

## 3. Qué falta por hacer (backlog)

1. **Selector de calidad de descarga real** (pendiente tuyo): hoy `DownloadService` siempre descarga la mejor URL disponible; el parámetro `quality` es solo etiqueta en `DownloadTask`.
2. **Prueba en dispositivo del PoToken**: es código sensible a cambios de YouTube; si BotGuard cambia, puede requerir ajustes (el diseño degrada a la cascada anterior sin romper nada).
3. **Migración de datos de la versión Kotlin → Flutter** (opcional): si quieres que usuarios que venían de la app Kotlin conserven favoritos/playlists, habría que crear un puente nativo que lea los XML antiguos (`FritoMusicPrefs`, `frito_*_prefs`…) y los vuelque a `shared_preferences` de Flutter.
4. **Limpieza de avisos `withOpacity` deprecados** (cosmético; hay cientos, todos preexistentes).
5. **Persistencia del "último cliente de stream exitoso"** (existía en Kotlin: `frito_stream_client` / `last_successful_client` y backoff de 10 min para clientes con 403): no está en Flutter y haría la resolución más rápida/resiliente.
6. **README del repo**: sigue casi vacío (`# FritoMusic`).

---

## 4. Archivos tocados (resumen)

**Eliminados:** `lossless_client.dart`, `player_cache_holder.dart`, `spotify_login_screen.dart`, `stream_tutorial_screen.dart`.

**Reescritos/editados:**
- `lib/core/constants.dart` (reescrito)
- `lib/utils/potoken/po_token_generator.dart` (reescrito completo)
- `lib/data/network/yt/youtube_repository.dart` (paso WEB_REMIX+PoToken + helper)
- `lib/data/network/yt/stream_client_utils.dart` (`appendPoToken`)
- `lib/data/network/innertube/innertube_client.dart` (`ensureVisitorData`)
- `lib/services/audio_handler.dart` (recuperación mid-playback)
- `lib/app.dart` (host PoToken + constantes), `lib/main.dart` (constantes)
- 8 repositories + `theme_provider` + `download_service` + `equalizer_service` + `appearance_screen` + `youtube_login_screen` (AppConstants)
