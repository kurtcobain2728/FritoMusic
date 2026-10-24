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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object YouTubeRepository {

    private val poTokenGenerator = PoTokenGenerator()

    private data class CachedUrl(val url: String, val timestamp: Long)
    private val streamUrlCache = ConcurrentHashMap<String, CachedUrl>()
    private const val STREAM_URL_TTL_MS = 4 * 60 * 60 * 1000L

    data class ResolvedFormatDetails(
        val videoId: String,
        val codec: String,
        val container: String,
        val bitrate: Int,
        val sampleRate: Int,
        val itag: Int?,
        val mimeType: String
    )
    private val lastResolvedFormatDetails = ConcurrentHashMap<String, ResolvedFormatDetails>()

    fun getResolvedFormatDetails(videoId: String): ResolvedFormatDetails? {
        return lastResolvedFormatDetails[videoId]
    }

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
        } catch (e: Exception) {
            null
        }
    }

    private fun getStreamQualityKey(): String {
        val q = appContext?.let { com.frito.music.data.preferences.StreamQualityPreferences.getQuality(it).key } ?: "high"
        val c = appContext?.let { com.frito.music.data.preferences.StreamQualityPreferences.getCodecPreference(it).key } ?: "auto"
        return "${c}_$q"
    }

    private fun getCachedStreamUrl(videoId: String): String? {
        val key = "${videoId}_${getStreamQualityKey()}"
        val entry = streamUrlCache[key] ?: streamUrlCache[videoId] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < STREAM_URL_TTL_MS) entry.url
        else { 
            streamUrlCache.remove(key)
            streamUrlCache.remove(videoId)
            null 
        }
    }

    /**
     * Invalida la URL cacheada de un video. Se llama cuando ExoPlayer da un
     * error de playback (p. ej. HTTP 403): la URL guardada puede estar
     * "envenenada" (bloqueada por bot-detection) y no debe reutilizarse.
     */
    fun invalidateStreamUrl(videoId: String) {
        val key = "${videoId}_${getStreamQualityKey()}"
        streamUrlCache.remove(key)
        streamUrlCache.remove(videoId)
    }

    fun clearStreamUrlCache() {
        streamUrlCache.clear()
        videoStreamUrlCache.clear()
        android.util.Log.i("YouTubeRepository", "Caché de URLs de streaming limpiada por cambio de calidad")
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
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Range", "bytes=0-0")
            if (isYouTubeHost) {
                profile.headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            }
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            val code = conn.responseCode
            runCatching { conn.inputStream?.close() }
            conn.disconnect()
            android.util.Log.d(
                "YouTubeRepository",
                "  validación rápida host=$host client=${if (isYouTubeHost) profile.requestedClientName else "n/a"} range=bytes=0-0 -> HTTP $code"
            )
            code in 200..399 || code == 416
        } catch (e: Exception) {
            android.util.Log.d("YouTubeRepository", "  validación excepción: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    // ─── Resolución de streams estilo FridaMusic ───
    private val PREFERRED_STREAM_CLIENT: YouTubeClient = IOS

    private fun isIosClient(client: YouTubeClient): Boolean =
        client == IOS || client == IOS_MUSIC || client == IPADOS || client == VISIONOS

    /**
     * Pool de clientes ordenado dinámicamente según el códec preferido del usuario.
     * Si el usuario eligió OPUS, los clientes nativos WebM/Opus encabezan la lista
     * evitando round-trips inútiles con clientes iOS (que solo admiten AAC).
     */
    private fun getClientsForCodec(preferredCodec: com.frito.music.data.preferences.AudioCodecPreference): List<YouTubeClient> {
        return when (preferredCodec) {
            com.frito.music.data.preferences.AudioCodecPreference.OPUS -> listOf(
                ANDROID_TESTSUITE, ANDROID_MUSIC, MOBILE, ANDROID_VR_NO_AUTH,
                ANDROID_VR_1_61_48, ANDROID_VR_1_43_32, ANDROID_CREATOR, ANDROID_UNPLUGGED,
                WEB, WEB_REMIX, TVHTML5, TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                IOS, IOS_MUSIC, IPADOS, VISIONOS
            )
            com.frito.music.data.preferences.AudioCodecPreference.AAC -> listOf(
                IOS, IOS_MUSIC, IPADOS, VISIONOS,
                ANDROID_TESTSUITE, MOBILE, ANDROID_MUSIC, ANDROID_VR_NO_AUTH,
                ANDROID_VR_1_61_48, ANDROID_VR_1_43_32, ANDROID_CREATOR, ANDROID_UNPLUGGED,
                WEB, WEB_REMIX, TVHTML5, TVHTML5_SIMPLY_EMBEDDED_PLAYER
            )
            com.frito.music.data.preferences.AudioCodecPreference.AUTO -> listOf(
                ANDROID_TESTSUITE, IOS, IOS_MUSIC, MOBILE, ANDROID_MUSIC,
                IPADOS, VISIONOS, ANDROID_VR_NO_AUTH, ANDROID_VR_1_61_48,
                ANDROID_VR_1_43_32, ANDROID_CREATOR, ANDROID_UNPLUGGED,
                WEB, WEB_REMIX, TVHTML5, TVHTML5_SIMPLY_EMBEDDED_PLAYER
            )
        }
    }

    /** Backoff por (videoId, cliente) tras un HTTP 403: 10 minutos. */
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()

    /** Último cliente que produjo un stream reproducible (se prueba primero). */
    @Volatile
    private var lastSuccessfulClientKey: String? = null

    // Persistencia del último cliente exitoso entre sesiones: la primera
    // canción de cada sesión ya no paga la cola de clientes que fallan.
    private const val PREFS_NAME = "frito_stream_client"
    private const val KEY_LAST_CLIENT = "last_successful_client"
    private var streamPrefs: android.content.SharedPreferences? = null

    @Volatile
    private var appContext: android.content.Context? = null

    /** Carga el último cliente exitoso guardado (llamar desde Application). */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        streamPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = streamPrefs?.getString(KEY_LAST_CLIENT, null)
        if (!saved.isNullOrBlank() && lastSuccessfulClientKey == null) {
            lastSuccessfulClientKey =
                StreamClientUtils.normalizeClientKey(saved).takeIf { it.isNotEmpty() }
        }
    }

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

    /** Ordena formatos de audio según calidad y códec seleccionados por el usuario (Alta, Normal, Baja / OPUS, AAC, Auto). */
    private fun selectAudioFormatCandidates(
        response: PlayerResponse,
    ): List<PlayerResponse.StreamingData.Format> {
        val preferredQuality = appContext?.let { com.frito.music.data.preferences.StreamQualityPreferences.getQuality(it) }
            ?: com.frito.music.data.preferences.StreamQuality.HIGH
        val preferredCodec = appContext?.let { com.frito.music.data.preferences.StreamQualityPreferences.getCodecPreference(it) }
            ?: com.frito.music.data.preferences.AudioCodecPreference.AUTO

        val formats = response.streamingData?.adaptiveFormats
            ?.asSequence()
            ?.filter { it.mimeType.startsWith("audio/") && it.bitrate > 0 }
            ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            ?: return emptyList()

        val comparator = when (preferredQuality) {
            com.frito.music.data.preferences.StreamQuality.HIGH -> {
                compareByDescending<PlayerResponse.StreamingData.Format> { codecRank(extractCodec(it.mimeType), it.mimeType, preferredCodec) }
                    .thenByDescending { it.bitrate }
                    .thenByDescending { it.audioSampleRate ?: 0 }
            }
            com.frito.music.data.preferences.StreamQuality.NORMAL -> {
                // Balance estándar (~128 kbps)
                compareByDescending<PlayerResponse.StreamingData.Format> { codecRank(extractCodec(it.mimeType), it.mimeType, preferredCodec) }
                    .thenBy { kotlin.math.abs(it.bitrate - 128_000) }
            }
            com.frito.music.data.preferences.StreamQuality.LOW -> {
                // Modo ahorro (~48-64 kbps Opus/AAC)
                compareByDescending<PlayerResponse.StreamingData.Format> { codecRank(extractCodec(it.mimeType), it.mimeType, preferredCodec) }
                    .thenBy { it.bitrate }
            }
        }

        val result = formats.sortedWith(comparator).toList()
        android.util.Log.i(
            "YouTubeRepository",
            "selectAudioFormatCandidates codec=$preferredCodec calidad=$preferredQuality formato=${result.firstOrNull()?.mimeType} bitrate=${result.firstOrNull()?.bitrate}"
        )
        return result
    }

    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
    ): String? {
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

    private fun codecRank(codec: String?, mimeType: String, preferredCodec: com.frito.music.data.preferences.AudioCodecPreference): Int {
        val isOpus = (codec != null && codec.contains("opus", ignoreCase = true)) || mimeType.contains("opus", ignoreCase = true) || mimeType.contains("webm", ignoreCase = true)
        val isAac = (codec != null && (codec.contains("mp4a", ignoreCase = true) || codec.contains("aac", ignoreCase = true))) || mimeType.contains("mp4", ignoreCase = true)

        return when (preferredCodec) {
            com.frito.music.data.preferences.AudioCodecPreference.AAC -> {
                when {
                    isAac -> 20
                    isOpus -> 5
                    else -> 1
                }
            }
            com.frito.music.data.preferences.AudioCodecPreference.OPUS -> {
                when {
                    isOpus -> 20
                    isAac -> 5
                    else -> 1
                }
            }
            com.frito.music.data.preferences.AudioCodecPreference.AUTO -> {
                when {
                    isOpus -> 10
                    isAac -> 9
                    else -> 1
                }
            }
        }
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
            registerVideoType(song.id, song.musicVideoType)
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

    suspend fun searchPlaylists(query: String): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val featured = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST)
                .getOrNull()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            val community = YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                .getOrNull()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            (featured + community).distinctBy { it.id }
        }
    }

    suspend fun getArtistDetails(browseId: String): Result<ArtistPage> = runCatching {
        YouTube.artist(browseId).getOrThrow()
    }

    suspend fun getAlbumDetails(browseId: String): Result<AlbumPage> = runCatching {
        val album = YouTube.album(browseId).getOrThrow()
        album.songs.forEach { registerVideoType(it.id, it.musicVideoType) }
        album
    }

    suspend fun getHome(): Result<HomePage> = runCatching {
        YouTube.home().getOrThrow()
    }

    suspend fun getExplore(): Result<ExplorePage> = runCatching {
        YouTube.explore().getOrThrow()
    }

    suspend fun getArtistVideos(artistName: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.search("$artistName official music video", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            val songs = result?.items?.filterIsInstance<SongItem>().orEmpty()
            songs.forEach { registerVideoType(it.id, "MUSIC_VIDEO_TYPE_OMV") }
            songs.take(15)
        }
    }

    /**
     * Obtiene álbumes y singles de los artistas preferidos del usuario en base a su historial y favoritos.
     */
    suspend fun getPersonalizedAlbums(
        artistNames: List<String>,
        artistIds: Map<String, String> = emptyMap()
    ): Result<List<AlbumItem>> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                artistNames.take(5).map { artistName ->
                    async {
                        val artistId = artistIds[artistName.lowercase()]
                        if (!artistId.isNullOrBlank()) {
                            YouTube.artist(artistId).getOrNull()?.sections
                                ?.filter { s ->
                                    val t = s.title.lowercase()
                                    t.contains("álbum") || t.contains("album") || t.contains("single") || t.contains("sencillo")
                                }
                                ?.flatMap { it.items }
                                ?.filterIsInstance<AlbumItem>()
                                ?.take(2)
                                .orEmpty()
                        } else {
                            YouTube.search(artistName, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                                ?.items?.filterIsInstance<AlbumItem>()?.take(2).orEmpty()
                        }
                    }
                }.awaitAll().flatten().distinctBy { it.browseId }
            }
        }
    }

    /**
     * Obtiene videos musicales oficiales de los artistas preferidos del usuario.
     */
    suspend fun getPersonalizedVideos(artistNames: List<String>): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                artistNames.take(5).map { artistName ->
                    async {
                        getArtistVideos(artistName).getOrDefault(emptyList()).take(3)
                    }
                }.awaitAll().flatten().distinctBy { it.id }.also { videos ->
                    videos.forEach { registerVideoType(it.id, "MUSIC_VIDEO_TYPE_OMV") }
                }
            }
        }
    }

    suspend fun getNewMusicVideos(): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.search("videos musicales nuevos oficiales", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            val songs = result?.items?.filterIsInstance<SongItem>().orEmpty()
            songs.forEach { registerVideoType(it.id, "MUSIC_VIDEO_TYPE_OMV") }
            songs.take(20)
        }
    }

    /**
     * Historial de reproducción de la cuenta (requiere sesión).
     * Devuelve las canciones escuchadas recientemente, aplanadas y sin duplicados.
     * Es la base de la sección "Escuchado recientemente" del home de Stream.
     */
    suspend fun getMusicHistory(): Result<List<SongItem>> = runCatching {
        val page = YouTube.musicHistory().getOrThrow()
        val songs = page.sections.orEmpty()
            .flatMap { it.songs }
            .distinctBy { it.id }
        songs.forEach { registerVideoType(it.id, it.musicVideoType) }
        songs
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
        val cleanId = item.id.removePrefix("VL")
        // Las playlists creadas o migradas por el usuario empiezan siempre por "PL".
        // NUNCA son la lista de Me Gusta autogenerada del sistema.
        if (cleanId.startsWith("PL")) {
            return false
        }
        val byId = item.id.startsWith("RDCLAK5uy") || item.id == "FEmusic_liked_songs" || item.id == "LM" || item.id == "LL"
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
        val remote = page.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { isLikedSongsEntry(it) }
        remote.forEach { pl ->
            if (pl.id.removePrefix("VL").startsWith("PL")) {
                com.frito.music.data.repository.MigratedPlaylistsManager.save(pl)
            }
        }
        val localMigrated = com.frito.music.data.repository.MigratedPlaylistsManager.getAll()
        (localMigrated + remote).distinctBy { it.id }
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
        val preferredCodec = appContext?.let { com.frito.music.data.preferences.StreamQualityPreferences.getCodecPreference(it) }
            ?: com.frito.music.data.preferences.AudioCodecPreference.AUTO

        val ordered = getClientsForCodec(preferredCodec)
        val available = ordered.filterNot { isClientTemporarilyBlocked(videoId, it) }

        val remembered = lastSuccessfulClientKey
        // Si el usuario configuró OPUS, no empezar con un cliente iOS que no soporta Opus
        val initialClient = available.firstOrNull { client ->
            (!client.loginRequired || isLoggedIn) &&
                StreamClientUtils.buildClientKey(client) == remembered &&
                (preferredCodec != com.frito.music.data.preferences.AudioCodecPreference.OPUS || !isIosClient(client))
        } ?: available.firstOrNull { client ->
            (!client.loginRequired || isLoggedIn) &&
                (preferredCodec != com.frito.music.data.preferences.AudioCodecPreference.OPUS || !isIosClient(client))
        } ?: available.firstOrNull { !it.loginRequired || isLoggedIn } ?: ordered.first()

        val streamClients = buildList {
            add(initialClient)
            addAll(available)
        }.distinct()

        android.util.Log.i(
            "YouTubeRepository",
            "resolveRemote videoId=$videoId codec=$preferredCodec loggedIn=$isLoggedIn clientes=${streamClients.size} inicial=${initialClient.clientName} recordado=$remembered"
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

            // Registrar tipo de video para auto-priorización inteligente
            response.videoDetails?.musicVideoType?.let { registerVideoType(videoId, it) }

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
                streamPrefs?.edit()?.putString(KEY_LAST_CLIENT, lastSuccessfulClientKey)?.apply()
                val rawCodec = extractCodec(format.mimeType)?.uppercase() ?: ""
                val cleanCodec = when {
                    rawCodec.contains("OPUS") || format.mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                    rawCodec.contains("MP4A") || rawCodec.contains("AAC") || format.mimeType.contains("mp4", ignoreCase = true) -> "AAC"
                    else -> rawCodec.ifBlank { "AUDIO" }
                }
                val container = if (format.mimeType.contains("webm", ignoreCase = true)) "WebM" else "M4A"
                lastResolvedFormatDetails[videoId] = ResolvedFormatDetails(
                    videoId = videoId,
                    codec = cleanCodec,
                    container = container,
                    bitrate = format.bitrate,
                    sampleRate = format.audioSampleRate ?: 44100,
                    itag = format.itag,
                    mimeType = format.mimeType
                )
                android.util.Log.i(
                    "YouTubeRepository",
                    "resolved videoId=$videoId client=${client.clientName}@${client.clientVersion} itag=${format.itag} codec=$cleanCodec bitrate=${format.bitrate}"
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
                val cacheKey = "${videoId}_${getStreamQualityKey()}"
                streamUrlCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
                return@runCatching url
            }

            // Fallback final NewPipe directo
            val newPipeUrl = NewPipeExtractor.newPipePlayer(videoId).firstOrNull()?.second
            if (!newPipeUrl.isNullOrEmpty() && isStreamUrlPlayable(newPipeUrl)) {
                val cacheKey = "${videoId}_${getStreamQualityKey()}"
                streamUrlCache[cacheKey] = CachedUrl(newPipeUrl, System.currentTimeMillis())
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
            val cacheKey = "${videoId}_${getStreamQualityKey()}"
            streamUrlCache.remove(cacheKey)
            streamUrlCache.remove(videoId)

            val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
            val newPipeUrl = newPipeUrls.firstOrNull()?.second
            if (!newPipeUrl.isNullOrEmpty()) {
                streamUrlCache[cacheKey] = CachedUrl(newPipeUrl, System.currentTimeMillis())
                return@runCatching newPipeUrl
            }

            getStreamUrl(videoId).getOrThrow()
        }
    }

    private val videoStreamUrlCache = ConcurrentHashMap<String, CachedUrl>()
    private val videoTypeCache = ConcurrentHashMap<String, String>()

    fun registerVideoType(videoId: String, musicVideoType: String?) {
        if (!musicVideoType.isNullOrBlank()) {
            videoTypeCache[videoId] = musicVideoType
        }
    }

    /**
     * Determina si la pista corresponde a un video musical real (videoclip oficial OMV,
     * contenido generado UGC/concierto/letra) o si es un video puramente estático (ATV / Topic Video)
     * que solo muestra la portada del álbum inmóvil.
     */
    suspend fun isRealMusicVideo(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val cached = videoTypeCache[videoId]
        if (cached != null) {
            return@withContext cached != WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV
        }

        val response = runCatching {
            YouTube.player(videoId = videoId, client = ANDROID_TESTSUITE).getOrNull()
        }.getOrNull()

        val type = response?.videoDetails?.musicVideoType
        if (!type.isNullOrBlank()) {
            videoTypeCache[videoId] = type
            return@withContext type != WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV
        }

        val author = response?.videoDetails?.author.orEmpty()
        val title = response?.videoDetails?.title.orEmpty()
        if (author.endsWith("- Topic", ignoreCase = true) || title.contains("- Topic", ignoreCase = true)) {
            videoTypeCache[videoId] = WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV
            return@withContext false
        }

        true
    }

    fun invalidateVideoStreamCache(videoId: String) {
        videoStreamUrlCache.remove(videoId)
    }

    suspend fun hasVideoTrack(videoId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (videoStreamUrlCache.containsKey(videoId)) return@withContext true
            val res = getVideoStreamUrl(videoId)
            res.isSuccess && !res.getOrNull().isNullOrBlank()
        }.getOrDefault(false)
    }

    /**
     * Resuelve la URL directa del stream de video (.mp4) para el reproductor nativo.
     * Elimina el uso de WebView/iframe evitando el Error 152 / Error 150 de YouTube.
     * Valida la conectividad real HTTP 200/206 antes de entregar o cachear la URL.
     */
    suspend fun getVideoStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            videoStreamUrlCache[videoId]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < STREAM_URL_TTL_MS) {
                    return@runCatching cached.url
                } else {
                    videoStreamUrlCache.remove(videoId)
                }
            }

            // 1. Extraer directo vía NewPipeExtractor (especializado en video MP4/muxed, rápido y sin bot-checks)
            val directVideoUrl = NewPipeExtractor.getVideoStreamUrl(videoId)
            if (!directVideoUrl.isNullOrBlank()) {
                if (isStreamUrlPlayable(directVideoUrl)) {
                    android.util.Log.i("YouTubeRepository", "Stream de video obtenido y validado vía NewPipeExtractor: $directVideoUrl")
                    videoStreamUrlCache[videoId] = CachedUrl(directVideoUrl, System.currentTimeMillis())
                    return@runCatching directVideoUrl
                }
            }

            // 2. Fallback a clientes YouTube con soporte MP4
            val clientsToTry = listOf(
                ANDROID_TESTSUITE,
                PREFERRED_STREAM_CLIENT, // IOS
                TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                WEB,
                WEB_REMIX,
                ANDROID_VR_NO_AUTH
            )
            for (client in clientsToTry) {
                val poToken = poTokenFor(client, videoId)
                val response = YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestampFor(client, videoId),
                    poToken = poToken?.playerRequestPoToken
                ).getOrNull()

                if (response == null || response.playabilityStatus.status != "OK") {
                    continue
                }

                response.videoDetails?.musicVideoType?.let { registerVideoType(videoId, it) }

                val formats = response.streamingData?.adaptiveFormats.orEmpty() + response.streamingData?.formats.orEmpty()
                val videoFormat = formats
                    .filter { (it.mimeType.startsWith("video/mp4") || it.mimeType.startsWith("video/webm")) && (it.width ?: 0) > 0 }
                    .minByOrNull { kotlin.math.abs((it.height ?: 720) - 720) }

                if (videoFormat != null) {
                    val rawCandidate = findUrlOrNull(videoFormat, videoId, client) ?: videoFormat.url
                    if (!rawCandidate.isNullOrBlank()) {
                        val candidateUrl: String = poToken?.streamingDataPoToken?.takeIf { client.useWebPoTokens }?.let {
                            StreamClientUtils.appendPoToken(rawCandidate, it)
                        } ?: rawCandidate

                        if (isStreamUrlPlayable(candidateUrl)) {
                            android.util.Log.i("YouTubeRepository", "Stream de video obtenido y validado vía ${client.clientName}: $candidateUrl")
                            videoStreamUrlCache[videoId] = CachedUrl(candidateUrl, System.currentTimeMillis())
                            return@runCatching candidateUrl
                        } else {
                            android.util.Log.d("YouTubeRepository", "Stream de video vía ${client.clientName} rechazado en verificación de red")
                        }
                    }
                }
            }

            throw Exception("No se encontró stream de video reproducible para $videoId")
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
        val remote = result.items
            .filterIsInstance<PlaylistItem>()
            .filterNot { isLikedSongsEntry(it) }
        remote.forEach { pl ->
            if (pl.id.removePrefix("VL").startsWith("PL")) {
                com.frito.music.data.repository.MigratedPlaylistsManager.save(pl)
            }
        }
        val localMigrated = com.frito.music.data.repository.MigratedPlaylistsManager.getAll()
        (localMigrated + remote).distinctBy { it.id }
    }

    suspend fun getFeaturedPlaylists(): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<PlaylistItem>()
            val seenIds = mutableSetOf<String>()

            val queries = listOf("Top 50 Éxitos", "Tendencias Música", "Pop Latino Hits", "Rock Clásico")
            for (query in queries) {
                try {
                    val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                    searchResult?.items?.filterIsInstance<PlaylistItem>()?.forEach { p ->
                        if (p.id.isNotBlank() && seenIds.add(p.id)) {
                            results.add(p)
                        }
                    }
                } catch (_: Exception) {}
            }
            results
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        com.frito.music.data.repository.MigratedPlaylistsManager.delete(cleanId)
        YouTube.deletePlaylist(cleanId).map { }
    }

    suspend fun subscribeChannel(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val res = YouTube.subscribeChannel(channelId)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Subscription failed"))
    }

    suspend fun unsubscribeChannel(channelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val res = YouTube.unsubscribeChannel(channelId)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Unsubscription failed"))
    }

    suspend fun getSubscribedArtists(): Result<List<ArtistItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = YouTube.library("FEmusic_library_corpus_artists").getOrThrow()
            result.items.filterIsInstance<ArtistItem>()
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): Result<PlaylistPage> = runCatching {
        YouTube.playlist(playlistId).getOrThrow()
    }

    suspend fun createYouTubePlaylist(title: String, videoIds: List<String>? = null): Result<String> = runCatching {
        val playlistId = YouTube.createPlaylist(title, videoIds) ?: throw Exception("Failed to create playlist")
        val cleanId = playlistId.removePrefix("VL")
        try {
            YouTube.likePlaylist(cleanId, true)
        } catch (_: Exception) {}
        playlistId
    }

    suspend fun registerPlayback(videoId: String, playlistId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        YouTube.registerPlaybackForVideo(videoId, playlistId)
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

