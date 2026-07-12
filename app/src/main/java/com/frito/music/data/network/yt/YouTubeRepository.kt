package com.frito.music.data.network.yt

import com.frito.music.data.models.StreamableTrack
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YouTubeClient

object YouTubeRepository {

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

    suspend fun getStreamUrl(videoId: String): Result<String> = runCatching {
        val playerResponse = YouTube.player(videoId, null, YouTubeClient.WEB_REMIX).getOrThrow()

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.mimeType.startsWith("audio/") }
            ?.maxByOrNull { it.bitrate }
            ?: throw Exception("No audio format found")

        format.url
            ?: YouTube.newPipePlayer(videoId, playerResponse)?.streamingData?.adaptiveFormats
                ?.filter { it.mimeType.startsWith("audio/") }
                ?.maxByOrNull { it.bitrate }?.url
            ?: throw Exception("Could not resolve stream URL")
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
