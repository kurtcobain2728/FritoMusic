package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamViewModel : ViewModel() {
    
    private val _searchResults = MutableStateFlow<List<StreamableTrack>?>(null)
    val searchResults: StateFlow<List<StreamableTrack>?> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Error exclusivo de reproducción (resolución de URL). Separado de
    // errorMessage para que un fallo al reproducir no "sangre" a las pantallas
    // de detalle que muestran errores de carga.
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    
    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()
    
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _artistResults = MutableStateFlow<List<ArtistItem>?>(null)
    val artistResults: StateFlow<List<ArtistItem>?> = _artistResults.asStateFlow()

    private val _selectedArtist = MutableStateFlow<ArtistPage?>(null)
    val selectedArtist: StateFlow<ArtistPage?> = _selectedArtist.asStateFlow()

    private val _isLoadingArtist = MutableStateFlow(false)
    val isLoadingArtist: StateFlow<Boolean> = _isLoadingArtist.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<AlbumPage?>(null)
    val selectedAlbum: StateFlow<AlbumPage?> = _selectedAlbum.asStateFlow()

    private val _isLoadingAlbum = MutableStateFlow(false)
    val isLoadingAlbum: StateFlow<Boolean> = _isLoadingAlbum.asStateFlow()

    private val _homePage = MutableStateFlow<HomePage?>(null)
    val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

    private val _explorePage = MutableStateFlow<ExplorePage?>(null)
    val explorePage: StateFlow<ExplorePage?> = _explorePage.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    // Historial personal (requiere sesión): alimenta la sección
    // "Escuchado recientemente" del home de Stream.
    private val _recentlyPlayed = MutableStateFlow<List<SongItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<SongItem>> = _recentlyPlayed.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val userPlaylists: StateFlow<List<PlaylistItem>> = _userPlaylists.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<PlaylistPage?>(null)
    val selectedPlaylistSongs: StateFlow<PlaylistPage?> = _selectedPlaylistSongs.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    private var searchJob: Job? = null
    private var prefetchJob: Job? = null

    /**
     * Prefetch cancelable: si llega una búsqueda nueva, se cancela el prefetch
     * anterior (antes se acumulaban hasta 9 player-requests y arriesgaba 429).
     */
    fun prefetchStreamUrls(videoIds: List<String>) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            videoIds.take(3).forEach { id ->
                if (!isActive) return@launch
                runCatching { YouTubeRepository.getStreamUrl(id) }
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()

        if (query.length < 2) {
            _searchResults.value = null
            _artistResults.value = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            _isSearching.value = true
            _errorMessage.value = null

            // Canciones y artistas EN PARALELO (antes iban en serie: doble espera)
            coroutineScope {
                val songsDeferred = async(Dispatchers.IO) {
                    YouTubeRepository.search(query)
                }
                val artistsDeferred = async(Dispatchers.IO) {
                    YouTubeRepository.searchArtists(query)
                }

                val searchResult = songsDeferred.await()
                searchResult
                    .onSuccess { results ->
                        _searchResults.value = results
                        prefetchStreamUrls(results.map { it.videoId })
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message ?: "Error searching"
                        _searchResults.value = null
                    }

                val artistResult = artistsDeferred.await()
                artistResult
                    .onSuccess { artists ->
                        _artistResults.value = artists
                    }
                    .onFailure { _artistResults.value = null }
            }

            _isSearching.value = false
        }
    }

    /**
     * Núcleo de reproducción: resuelve SOLO la URL del track inicial (arranque
     * rápido) y encola la lista completa; las URLs restantes se resuelven
     * perezosamente desde PlayerViewModel cuando ExoPlayer llega a cada una.
     */
    private fun startPlayback(
        tracks: List<StreamableTrack>,
        startIndex: Int,
        playerViewModel: PlayerViewModel
    ) {
        if (tracks.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, tracks.lastIndex)
        val startTrack = tracks[safeStart]
        playerViewModel.setPreparingAudio(startTrack.toAudioFile(""))

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getStreamUrl(startTrack.videoId)
            }
            result
                .onSuccess { streamUrl ->
                    // Solo el track inicial trae URL; el resto queda pendiente ("")
                    val audios = tracks.mapIndexed { i, t ->
                        t.toAudioFile(if (i == safeStart) streamUrl else "")
                    }
                    playerViewModel.playStreamQueue(
                        audios = audios,
                        startIndex = safeStart,
                        videoIds = tracks.map { it.videoId }
                    ) { videoId ->
                        YouTubeRepository.getStreamUrl(videoId).getOrNull()
                    }
                    loadLyrics(startTrack.videoId)
                }
                .onFailure { error ->
                    _playbackError.value = error.message ?: "Error playing track"
                    playerViewModel.clearPreparingAudio()
                }
        }
    }

    private fun SongItem.toStreamableTrack() = StreamableTrack(
        videoId = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.name,
        durationMs = duration?.times(1000L) ?: 0L,
        thumbnailUrl = thumbnail
    )

    fun playTrack(
        track: StreamableTrack,
        playerViewModel: PlayerViewModel,
        queue: List<StreamableTrack>? = null
    ) {
        val list = queue ?: listOf(track)
        val index = queue
            ?.indexOfFirst { it.videoId == track.videoId }
            ?.takeIf { it >= 0 } ?: 0
        startPlayback(list, index, playerViewModel)
    }

    fun playArtistSong(
        song: SongItem,
        playerViewModel: PlayerViewModel,
        queueSongs: List<SongItem>? = null
    ) {
        val list = queueSongs?.map { it.toStreamableTrack() } ?: listOf(song.toStreamableTrack())
        val index = queueSongs
            ?.indexOfFirst { it.id == song.id }
            ?.takeIf { it >= 0 } ?: 0
        startPlayback(list, index, playerViewModel)
    }

    fun playAlbumSong(
        song: SongItem,
        playerViewModel: PlayerViewModel,
        queueSongs: List<SongItem>? = null
    ) {
        val list = queueSongs?.map { it.toStreamableTrack() } ?: listOf(song.toStreamableTrack())
        val index = queueSongs
            ?.indexOfFirst { it.id == song.id }
            ?.takeIf { it >= 0 } ?: 0
        startPlayback(list, index, playerViewModel)
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }
    
    private fun loadLyrics(videoId: String) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _currentLyrics.value = null
            
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getLyrics(videoId)
            }
            result
                .onSuccess { lyrics ->
                    _currentLyrics.value = lyrics
                }
            
            _isLoadingLyrics.value = false
        }
    }
    
    fun loadArtistDetails(browseId: String) {
        viewModelScope.launch {
            _isLoadingArtist.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getArtistDetails(browseId)
            }
            result
                .onSuccess { artistPage ->
                    _selectedArtist.value = artistPage
                    val songIds = artistPage.sections.flatMap { it.items }.filterIsInstance<SongItem>().map { it.id }
                    prefetchStreamUrls(songIds)
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error loading artist"
                }

            _isLoadingArtist.value = false
        }
    }

    fun clearSelectedArtist() {
        _selectedArtist.value = null
    }

    fun loadAlbumDetails(browseId: String) {
        viewModelScope.launch {
            _isLoadingAlbum.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getAlbumDetails(browseId)
            }
            result
                .onSuccess { albumPage ->
                    _selectedAlbum.value = albumPage
                    prefetchStreamUrls(albumPage.songs.map { it.id })
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error loading album"
                }

            _isLoadingAlbum.value = false
        }
    }

    fun clearSelectedAlbum() {
        _selectedAlbum.value = null
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
        _artistResults.value = null
        _errorMessage.value = null
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoadingHome.value = true
            _errorMessage.value = null

            // Historial en paralelo con home/explore (solo tiene sentido con sesión)
            val historyDeferred = async(Dispatchers.IO) { YouTubeRepository.getMusicHistory() }

            // Home y Explore EN PARALELO (antes en serie)
            coroutineScope {
                val homeDeferred = async(Dispatchers.IO) { YouTubeRepository.getHome() }
                val exploreDeferred = async(Dispatchers.IO) { YouTubeRepository.getExplore() }

                homeDeferred.await()
                    .onSuccess { home ->
                        _homePage.value = home
                        // Prefetch de la primera tanda de canciones del home
                        val songIds = home.sections.flatMap { it.items }
                            .filterIsInstance<SongItem>().map { it.id }
                        prefetchStreamUrls(songIds)
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message ?: "Error loading home content"
                    }

                exploreDeferred.await()
                    .onSuccess { explore ->
                        _explorePage.value = explore
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message ?: "Error loading explore content"
                    }
            }

            // El historial es opcional: si falla, la sección simplemente no aparece
            historyDeferred.await()
                .onSuccess { songs ->
                    _recentlyPlayed.value = songs
                    prefetchStreamUrls(songs.take(3).map { it.id })
                }
                .onFailure { _recentlyPlayed.value = emptyList() }

            _isLoadingHome.value = false
        }
    }

    fun loadUserPlaylists() {
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getUserPlaylists()
            }
            result
                .onSuccess { playlists ->
                    _userPlaylists.value = playlists
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error loading playlists"
                }

            _isLoadingPlaylists.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getPlaylistSongs(playlistId)
            }
            result
                .onSuccess { playlistPage ->
                    _selectedPlaylistSongs.value = playlistPage
                    // Prefetch de las primeras canciones de la playlist
                    prefetchStreamUrls(playlistPage.songs.map { it.id })
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error loading playlist songs"
                }

            _isLoadingPlaylists.value = false
        }
    }

    fun clearSelectedPlaylist() {
        _selectedPlaylistSongs.value = null
    }

    fun createYouTubePlaylist(title: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.createYouTubePlaylist(title)
            }
            result
                .onSuccess {
                    loadUserPlaylists()
                }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun addToYouTubePlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.addToPlaylist(playlistId, videoId)
            }
            result
                .onFailure { _errorMessage.value = it.message }
        }
    }
}