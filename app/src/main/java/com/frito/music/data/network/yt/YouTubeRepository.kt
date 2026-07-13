package com.frito.music.data.network.yt

import com.frito.music.data.models.StreamableTrack
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
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

object YouTubeRepository {

    private val STREAM_CLIENTS = listOf(
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        WEB_REMIX
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

    suspend fun getStreamUrl(videoId: String): Result<String> = runCatching {
        var lastException: Exception? = null

        for (client in STREAM_CLIENTS) {
            try {
                val playerResponse = YouTube.player(videoId, null, client).getOrThrow()

                // Check if playability status is OK
                if (playerResponse.playabilityStatus.status != "OK") {
                    lastException = Exception("Playability: ${playerResponse.playabilityStatus.reason}")
                    continue
                }

                val format = playerResponse.streamingData?.adaptiveFormats
                    ?.filter { it.mimeType.startsWith("audio/") }
                    ?.maxByOrNull { it.bitrate }

                if (format != null) {
                    // 1. Try direct URL
                    val directUrl = format.url
                    if (!directUrl.isNullOrEmpty()) {
                        return@runCatching directUrl
                    }

                    // 2. Try signatureCipher deobfuscation via NewPipe
                    val sigCipher = format.signatureCipher
                    val cipher = format.cipher
                    if (!sigCipher.isNullOrEmpty() || !cipher.isNullOrEmpty()) {
                        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
                        if (deobfuscatedUrl != null) {
                            return@runCatching deobfuscatedUrl
                        }
                    }

                    // 3. Try NewPipe player as last resort for this client
                    val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
                    if (newPipeUrls.isNotEmpty()) {
                        // Find best audio stream
                        val audioUrl = newPipeUrls.firstOrNull { (itag, _) ->
                            playerResponse.streamingData?.adaptiveFormats
                                ?.any { it.itag == itag && it.mimeType.startsWith("audio/") } == true
                        }?.second ?: newPipeUrls.firstOrNull()?.second

                        if (audioUrl != null) {
                            return@runCatching audioUrl
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }

        // Final fallback: try NewPipe directly without any client
        try {
            val newPipeUrls = NewPipeExtractor.newPipePlayer(videoId)
            if (newPipeUrls.isNotEmpty()) {
                val audioUrl = newPipeUrls.firstOrNull()?.second
                if (audioUrl != null) {
                    return@runCatching audioUrl
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        throw lastException ?: Exception("Could not resolve stream URL with any client")
    }

    suspend fun getLyrics(videoId: String): Result<String?> = runCatching {
        val nextResult = YouTube.next(
            WatchEndpoint(videoId = videoId)
        ).getOrThrow()

        nextResult.lyricsEndpoint?.let { endpoint ->
            YouTube.lyrics(endpoint).getOrThrow()
        }
    }
}
