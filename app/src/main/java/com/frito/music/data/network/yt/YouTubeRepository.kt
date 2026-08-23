package com.frito.music.data.network.yt

import com.frito.music.data.models.StreamableTrack
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.PlaylistPage
import com.frito.music.utils.potoken.PoTokenGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    // Clients that don't require PoToken (work without login)
    private val ANONYMOUS_CLIENTS = listOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48
    )

    // Clients that work best with login (have cookies)
    private val AUTHENTICATED_CLIENTS = listOf(
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_VR_1_43_32
    )

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

    private suspend fun resolveWithClient(client: YouTubeClient, videoId: String): String? {
        val poToken = if (client.useWebPoTokens) {
            val sessionId = YouTube.dataSyncId ?: YouTube.visitorData
            if (sessionId != null) poTokenGenerator.getWebClientPoToken(videoId, sessionId) else null
        } else null

        val playerResponse = YouTube.player(
            videoId = videoId,
            playlistId = null,
            client = client,
            signatureTimestamp = getSignatureTimestamp(videoId),
            poToken = poToken?.playerRequestPoToken
        ).getOrNull() ?: return null

        if (playerResponse.playabilityStatus.status != "OK") return null

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.mimeType.startsWith("audio/") }
            ?.maxByOrNull { it.bitrate } ?: return null

        val directUrl = format.url
        if (!directUrl.isNullOrEmpty()) {
            return if (poToken != null) "$directUrl&pot=${poToken.streamingDataPoToken}" else directUrl
        }

        val sigCipher = format.signatureCipher
        val cipher = format.cipher
        if (!sigCipher.isNullOrEmpty() || !cipher.isNullOrEmpty()) {
            val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
            if (deobfuscatedUrl != null) {
                return if (poToken != null) "$deobfuscatedUrl&pot=${poToken.streamingDataPoToken}" else deobfuscatedUrl
            }
        }

        val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
        if (newPipeUrls.isNotEmpty()) {
            val audioUrl = newPipeUrls.firstOrNull { (itag, _) ->
                playerResponse.streamingData?.adaptiveFormats
                    ?.any { it.itag == itag && it.mimeType.startsWith("audio/") } == true
            }?.second ?: newPipeUrls.firstOrNull()?.second
            if (audioUrl != null) return audioUrl
        }

        return null
    }

    private suspend fun raceClients(videoId: String, clients: List<YouTubeClient>): String? =
        supervisorScope {
            val first = CompletableDeferred<String>()
            val remaining = AtomicInteger(clients.size)
            val jobs = clients.map { client ->
                async(Dispatchers.IO) {
                    val url = runCatching { resolveWithClient(client, videoId) }.getOrNull()
                    if (url != null) {
                        first.complete(url)
                    } else if (remaining.decrementAndGet() == 0) {
                        first.completeExceptionally(Exception("Ningún cliente pudo resolver el stream"))
                    }
                }
            }
            try {
                first.await()
            } catch (e: Exception) {
                null
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

    suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            getCachedStreamUrl(videoId)?.let { return@runCatching it }

            val isLoggedIn = YouTube.cookie != null
            val clients = if (isLoggedIn) AUTHENTICATED_CLIENTS else ANONYMOUS_CLIENTS

            val url = raceClients(videoId, clients)
            if (url != null) {
                streamUrlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
                return@runCatching url
            }

            // Fallback final NewPipe directo
            val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
            if (newPipeUrls.isNotEmpty()) {
                val audioUrl = newPipeUrls.firstOrNull()?.second
                if (audioUrl != null) {
                    streamUrlCache[videoId] = CachedUrl(audioUrl, System.currentTimeMillis())
                    return@runCatching audioUrl
                }
            }

            throw Exception("Could not resolve stream URL with any client")
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
        result.items.filterIsInstance<PlaylistItem>()
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
}
