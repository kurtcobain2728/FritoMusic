package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.ArtistPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamViewModel : ViewModel() {
    
    private val _searchResults = MutableStateFlow<List<StreamableTrack>?>(null)
    val searchResults: StateFlow<List<StreamableTrack>?> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
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

    private var searchJob: Job? = null
    
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

            YouTubeRepository.search(query)
                .onSuccess { results ->
                    _searchResults.value = results
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error searching"
                    _searchResults.value = null
                }

            YouTubeRepository.searchArtists(query)
                .onSuccess { artists ->
                    _artistResults.value = artists
                }
                .onFailure { _artistResults.value = null }

            _isSearching.value = false
        }
    }
    
    fun playTrack(track: StreamableTrack, playerViewModel: PlayerViewModel) {
        viewModelScope.launch {
            _errorMessage.value = null
            
            YouTubeRepository.getStreamUrl(track.videoId)
                .onSuccess { streamUrl ->
                    val audioFile = track.toAudioFile(streamUrl)
                    playerViewModel.playAudios(listOf(audioFile), 0)
                    
                    // Load lyrics in background
                    loadLyrics(track.videoId)
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error playing track"
                }
        }
    }
    
    private fun loadLyrics(videoId: String) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _currentLyrics.value = null
            
            YouTubeRepository.getLyrics(videoId)
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

            YouTubeRepository.getArtistDetails(browseId)
                .onSuccess { artistPage ->
                    _selectedArtist.value = artistPage
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

    fun playArtistSong(song: SongItem, playerViewModel: PlayerViewModel) {
        viewModelScope.launch {
            _errorMessage.value = null

            val streamableTrack = StreamableTrack(
                videoId = song.id,
                title = song.title,
                artist = song.artists.joinToString(", ") { it.name },
                album = song.album?.name,
                durationMs = song.duration?.times(1000L) ?: 0L,
                thumbnailUrl = song.thumbnail
            )

            YouTubeRepository.getStreamUrl(song.id)
                .onSuccess { streamUrl ->
                    val audioFile = streamableTrack.toAudioFile(streamUrl)
                    playerViewModel.playAudios(listOf(audioFile), 0)
                    loadLyrics(song.id)
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Error playing track"
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = null
        _artistResults.value = null
        _errorMessage.value = null
    }
}