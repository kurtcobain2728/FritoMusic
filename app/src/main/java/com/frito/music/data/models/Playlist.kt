package com.frito.music.data.models

data class Playlist(
    val id: String,
    val name: String,
    val audioPaths: List<String>
)
