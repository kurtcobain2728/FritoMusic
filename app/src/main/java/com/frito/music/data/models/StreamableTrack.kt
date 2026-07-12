package com.frito.music.data.models

data class StreamableTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val thumbnailUrl: String
) {
    fun toAudioFile(streamUrl: String) = AudioFile(
        id = videoId.hashCode().toLong(),
        title = title,
        artist = artist,
        path = streamUrl,
        durationMs = durationMs,
        sizeBytes = 0L,
        albumUri = thumbnailUrl,
        album = album ?: "",
        dateAdded = System.currentTimeMillis()
    )
}
