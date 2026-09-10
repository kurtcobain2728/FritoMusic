package com.frito.music.data.network.yt

import com.frito.music.data.models.StreamableTrack
import com.frito.music.utils.potoken.PoTokenGenerator
import com.frito.music.utils.potoken.PoTokenResult
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object YouTubeRepository {

    private val poTokenGenerator = PoTokenGenerator()

    private data class CachedUrl(val url: String, val timestamp: Long)
    private val streamUrlCache = ConcurrentHashMap<String, CachedUrl>()
    private const val STREAM_URL_TTL_MS = 4 * 60 * 60 * 1000L

    /**
     * signatureTimestamp global de la sesión (viene del player JS de YouTube y
     * cambia con poca frecuencia). Los clientes con useSignatureTimestamp lo
     * requieren en /player o devuelven 403.
     */
    @Volatile
    private var signatureTimestampCache: Int? = null

    private fun getSignatureTimestamp(videoId: String): Int? {
        signatureTimestampCache?.let { return it }
        return try {
            NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()?.also {
                signatureTimestampCache = it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCachedStreamUrl(videoId: String): String? {
        val entry = streamUrlCache[videoId] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < STREAM_URL_TTL_MS) entry.url
        else { streamUrlCache.remove(videoId); null }
    }

    /**
     * Invalida la URL cacheada de un video. Se llama cuando ExoPlayer da un
     * error de playback (p. ej. HTTP 403): la URL guardada puede estar
     * "envenenada" (bloqueada por bot-detection) y no debe reutilizarse.
     */
    fun invalidateStreamUrl(videoId: String) {
        streamUrlCache.remove(videoId)
    }

    /**
     * Comprueba que una URL de stream es realmente reproducible desde este
     * dispositivo. Usa el PERFIL REAL del cliente que emitió la URL
     * (User-Agent + Origin + Referer) y sondas Range (1, o 3 en TV/Web) —
     * exactamente como lo hará el reproductor. Detecta los 403 de
     * bot-detection ANTES de entregar la URL a ExoPlayer.
     */
    suspend fun isStreamUrlPlayable(url: String): Boolean = withContext(Dispatchers.IO) {
        val host = runCatching { java.net.URL(url).host }.getOrNull().orEmpty()
        val isYouTubeHost = StreamClientUtils.isYouTubeMediaHost(host)
        val profile = StreamClientUtils.resolveRequestProfile(url)
        val ranges = if (isYouTubeHost && profile.requiresPlaybackProbeRanges) {
            listOf("bytes=0-0", "bytes=262144-262145", "bytes=1048576-1048577")
        } else {
            listOf("bytes=0-0")
        }
        ranges.all { range ->
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Range", range)
                // Mismo criterio que el interceptor del reproductor: solo los
                // hosts de YouTube reciben el perfil (UA/Origin/Referer). Las
                // fuentes alternativas (JioSaavn/Qobuz) se validan tal cual se
                // van a reproducir, sin headers de YouTube.
                if (isYouTubeHost) {
                    profile.headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
                }
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                val code = conn.responseCode
                runCatching { conn.inputStream?.close() }
                conn.disconnect()
                android.util.Log.d(
                    "YouTubeRepository",
                    "  validación host=$host client=${if (isYouTubeHost) profile.requestedClientName else "n/a"} range=$range -> HTTP $code"
                )
                code in 200..399 || code == 416
            } catch (e: Exception) {
                android.util.Log.d("YouTubeRepository", "  validación excepción: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
    }

    // ─── Resolución de streams estilo FridaMusic ───
    // Cliente preferido: Android VR sin auth (no requiere PoToken ni login).
    private val PREFERRED_STREAM_CLIENT: YouTubeClient = ANDROID_VR_NO_AUTH

    // Pool de fallback (mismo orden que FridaMusic, adaptado a los clientes
    // disponibles en este innertube). Se itera SECUENCIALMENTE con validación
    // real de cada URL antes de entregarla al reproductor.
    private val STREAM_FALLBACK_CLIENTS: List<YouTubeClient> = listOf(
        IOS, MOBILE, ANDROID_MUSIC, IOS_MUSIC, ANDROID_VR_NO_AUTH, ANDROID_VR_1_61_48,
        ANDROID_VR_1_43_32, ANDROID_CREATOR, ANDROID_TESTSUITE, ANDROID_UNPLUGGED,
        IPADOS, VISIONOS, TVHTML5, TVHTML5_SIMPLY_EMBEDDED_PLAYER, WEB, WEB_CREATOR, WEB_REMIX
    )

    /** Backoff por (videoId, cliente) tras un HTTP 403: 10 minutos. */
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()

    /** Último cliente que produjo un stream reproducible (se prueba primero). */
    @Volatile
    private var lastSuccessfulClientKey: String? = null

    /**
     * Marca un cliente como fallido (403) para ese video durante 10 minutos.
     * Se llama desde el manejador de errores del reproductor.
     */
    fun markStreamClientFailed(videoId: String, clientKey: String?, httpStatusCode: Int?) {
        if (httpStatusCode != 403) return
        val normalized = StreamClientUtils.normalizeClientKey(clientKey)
        if (normalized.isEmpty()) return
        failedStreamClientsUntil["$videoId:$normalized"] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    private fun isClientTemporarilyBlocked(videoId: String, client: YouTubeClient): Boolean {
        val keys = listOf(client.clientName, StreamClientUtils.buildClientKey(client))
        return keys.any { key ->
            val mapKey = "$videoId:${StreamClientUtils.normalizeClientKey(key)}"
            val until = failedStreamClientsUntil[mapKey] ?: return@any false
            if (until <= System.currentTimeMillis()) {
                failedStreamClientsUntil.remove(mapKey)
                false
            } else {
                true
            }
        }
    }

    private fun signatureTimestampFor(client: YouTubeClient, videoId: String): Int? {
        if (!client.useSignatureTimestamp) return null
        return getSignatureTimestamp(videoId)
    }

    private suspend fun poTokenFor(client: YouTubeClient, videoId: String): PoTokenResult? {
        if (!client.useWebPoTokens) return null
        val sessionId = YouTube.dataSyncId ?: YouTube.visitorData ?: return null
        return runCatching { poTokenGenerator.getWebClientPoToken(videoId, sessionId) }.getOrNull()
    }

    /** Ordena formatos de audio: URL directa > bitrate > códec (opus>aac) > sample rate. */
    private fun selectAudioFormatCandidates(
        response: PlayerResponse,
    ): List<PlayerResponse.StreamingData.Format> =
        response.streamingData?.adaptiveFormats
            ?.asSequence()
            ?.filter { it.mimeType.startsWith("audio/") && it.bitrate > 0 }
            ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            ?.sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                    .thenByDescending { it.bitrate }
                    .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                    .thenByDescending { it.audioSampleRate ?: 0 }
            )
            ?.toList()
            .orEmpty()

    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
    ): String? {
        // Igual que FridaMusic: si el extractor (con desobfuscación n-sig) no
        // devuelve URL, usar la URL DIRECTA del formato como respaldo.
        // Sin este respaldo, un fallo de desobfuscación descartaba formatos
        // válidos y la canción no reproducía.
        val url = NewPipeExtractor.getStreamUrl(format, videoId)
            ?: format.url
            ?: return null
        return StreamClientUtils.patchClientVersion(url, client.clientVersion)
    }

    private fun extractCodec(mimeType: String): String? =
        Regex("""codecs="([^"]+)"""")
            .find(mimeType)
            ?.groupValues
            ?.getOrNull(1)
            ?.substringBefore(',')
            ?.trim()

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }

    /** Descarta formatos de vista previa (duración muy inferior a la esperada). */
    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approximate = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approximate in 1L..minOf(90_000L, expectedDurationMs * 9L / 10L)
    }

    /**
     * Descarta previews/samples cortos (p. ej. los ~7 s que YouTube devuelve
     * para contenido con restricción de edad): compara la duración real de la
     * URL (dur) o su tamaño (clen) contra la duración esperada del video. Sin
     * esto, una preview pasa la validación de 1 byte, suena unos segundos y
     * luego revienta con 416/EOF.
     */
    private fun isPreviewByLength(
        url: String,
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long?,
    ): Boolean {
        if (expectedDurationMs == null || expectedDurationMs < 90_000L) return false
        val threshold = minOf(90_000L, expectedDurationMs * 9L / 10L)
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        val urlDurationSeconds = uri.getQueryParameter("dur")?.toDoubleOrNull()
        if (urlDurationSeconds != null && urlDurationSeconds > 0) {
            return urlDurationSeconds * 1000.0 < threshold
        }
        val contentLength = uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 } ?: return false
        if (format.bitrate <= 0) return false
        val impliedDurationMs = contentLength * 8000L / format.bitrate
        return impliedDurationMs < threshold
    }

    suspend fun search(query: String): Result<List<StreamableTrack>> = runCatching {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
        result.getOrThrow().items.filterIsInstance<SongItem>().map { song ->
            StreamableTrack(
                videoId = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                album = song.album?.name,
                durationMs = song.duration?.times(1000L) ?: 0L,
                thumbnailUrl = song.thumbnail
            )
        }
    }

    suspend fun searchArtists(query: String): Result<List<ArtistItem>> = runCatching {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST)
        result.getOrThrow().items.filterIsInstance<ArtistItem>()
    }

    suspend fun getArtistDetails(browseId: String): Result<ArtistPage> = runCatching {
        YouTube.artist(browseId).getOrThrow()
    }

    suspend fun getAlbumDetails(browseId: String): Result<AlbumPage> = runCatching {
        YouTube.album(browseId).getOrThrow()
    }

    suspend fun getHome(): Result<HomePage> = runCatching {
        YouTube.home().getOrThrow()
    }

    suspend fun getExplore(): Result<ExplorePage> = runCatching {
        YouTube.explore().getOrThrow()
    }

    /**
     * Historial de reproducción de la cuenta (requiere sesión).
     * Devuelve las canciones escuchadas recientemente, aplanadas y sin duplicados.
     * Es la base de la sección "Escuchado recientemente" del home de Stream.
     */
    suspend fun getMusicHistory(): Result<List<SongItem>> = runCatching {
        val page = YouTube.musicHistory().getOrThrow()
        page.sections.orEmpty()
            .flatMap { it.songs }
            .distinctBy { it.id }
    }

    /**
     * Identifica la entrada "Música que te gustó" / "Liked songs" dentro de la
     * librería. Es una playlist AUTOGENERADA (no una playlist del usuario).
     *
     * Señales (cualquiera de ellas vale):
     *  - id empieza por "RDCLAK5uy" (prefijo estándar de la playlist de likes)
     *  - id == "FEmusic_liked_songs"
     *  - título contiene "gustó" / "liked" / "likes"
     *  - subtítulo dice "Playlist autogenerada" / "Auto-generated playlist"
     */
    private fun isLikedSongsEntry(item: PlaylistItem): Boolean {
        val byId = item.id.startsWith("RDCLAK5uy") || item.id == "FEmusic_liked_songs"
        val byTitle = item.title.contains("gustó", ignoreCase = true) ||
            item.title.contains("liked", ignoreCase = true) ||
            item.title.contains("likes", ignoreCase = true)
        val bySubtitle = item.songCountText?.contains("autogenerada", ignoreCase = true) == true ||
            item.songCountText?.contains("auto-generated", ignoreCase = true) == true
        return byId || byTitle || bySubtitle
    }

    /**
     * Canciones "Me gusta" de la cuenta de YouTube Music.
     *
     * NO usamos el browseId "FEmusic_liked_songs" directamente: con el cliente
     * WEB_REMIX devuelve HTTP 400 INVALID_ARGUMENT. En su lugar localizamos la
     * entrada "Música que te gustó" dentro de la librería (FEmusic_liked_playlists)
     * y abrimos su playlistId real con YouTube.playlist(), que sí funciona.
     */
    suspend fun getLikedSongs(): Result<List<SongItem>> = runCatching {
        val page = YouTube.library("FEmusic_liked_playlists").getOrThrow()
        val playlistItems = page.items.filterIsInstance<PlaylistItem>()
        android.util.Log.d("FritoLikes", "Entradas de librería: ${playlistItems.map { "id=${it.id} | titulo=${it.title} | sub=${it.songCountText}" }}")

        val likedEntry = playlistItems.firstOrNull { isLikedSongsEntry(it) }
        android.util.Log.d("FritoLikes", "Entrada de likes detectada: ${likedEntry?.id} (play=${likedEntry?.playEndpoint?.playlistId}, shuffle=${likedEntry?.shuffleEndpoint?.playlistId})")

        // Cadena de fallback para obtener el playlistId real de los likes
        val likedPlaylistId = likedEntry?.playEndpoint?.playlistId
            ?: likedEntry?.shuffleEndpoint?.playlistId
            ?: likedEntry?.id?.takeIf { !it.startsWith("FEmusic_") }
        if (likedPlaylistId.isNullOrEmpty()) {
            throw Exception("No se encontró tu lista de Me gusta")
        }
        android.util.Log.d("FritoLikes", "Abriendo playlist de likes: $likedPlaylistId")
        YouTube.playlist(likedPlaylistId).getOrThrow().songs
    }

    /**
     * Playlists guardadas de la cuenta de YouTube Music.
     * Se excluye la entrada "Música que te gustó" (autogenerada): no es una
     * playlist real, son los favoritos y tienen su propia sección.
     */
    suspend fun getLikedPlaylists(): Result<List<PlaylistItem>> = runCatching {
        val page = YouTube.library("FEmusic_liked_playlists").getOrThrow()
        page.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { isLikedSongsEntry(it) }
    }

    /**
     * Resolución secuencial estilo FridaMusic:
     *  · Empieza por el último cliente exitoso (memoria de sesión).
     *  · Salta clientes con backoff activo (403 reciente para ese video).
     *  · PoToken y signatureTimestamp POR CLIENTE.
     *  · Selección de formato: URL directa > bitrate > códec, sin previews.
     *  · Valida CADA URL con su perfil real (UA/Origin/Referer) antes de
     *    entregarla al reproductor — así nunca llega una URL muerta.
     */
    private suspend fun resolveRemote(videoId: String): String? {
        val isLoggedIn = !YouTube.cookie.isNullOrBlank()
        val ordered = if (isLoggedIn) {
            STREAM_FALLBACK_CLIENTS.filter { it.loginSupported } +
                STREAM_FALLBACK_CLIENTS.filterNot { it.loginSupported }
        } else {
            STREAM_FALLBACK_CLIENTS
        }
        val available = buildList {
            add(PREFERRED_STREAM_CLIENT)
            addAll(ordered)
            add(WEB_REMIX)
        }.distinct().filterNot { isClientTemporarilyBlocked(videoId, it) }

        val remembered = lastSuccessfulClientKey
        val initialClient = available.firstOrNull { client ->
            (!client.loginRequired || isLoggedIn) &&
                StreamClientUtils.buildClientKey(client) == remembered
        } ?: available.firstOrNull { !it.loginRequired || isLoggedIn } ?: PREFERRED_STREAM_CLIENT

        val streamClients = buildList {
            add(initialClient)
            addAll(available)
        }.distinct()

        android.util.Log.i(
            "YouTubeRepository",
            "resolveRemote videoId=$videoId loggedIn=$isLoggedIn clientes=${streamClients.size} recordado=$remembered"
        )

        for (client in streamClients) {
            if (client.loginRequired && !isLoggedIn) continue

            val poToken = poTokenFor(client, videoId)
            val response = YouTube.player(
                videoId = videoId,
                playlistId = null,
                client = client,
                signatureTimestamp = signatureTimestampFor(client, videoId),
                poToken = poToken?.playerRequestPoToken
            ).getOrNull()
            if (response == null) {
                android.util.Log.d("YouTubeRepository", "  ${client.clientName}@${client.clientVersion}: sin respuesta /player")
                continue
            }

            if (response.playabilityStatus.status != "OK") {
                android.util.Log.d(
                    "YouTubeRepository",
                    "  ${client.clientName}: status=${response.playabilityStatus.status} reason=${response.playabilityStatus.reason}"
                )
                continue
            }
            if (response.streamingData?.expiresInSeconds == null) {
                android.util.Log.d("YouTubeRepository", "  ${client.clientName}: sin streamingData")
                continue
            }

            val expectedDurationMs = response.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
            for (format in selectAudioFormatCandidates(response).take(6)) {
                if (expectedDurationMs != null && isLikelyPreview(format, expectedDurationMs)) continue

                val baseUrl = findUrlOrNull(format, videoId, client)
                if (baseUrl == null) {
                    android.util.Log.d("YouTubeRepository", "  ${client.clientName} itag=${format.itag}: sin URL (cipher falló)")
                    continue
                }
                var candidateUrl: String = baseUrl
                poToken?.streamingDataPoToken?.takeIf { client.useWebPoTokens }?.let {
                    candidateUrl = StreamClientUtils.appendPoToken(candidateUrl, it)
                }

                if (isPreviewByLength(candidateUrl, format, expectedDurationMs)) {
                    android.util.Log.d(
                        "YouTubeRepository",
                        "  ${client.clientName} itag=${format.itag}: descartada (preview por duración/tamaño)"
                    )
                    continue
                }

                if (!isStreamUrlPlayable(candidateUrl)) {
                    android.util.Log.d("YouTubeRepository", "  ${client.clientName} itag=${format.itag}: URL rechazada en validación")
                    continue
                }

                lastSuccessfulClientKey = StreamClientUtils.buildClientKey(client)
                android.util.Log.i(
                    "YouTubeRepository",
                    "resolved videoId=$videoId client=${client.clientName}@${client.clientVersion} itag=${format.itag} bitrate=${format.bitrate}"
                )
                return candidateUrl
            }
        }
        android.util.Log.w("YouTubeRepository", "resolveRemote SIN ÉXITO videoId=$videoId (${streamClients.size} clientes probados)")
        return null
    }

    suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            getCachedStreamUrl(videoId)?.let { return@runCatching it }

            var url = resolveRemote(videoId)

            // Mitigación anti bot-detection: si todo falló como invitado,
            // rotar la sesión guest (identidad nueva) y reintentar una vez.
            if (url == null && YouTube.cookie == null) {
                runCatching { YouTube.refreshVisitorData() }
                    .onSuccess {
                        android.util.Log.i("YouTubeRepository", "Sesión guest rotada; reintentando $videoId")
                    }
                url = resolveRemote(videoId)
            }

            if (url != null) {
                streamUrlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
                return@runCatching url
            }

            // Fallback final NewPipe directo
            val newPipeUrl = NewPipeExtractor.newPipePlayer(videoId).firstOrNull()?.second
            if (!newPipeUrl.isNullOrEmpty() && isStreamUrlPlayable(newPipeUrl)) {
                streamUrlCache[videoId] = CachedUrl(newPipeUrl, System.currentTimeMillis())
                return@runCatching newPipeUrl
            }

            throw Exception("No se pudo resolver el stream con ningún cliente")
        }
    }

    /**
     * Reintento para cuando una URL ya resuelta falla al reproducir (403).
     * Prueba PRIMERO NewPipe (otra vía de extracción: otro cliente, otros
     * headers), y si no, la resolución normal con clientes. Sin caché previa.
     */
    suspend fun getStreamUrlNewPipeFirst(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            streamUrlCache.remove(videoId)

            val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
            val newPipeUrl = newPipeUrls.firstOrNull()?.second
            if (!newPipeUrl.isNullOrEmpty()) {
                streamUrlCache[videoId] = CachedUrl(newPipeUrl, System.currentTimeMillis())
                return@runCatching newPipeUrl
            }

            getStreamUrl(videoId).getOrThrow()
        }
    }

    suspend fun getLyrics(videoId: String): Result<String?> = runCatching {
        val nextResult = YouTube.next(
            WatchEndpoint(videoId = videoId)
        ).getOrThrow()

        nextResult.lyricsEndpoint?.let { endpoint ->
            YouTube.lyrics(endpoint).getOrThrow()
        }
    }

    suspend fun getUserPlaylists(): Result<List<PlaylistItem>> = runCatching {
        val result = YouTube.library("FEmusic_liked_playlists").getOrThrow()
        result.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { isLikedSongsEntry(it) }
    }

    suspend fun getPlaylistSongs(playlistId: String): Result<PlaylistPage> = runCatching {
        YouTube.playlist(playlistId).getOrThrow()
    }

    suspend fun createYouTubePlaylist(title: String): Result<String> = runCatching {
        YouTube.createPlaylist(title) ?: throw Exception("Failed to create playlist")
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String): Result<Unit> = runCatching {
        YouTube.addToPlaylist(playlistId, videoId)
        Unit
    }

    /**
     * Obtiene canciones de la radio/automix basada en una canción semilla.
     * Es la fuente directa de recomendaciones contextuales de YouTube Music.
     */
    suspend fun getSongRadio(videoId: String): Result<List<SongItem>> = runCatching {
        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
        nextResult.items.filter { it.id != videoId }
    }

    /**
     * Obtiene los artistas relacionados a partir de las secciones de un artista.
     */
    suspend fun getRelatedArtists(browseId: String): Result<List<ArtistItem>> = runCatching {
        val artistPage = YouTube.artist(browseId).getOrThrow()
        artistPage.sections
            .flatMap { it.items }
            .filterIsInstance<ArtistItem>()
            .filter { it.id.isNotBlank() && it.id != browseId }
            .distinctBy { it.id }
    }

    /**
     * Busca álbumes en YouTube Music por término de búsqueda.
     */
    suspend fun searchAlbums(query: String): Result<List<AlbumItem>> = runCatching {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
        result.getOrThrow().items.filterIsInstance<AlbumItem>()
    }

    /**
     * Obtiene los álbumes y sencillos oficiales de un artista a partir de su página de YouTube Music.
     * Si el modelo de Innertube no trae 'artists', se auto-completa con el artista correspondiente.
     */
    suspend fun getArtistAlbums(browseId: String, artistName: String? = null): Result<List<AlbumItem>> = runCatching {
        val artistPage = YouTube.artist(browseId).getOrThrow()
        val name = artistName?.takeIf { it.isNotBlank() } ?: artistPage.artist.title
        val albums = artistPage.sections
            .flatMap { it.items }
            .filterIsInstance<AlbumItem>()
            .distinctBy { it.browseId }

        if (albums.isNotEmpty()) {
            albums.map { album ->
                if (album.artists.isNullOrEmpty()) {
                    album.copy(artists = listOf(Artist(name = name, id = browseId)))
                } else album
            }
        } else if (name.isNotBlank()) {
            searchAlbums(name).getOrDefault(emptyList()).take(6)
        } else {
            emptyList()
        }
    }
}

