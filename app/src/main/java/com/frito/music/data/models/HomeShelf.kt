package com.frito.music.data.models

import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

/**
 * Representa el perfil de gustos consolidado del usuario según su actividad.
 */
data class TasteProfile(
    val likedSongs: List<SongItem> = emptyList(),
    val likedArtistIds: Set<String> = emptySet(),
    val likedAlbumIds: Set<String> = emptySet(),
    val playlistSongIds: Set<String> = emptySet(),
    val recentSongIds: List<String> = emptyList()
) {
    /**
     * Conjunto de IDs que el usuario ya conoce/escucha frecuentemente
     * para evitar repetirlos en las recomendaciones nuevas.
     */
    val knownVideoIds: Set<String> by lazy {
        (likedSongs.map { it.id } + playlistSongIds + recentSongIds).toSet()
    }
}

/**
 * Representa un estante/carrusel curado del Home de Stream.
 */
data class HomeShelf(
    val id: String,
    val title: String,
    val items: List<YTItem>
)
