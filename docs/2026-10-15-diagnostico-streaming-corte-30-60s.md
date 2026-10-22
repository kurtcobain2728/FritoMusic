# Diagnóstico: reproducción en stream corta a los 30–60 s y canciones que no arrancan

**Fecha:** 2026-10-15
**Proyecto:** `musicfritoFlutter/` (migración desde Kotlin `app/`)
**Síntomas reportados por el usuario:**
1. La canción arranca (a veces tarda un poco en cargar) pero **deja de sonar a los 30–60 segundos**; no hay error visible, y **adelantar o retroceder no ayuda**.
2. Algunas canciones **no reproducen ni una fracción de segundo** (ej: *Lost — Bring Me The Horizon*).
3. Reinstalar la app no cambia nada.

**Veredicto: REPARAR, no reconstruir.** La arquitectura (cascada de resolución → proxy local → `audio_handler` con recuperación) es correcta y está validada empíricamente en su mayoría. Hay bugs concretos y un puñado de fallbacks muertos que explican los síntomas; el corte a 30–60 s necesita **una sesión de logs en el dispositivo (Fase 0)** para confirmar entre 3 hipótesis H1/H2/H3 — todas con correcciones definidas en este documento. Reconstruir desde cero perdería lo que ya funciona y no aportaría información que la Fase 0 no dé.

---

## 1. Evidencia empírica recogida (2026-10-15, ejecutada desde PC, red doméstica)

| # | Prueba | Resultado |
|---|--------|-----------|
| E1 | `GET` a googlevideo **sin header `Range`** (cliente IOS, UA de YouTube iOS) | **~31–33 KB/s sostenido** (throttle) |
| E2 | `GET` a googlevideo **con `Range: bytes=0-`** | **7.7 MB/s** |
| E3 | `GET` con rango fijo (como hace el proxy: `bytes=X-Y`) | 6.6–8.4 MB/s |
| E4 | 36 peticiones con `Range` secuenciales sobre la misma URL (≈3.4 MB, archivo completo) | **Sin degradación ni 403**: 1.3–8.4 MB/s en todas las que cabían en el archivo (los 416 del test fueron por pedir bytes más allá del fin del archivo = bug del test, no de YouTube) |
| E5 | Duración de URLs `IOS`/`ANDROID_VR` | `expiresInSeconds=21540` ≈ **6 horas** (NO expiran en segundos — teoría anterior descartada) |
| E6 | `WEB_REMIX` **sin PoToken** (video normal) | `UNPLAYABLE` |
| E7 | `TVHTML5_SIMPLY_EMBEDDED_PLAYER` | `ERROR: "YouTube ya no es compatible con esta aplicación o dispositivo"` → **cliente muerto** |
| E8 | *LosT — BMTH* (video oficial, id `xv-70a6yXfM`) | **Restringido por edad**: IOS/ANDROID_VR = `LOGIN_REQUIRED ("puede ser inadecuado para algunos usuarios")`; WEB_REMIX sin pot = `LOGIN_REQUIRED ("Inicia sesión y confirma tu edad")`; embed = `ERROR` → **toda la cascada falla** |
| E9 | 5 instancias Piped + 3 Invidious (las de la cascada) | **TODAS MUERTAS** (526/502/sin respuesta/401) |
| E10 | JioSaavn (URL `_96.mp4` y `_320.mp4` descifradas) | Ambas `HTTP 200` (2.8 MB / 8.5 MB); descarga completa a **4745 KB/s**; EOF normal al final → **Saavn no corta** |
| E11 | Parámetros de la URL de googlevideo | Trae `clen` (⇒ el proxy SIEMPRE usa su rama *chunked* con `Range` upstream) y `mime=audio%2Fwebm` (⇒ el content-type del proxy se resuelve bien) |
| E12 | Una URL `IOS` de otro video (`Z4rQCmfQ1r0`) | Devolvió **403 al fetchearla** pese a `playabilityStatus=OK` |

**Cómo reproducir E1/E2 (PowerShell):** obtener URL vía `POST youtubei/v1/player` (client IOS, headers `X-YouTube-Client-Name: 5`, UA `com.google.ios.youtube/21.03.1 ...`) y luego `curl.exe -A <ua> -o NUL --max-time 10 -w "%{speed_download}" <url>` con y sin `-H "Range: bytes=0-"`.

---

## 2. Hallazgos confirmados en el código (bugs/defectos)

### 2.1 Canciones que "no suenan nada" — *LosT* (confirmado E8)
Cascada completa (`youtube_repository.dart:131-367`) falla para contenido con restricción de edad, y después:
- `audio_handler.dart:196` lanza `StateError('No se pudo obtener el audio de este tema')`;
- `audio_handler.dart:263-265` lo captura y solo hace `debugPrint` → **el usuario ve silencio total, sin ningún mensaje**.

### 2.2 Los pasos 5 y 6 de la cascada están muertos (E9)
- `youtube_repository.dart:305-336` → 5 instancias Piped **todas muertas**.
- `youtube_repository.dart:338-364` → 3 instancias Invidious **todas muertas** (además este paso **no valida** la URL con `isStreamUrlPlayable`, a diferencia del paso Piped).
- Coste: hasta 8 × `receiveTimeout: 6 s` de espera en cada canción que falla antes de devolver `null`.

### 2.3 El paso 4 del fallback (TVHTML5 embed) está muerto (E7)
`youtube_repository.dart:257-293` → `ERROR` siempre. Solo añade latencia.

### 2.4 Paso 2 (WEB_REMIX+PoToken) se ejecuta aunque no haya PoToken
`youtube_repository.dart:72-99`: si `getWebClientPoToken` devuelve `null`, igual se llama `getPlayer` → sin `pot` el cliente devuelve `UNPLAYABLE` (E6) → request inútil. **Además:** el comentario en `youtube_repository.dart:148-152` dice *"IOS/ANDROID_VR sin PoToken expiran en segundos/minutos y cortan a los 30-60 segundos"* — **eso es FALSO según E5** (durán 6 h); hay que corregirlo para no confundir al siguiente agente.

### 2.5 Solo 2 de 7 rutas validan la URL antes de entregarla
`isStreamUrlPlayable` (`youtube_repository.dart:43-64`, sonda `Range: bytes=0-0`) se usa solo en WEB_REMIX (`:96`) y Piped (`:326`). **IOS (`:212-216`), ANDROID_VR (`:250-254`), TVHTML5 (`:288-292`), fallback web (`:297-302`) e Invidious (`:354-359`) entregan URLs sin validar** → una URL 403 (E12) llega al reproductor.

### 2.6 `getProxyUrl` puede entregar la URL directa de googlevideo (carrera)
`local_stream_proxy.dart:47-50`:
```dart
if (_server == null) {
  ensureStarted();      // fire-and-forget, asincrónico
  return originalUrl;   // ← URL DIRECTA a googlevideo
}
```
Si el bind falla o se llama antes de que el servidor esté listo, ExoPlayer habla **directo con googlevideo sin `Range` en la primera petición** → **throttle de ~31 KB/s (E1)** → exactamente un síntoma de "arranca lento y se queda sin datos".

### 2.7 El proxy falla "limpio" en vez de con error (rompe la recuperación)
`local_stream_proxy.dart:174-178` (`break` ante status != 200/206) y `:185-189` (`catch` → `response.close()`):
- Cierra la respuesta **con menos bytes que el `Content-Length` prometido** sin log de bytes servidos;
- El comportamiento de ExoPlayer ante EOF prematuro no está garantizado como `ProcessingState.idle`, y **nuestro watchdog de recuperación SOLO se dispara en `idle`** (`audio_handler.dart:79-87`) → si el estado queda en `buffering`, **no hay recuperación y hay silencio eterno**.
- No hay reintento de chunk ni log de velocidad por chunk → imposible diagnosticar a distancia.

### 2.8 No hay watchdog de "stall" (buffers que no avanzan)
Si la entrega efectiva es lenta (throttle) **no hay error**: el estado queda `buffering`/`ready` con `position` congelado. `audio_handler.dart:79-87` solo reacciona a `idle` → **el síntoma exacto del usuario queda invisible para la lógica de recuperación**.

### 2.9 Recovery: `wasPlaying` puede ser falso tras un error
`audio_handler.dart:95` lee `_player.playing` **después** de la muerte del stream; si just_audio pone `playing=false` al error, la recuperación reanuda **en pausa** (silencio a pesar de "haber recuperado"). Debe trackearse la intención del usuario (`_userWantedPlaying`).

### 2.10 Posible carrera `playAudio` vs watchdog de `idle`
`audio_handler.dart:79-87` puede dispararse mientras `playAudio` aún está dentro de `setAudioSource` (evento `idle` transitorio con `audioSource != null`) → doble `setAudioSource` concurrente. Falta un flag `_isSettingSource`.

### 2.11 Errores silenciosos en la UI
Todos los `catch (_) {}` de `getStreamUrl` (`youtube_repository.dart:143,159,217,255,293,303,334,362`) + el catch final del handler → **cero feedback al usuario**.

---

## 3. Hipótesis del corte a los 30–60 s (H1/H2/H3)

> Los 512 KB de cada chunk del proxy ≈ **30–64 s de audio** a los bitrates reales (136–320 kbps) — la duración del síntoma coincide sospechosamente con 1–2 chunks. La resolución de URLs, en cambio, está validada (E1–E5): URLs de 6 h y descarga con `Range` rápida desde esta red.

| | Hipótesis | Mecanismo | Discriminador (log) |
|---|---|---|---|
| **H1** | **Throttle de red en el teléfono** (operador/ruta/DPI hacia googlevideo, o primer request sin `Range` por bypass del proxy §2.6) | Entrega < bitrate → `buffering` eterno, sin error → watchdog de `idle` no dispara (§2.8) → silencio; `seek` tampoco ayuda si todo llega lento | `[Proxy] chunk ... = 20-40 KB/s` sostenido, **sin status de error** |
| **H2** | **Chunk upstream falla** (403/416/timeout) a partir del 1º–2º bloque ≈ 30–60 s | `break` silencioso (§2.7) → EOF prematuro → estado ambiguo, recuperación no garantizada | `[Proxy] Upstream error ... para bytes=524288-...` (ya existe el log de status, faltan bytes/servidos) |
| **H3** | **Proxy no usado**: `getProxyUrl` devuelve URL directa (§2.6) o el bind falló | ExoPlayer directo sin `Range` → 31 KB/s (E1) → mismo desenlace que H1 | **Ausencia total de logs `[Proxy]` durante la reproducción** + log de `getProxyUrl` devolviendo host externo |

**Ninguna hipótesis se puede confirmar sin la Fase 0.** Todas tienen corrección definida abajo.

---

## 4. Plan de reparación paso a paso (para otra IA)

> Orden obligatorio: **Fase 0 primero (instrumentación)** — si se "arregla" a ciegas no se sabrá si se curó. Ejecutar `flutter analyze` tras cada fase (debe quedar en 0 errores).

### Fase 0 — Instrumentación diagnóstica (sin cambiar lógica)

**0.1 Proxy: log de cada petición del cliente y de cada chunk** — `lib/services/local_stream_proxy.dart`
- En `_handleIncomingRequest`, junto a `clientRange` (línea ~101):
  ```dart
  debugPrint('[Proxy] petición cliente Range=${clientRange ?? "(SIN RANGE)"} '
      'clen=$clen urlHost=${targetUri.host}');
  ```
- Reemplazar `await request.response.addStream(upstreamResp);` (línea ~180) por lectura contada:
  ```dart
  final sw = Stopwatch()..start();
  int chunkBytes = 0;
  try {
    await for (final data in upstreamResp) {
      chunkBytes += data.length;
      request.response.add(data);
    }
    debugPrint('[Proxy] chunk $chunkRange → $chunkBytes B en '
        '${sw.elapsedMilliseconds} ms (${(chunkBytes / 1024 / (sw.elapsedMilliseconds / 1000)).toStringAsFixed(0)} KB/s)');
  } catch (e) {
    debugPrint('[Proxy] chunk $chunkRange FALLO tras $chunkBytes B: $e');
    rethrow; // ver 0.4
  }
  ```
- Log final: `debugPrint('[Proxy] FIN esperado=${totalToServe}B servidos=$servedB')` (sumar `chunkBytes`).

**0.2 audio_handler: log de estado + watchdog de stall (temporal, 5 s)** — `lib/services/audio_handler.dart`
```dart
DateTime? _lastPosTime; Duration? _lastPos; int _stallTicks = 0;
// en _init():
Timer.periodic(const Duration(seconds: 5), (_) async {
  if (_player.audioSource == null || currentAudio == null) return;
  debugPrint('[Watchdog] pos=${_player.position} buffered=${_player.bufferedPosition} '
      'state=${_player.processingState} playing=${_player.playing}');
  final pos = _player.position;
  if (_player.playing && _lastPos != null && pos == _lastPos &&
      _player.bufferedPosition - pos < const Duration(seconds: 5)) {
    if (++_stallTicks >= 2) {
      _stallTicks = 0;
      debugPrint('[Watchdog] STALL detectado → recuperar');
      // en Fase 0: SOLO loguear. En Fase 2: _recoverFromStreamError();
    }
  } else { _stallTicks = 0; }
  _lastPos = pos;
});
```
(Sin `Timer` en scope: `import 'dart:async';`.)

**0.3 Log del camino de resolución por canción** — `youtube_repository.dart`
- Al inicio de `getStreamUrl`: `debugPrint('[StreamResolve] $videoId → buscando (título=$title, artista=$artist)')`.
- En cada `return` de éxito: `debugPrint('[StreamResolve] $videoId OK vía $fuente (Saavn|WEB_REMIX+pot|IOS|ANDROID_VR|TVHTML5|web|Piped|Invidious)')`.
- En `getProxyUrl`: loguear si devuelve proxy o URL original (detecta H3, §2.6).

**0.4 Log de eventos de PoToken** — `po_token_generator.dart`: asegurar `debugPrint` con `[PoToken] OK (minter=..., expira=...s)` / `[PoToken] FAIL: <razón>` en **todos** los caminos de error (hoy varios `catch (_) {}`).

**0.5 Captura en el dispositivo**
```
flutter run --release   (o debug)
adb logcat -s flutter | findstr /i "Proxy Watchdog StreamResolve PoToken FritoAudioHandler"
```
Reproducir: (a) una canción normal 2 min, (b) adelantar 1 min, (c) *LosT* de BMTH. Guardar la salida.

**0.6 Decidir según logs:**
- `[Proxy] chunk ... 20-40 KB/s` sin errores → **H1** → aplicar Fases 1+2 (prioridad: PoToken real, §Fase 2) y medir de nuevo; si persiste, diagnosticar red del teléfono (probar otra WiFi/datos, VPN) — es throttle de operador hacia googlevideo.
- `[Proxy] Upstream error` / `[Proxy] chunk FALLO` → **H2** → Fase 1 §1.4–1.5.
- Cero logs `[Proxy]` durante playback → **H3** → Fase 1 §1.6.

### Fase 1 — Correcciones justificadas ya por la evidencia

**1.1 Podar la cascada muerta** — `youtube_repository.dart`
- Eliminar (o comentar con `// TODO: lista dinámica de instancias vivas`) los pasos Piped (`:305-336`) e Invidious (`:338-364`): E9.
- Eliminar TVHTML5_SIMPLY_EMBEDDED (`:257-293`): E7. Deben quedar: Saavn → WEB_REMIX+PoToken → IOS → ANDROID_VR → web fallback.
- Corregir el comentario falso de `:148-152` (§2.4).

**1.2 SKIP del paso WEB_REMIX si no hay PoToken** — `youtube_repository.dart:72-99`:
```dart
final poToken = await PoTokenGenerator.instance.getWebClientPoToken(videoId, sessionId);
if (poToken == null) {
  debugPrint('[StreamResolve] sin PoToken → se omite WEB_REMIX');
  return null;   // sin pot es UNPLAYABLE (E6): no gastar el request
}
```

**1.3 Validar TODAS las URLs antes de entregarlas** — envolver el `url` de IOS/ANDROID_VR/fallback web con:
```dart
if (url != null && await isStreamUrlPlayable(url)) { /* cachear y return */ }
```
(Evita E12: URLs `playability=OK` que dan 403 al usarlas. Coste ~300 ms.)

**1.4 Proxy: reintentar chunk y fallar RUIDOSO** — `local_stream_proxy.dart:162-184`
- Reintentar cada chunk **1 vez** con un `HttpClient` nuevo (fuerza conexión nueva):
  ```dart
  Future<HttpClientResponse> _openChunk(Uri u, String range, StreamRequestProfile p, {String? cookie}) // helper con reintento
  ```
- Si tras el reintento sigue fallando: **no** `close()` limpio — abortar el socket para que ExoPlayer vea un error y dispare la recuperación:
  ```dart
  final socket = await request.response.detachSocket(writeHeaders: false);
  socket.destroy();   // en vez de response.close() con Content-Length incumplido
  ```
- Registrar bytes servidos reales en el log (0.1) para saber a qué byte absoluto murió.

**1.5 `getProxyUrl` sin carrera** — `local_stream_proxy.dart:41-55`:
- Cambiar a `Future<String> getProxyUrlAsync(String url)` con `await ensureStarted()` **dentro**; si `_server == null` tras el intento → lanzar excepción (`StateError('proxy no disponible'`) en vez de devolver la URL directa (cierra H3).
- Actualizar los 3 llamadores (`audio_handler.dart:132`, `:209`, `:239`).

**1.6 Errores visibles en UI** — `audio_handler.dart:263-265` + `youtube_repository.dart`
- Añadir un `ValueNotifier<String?> ultimoErrorStream` (p.ej. en un `ErrorBus` singleton o en el handler) con mensajes mapeados:
  - `getStreamUrl == null` → `"No se pudo obtener el audio (¿contenido con restricción de edad? Prueba iniciando sesión en YouTube)"`;
  - `LOGIN_REQUIRED` detectado en cascada → mensaje específico.
- `app.dart` o la pantalla de reproducción: listener que muestre `SnackBar`.

**1.7 Corregir `wasPlaying` del recovery** — `audio_handler.dart:95`: trackear intención:
```dart
bool _userWantedPlaying = false; // =true en play()/playAudio, =false en pause()
// en _recoverFromStreamError: if (_userWantedPlaying) await _player.play();
```

**1.8 Guard de carrera en `setAudioSource`** — `audio_handler.dart`: flag `_isSettingSource`; el listener de `idle` (`:79-87`) lo salta mientras sea `true` (evita doble `setAudioSource`).

### Fase 2 — PoToken + watchdog de stall (el antídoto de H1)

**2.1 Watchdog de stall operativo** — reutilizar 0.2, pero llamando `_recoverFromStreamError()` al detectar 2 ticks de stall (ya con los guards de §1.7/1.8). Es la red de seguridad que hoy falta para "silencio sin error".

**2.2 Robustecer el WebView del PoToken** — `po_token_generator.dart` / montaje en `app.dart`
- Reemplazar el `Visibility(visible:false, maintainSize...)` por **posición fuera de pantalla** (algunos Android no ejecutan JS/timers en views invisibles):
  ```dart
  Positioned(left: -2000, top: -2000, width: 10, height: 10, child: WebViewWidget(controller: _controller))
  ```
- Prewarm al arrancar (`main.dart` o `app.dart` initState): `unawaited(PoTokenGenerator.instance.prewarm());` para que el primer play no espere 2–5 s.
- Todo fallo debe loguearse con `[PoToken] FAIL: <excepción>` (0.4).

**2.3 Verificar en dispositivo** que `[PoToken] OK` aparece y que el paso `[StreamResolve] ... OK vía WEB_REMIX+pot` se usa. Si WEB_REMIX sigue `UNPLAYABLE` con pot: loguear el `playabilityStatus` crudo completo y revisar `signatureTimestamp`/`visitorData`/cookies de sesión.

**2.4 (Opcional) Mitigación de bitrate**: si H1 persiste con PoToken OK, considerar en `_extractBestAudioUrl` (`youtube_repository.dart:116`) elegir el format ≤192 kbps en vez del máximo (menor consumo = tolera throttle mejor). *Requiere visto bueno del usuario: afecta calidad.*

### Fase 3 — Verificación (criterios de aceptación)

- [ ] Canción normal de YouTube **reproduce ≥ 5 min seguidos** y termina.
- [ ] Adelantar 2 min funciona (vuelve a sonar en < 3 s).
- [ ] Logs muestran `[StreamResolve] OK vía ...` con fuente conocida y `[Proxy] chunk ... = >500 KB/s`.
- [ ] *LosT* (edad restringida): o reproduce (si sesión logueada con edad confirmada), **o muestra SnackBar con el motivo** — nunca silencio.
- [ ] Canción con fuente caída: `[Watchdog] STALL → recuperar` reanuda automáticamente.
- [ ] `flutter analyze` → 0 errores.
- [ ] Canciones vía Saavn reproducen completas (regresión, E10).

### Comandos útiles de verificación (PC)
```powershell
# ¿Sigue vivo el throttle sin Range? (E1/E2)
curl.exe -A <ua> -o NUL --max-time 10 -w "%{speed_download}" <url>          # ~31 KB/s = throttle
curl.exe -A <ua> -H "Range: bytes=0-" -o NUL --max-time 10 -w "%{speed_download}" <url>   # MB/s = ok
# Vida de la URL
#  → parámetro `expire` en la query de googlevideo vs Get-Date (debe dar ~6 h)
```

---

## 5. Backlog relacionado (NO tocado a propósito)
- Selector real de calidad en descargas (`DownloadService` es solo etiqueta) — pendiente de orden del usuario.
- Migración de datos Kotlin (`frito_theme_prefs` XML) → prefs Flutter (puente).
- Limpieza de `withOpacity` (info de analyze).
- Persistir último cliente de stream exitoso + backoff 403 (existía en Kotlin: `frito_stream_client`/`last_successful_client`).
- README casi vacío.

## 6. Plan B (solo si Fase 0+1+2 no lo cierran)
Reconstruir **únicamente la capa de transporte** (no la app): sustituir el par (proxy Dart + `just_audio` HTTP) por **`cronet_http`** (Cronet/QUIC nativo de Android, como hace la app oficial de YouTube — evita por completo el throttle HTTP/1.1 y los problemas de `Range`), manteniendo la cascada de resolución ya validada. Es un cambio contained en `audio_handler.dart` + `local_stream_proxy.dart` y requiere `flutter pub add cronet_http` + permiso de red (ya existe).
