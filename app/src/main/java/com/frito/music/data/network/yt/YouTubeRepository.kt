package com.frito.music.data.network.yt

import com.frito.music.data.models.StreamableTrack
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.ArtistPage
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX

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

    suspend fun getStreamUrl(videoId: String): Result<String> = runCatching {
        var lastException: Exception? = null

        for (client in STREAM_CLIENTS) {
            try {
                val playerResponse = YouTube.player(videoId, null, client).getOrThrow()

                val format = playerResponse.streamingData?.adaptiveFormats
                    ?.filter { it.mimeType.startsWith("audio/") }
                    ?.maxByOrNull { it.bitrate }

                if (format != null) {
                    format.url?.let { return@runCatching it }

                    YouTube.newPipePlayer(videoId, playerResponse)?.streamingData?.adaptiveFormats
                        ?.filter { it.mimeType.startsWith("audio/") }
                        ?.maxByOrNull { it.bitrate }?.url?.let { return@runCatching it }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
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
