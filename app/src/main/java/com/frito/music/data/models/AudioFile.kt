package com.frito.music.data.models

data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val albumUri: String?,
    val album: String,
    val dateAdded: Long
)
