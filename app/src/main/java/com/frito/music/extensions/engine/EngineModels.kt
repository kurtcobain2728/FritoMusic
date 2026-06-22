package com.frito.music.extensions.engine

    data class SearchResult(
    val tracks: List<TrackResult>,
    val albums: List<AlbumResult>,
    val artists: List<ArtistResult>
)

data class TrackResult(
    val id: String,
    val name: String,
    val artists: String,
    val album: String,
    val duration_ms: Long,
    val imageUrl: String,
    val external_url: String,
    val platform: String
)

data class AlbumResult(
    val id: String,
    val name: String,
    val artists: String,
    val imageUrl: String,
    val platform: String
)

data class ArtistResult(
    val id: String,
    val name: String,
    val imageUrl: String,
    val platform: String
)

