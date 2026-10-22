package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineLibraryViewModel : ViewModel() {

    private val _likedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val likedSongs: StateFlow<List<SongItem>> = _likedSongs.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    fun loadLikedSongs() {
        viewModelScope.launch {
            _isLoadingLiked.value = true
            _onlineError.value = null
            val result = withContext(Dispatchers.IO) { YouTubeRepository.getLikedSongs() }
            result
                .onSuccess { songs ->
                    _likedSongs.value = songs
                    _likedSongIds.value = songs.map { it.id }.toSet()
                }
                .onFailure { e ->
                    _onlineError.value = e.message ?: "No se pudieron cargar tus canciones"
                }
            _isLoadingLiked.value = false
        }
    }

    /** Cambio optimista: actualiza localmente y luego llama a la API. */
    fun likeSong(videoId: String, liked: Boolean) {
        val current = _likedSongIds.value.toMutableSet()
        if (liked) current.add(videoId) else current.remove(videoId)
        _likedSongIds.value = current
        // Mantener la lista coherente: si se quita el like, eliminar de likedSongs
        if (!liked) {
            _likedSongs.value = _likedSongs.value.filterNot { it.id == videoId }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.likeVideo(videoId, liked)
            }
            result.onFailure {
                // revertir optimista
                val rev = _likedSongIds.value.toMutableSet()
                if (liked) rev.remove(videoId) else rev.add(videoId)
                _likedSongIds.value = rev
                _onlineError.value = "No se pudo actualizar en la biblioteca online"
            }
        }
    }

    fun clearOnlineError() { _onlineError.value = null }

    private val _onlinePlaylists = MutableStateFlow<List<com.music.innertube.models.PlaylistItem>>(emptyList())
    val onlinePlaylists: StateFlow<List<com.music.innertube.models.PlaylistItem>> = _onlinePlaylists.asStateFlow()

    private val _playlistSongs = MutableStateFlow<com.music.innertube.pages.PlaylistPage?>(null)
    val playlistSongs: StateFlow<com.music.innertube.pages.PlaylistPage?> = _playlistSongs.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    fun loadOnlinePlaylists() {
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            val result = withContext(Dispatchers.IO) { YouTubeRepository.getLikedPlaylists() }
            result.onSuccess { _onlinePlaylists.value = it }
                .onFailure { _onlineError.value = it.message ?: "No se pudieron cargar tus listas" }
            _isLoadingPlaylists.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            _playlistSongs.value = null
            _onlineError.value = null
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getPlaylistSongs(playlistId)
            }
            result.onSuccess { _playlistSongs.value = it }
                .onFailure { _onlineError.value = it.message ?: "No se pudo abrir la lista" }
        }
    }

    fun clearPlaylistSongs() { _playlistSongs.value = null }

    fun createOnlinePlaylist(title: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.createYouTubePlaylist(title)
            }
            result.onSuccess { loadOnlinePlaylists() }
                .onFailure { _onlineError.value = it.message ?: "No se pudo crear la lista" }
        }
    }

    fun deleteOnlinePlaylist(playlistId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.deletePlaylist(playlistId)
            }
            result.onSuccess { loadOnlinePlaylists() }
                .onFailure { _onlineError.value = it.message ?: "No se pudo borrar la lista" }
        }
    }

    fun addToOnlinePlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.addToPlaylist(playlistId, videoId)
            }
            result.onFailure { _onlineError.value = it.message ?: "No se pudo añadir la canción" }
        }
    }

    fun removeFromOnlinePlaylist(playlistId: String, videoId: String, setVideoId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.music.innertube.YouTube.removeFromPlaylist(playlistId, videoId, setVideoId)
            }
            result.onSuccess { loadPlaylistSongs(playlistId) }
                .onFailure { _onlineError.value = it.message ?: "No se pudo quitar la canción" }
        }
    }

    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannelIds: StateFlow<Set<String>> = _subscribedChannelIds.asStateFlow()

    private val _subscribedArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val subscribedArtists: StateFlow<List<ArtistItem>> = _subscribedArtists.asStateFlow()

    private val _isLoadingSubscribed = MutableStateFlow(false)
    val isLoadingSubscribed: StateFlow<Boolean> = _isLoadingSubscribed.asStateFlow()

    fun loadSubscribedArtists(forceRefresh: Boolean = false) {
        if (!forceRefresh && _subscribedArtists.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingSubscribed.value = true
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getSubscribedArtists()
            }
            result.onSuccess { artists ->
                _subscribedArtists.value = artists
                val ids = artists.map { it.channelId ?: it.id }.toSet()
                _subscribedChannelIds.value = _subscribedChannelIds.value + ids
            }
            _isLoadingSubscribed.value = false
        }
    }

    fun isSubscribed(channelId: String): Boolean = _subscribedChannelIds.value.contains(channelId)

    fun toggleSubscribeArtist(channelId: String, artistName: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentlySubscribed = _subscribedChannelIds.value.contains(channelId)
        val newSubscribed = !currentlySubscribed

        val updatedSet = _subscribedChannelIds.value.toMutableSet()
        if (newSubscribed) updatedSet.add(channelId) else updatedSet.remove(channelId)
        _subscribedChannelIds.value = updatedSet

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (newSubscribed) {
                    YouTubeRepository.subscribeChannel(channelId)
                } else {
                    YouTubeRepository.unsubscribeChannel(channelId)
                }
            }
            if (result.isFailure) {
                val revertedSet = _subscribedChannelIds.value.toMutableSet()
                if (currentlySubscribed) revertedSet.add(channelId) else revertedSet.remove(channelId)
                _subscribedChannelIds.value = revertedSet
                _onlineError.value = "Error al actualizar suscripción"
                onComplete?.invoke(currentlySubscribed)
            } else {
                if (!newSubscribed) {
                    _subscribedArtists.value = _subscribedArtists.value.filterNot { (it.channelId ?: it.id) == channelId }
                } else {
                    loadSubscribedArtists(forceRefresh = true)
                }
                onComplete?.invoke(newSubscribed)
            }
        }
    }
}