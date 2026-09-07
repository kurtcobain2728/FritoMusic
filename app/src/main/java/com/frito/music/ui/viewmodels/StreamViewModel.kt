package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.PlaylistPage
import com.frito.music.data.models.HomeShelf
import com.frito.music.data.models.TasteProfile
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _homeShelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    val homeShelves: StateFlow<List<HomeShelf>> = _homeShelves.asStateFlow()

    private val _tasteProfile = MutableStateFlow<TasteProfile?>(null)
    val tasteProfile: StateFlow<TasteProfile?> = _tasteProfile.asStateFlow()

    private var lastHomeContentTime = 0L
    private val HOME_CACHE_TTL_MS = 45 * 60 * 1000L
    private val recommendationSemaphore = Semaphore(3)

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

    fun loadHomeContent(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _homeShelves.value.isNotEmpty() && (now - lastHomeContentTime < HOME_CACHE_TTL_MS)) {
            return
        }

        viewModelScope.launch {
            _isLoadingHome.value = true
            _errorMessage.value = null

            try {
                // 1. Carga en paralelo de fuentes base
                val historyDeferred = async(Dispatchers.IO) { YouTubeRepository.getMusicHistory() }
                val likedDeferred = async(Dispatchers.IO) { YouTubeRepository.getLikedSongs() }
                val homeDeferred = async(Dispatchers.IO) { YouTubeRepository.getHome() }
                val exploreDeferred = async(Dispatchers.IO) { YouTubeRepository.getExplore() }

                val historySongs = historyDeferred.await().getOrDefault(emptyList())
                val likedSongs = likedDeferred.await().getOrDefault(emptyList())
                val homeResult = homeDeferred.await()
                val exploreResult = exploreDeferred.await()

                val home = homeResult.getOrNull()
                val explore = exploreResult.getOrNull()

                _homePage.value = home
                _explorePage.value = explore
                _recentlyPlayed.value = historySongs

                // 2. Construir perfil de gustos (TasteProfile)
                val likedArtistIds = likedSongs.flatMap { it.artists }
                    .mapNotNull { it.id }
                    .filter { it.isNotBlank() }
                    .toSet()
                val likedAlbumIds = likedSongs.mapNotNull { it.album?.id }
                    .filter { it.isNotBlank() }
                    .toSet()
                val recentSongIds = historySongs.map { it.id }

                val profile = TasteProfile(
                    likedSongs = likedSongs,
                    likedArtistIds = likedArtistIds,
                    likedAlbumIds = likedAlbumIds,
                    recentSongIds = recentSongIds
                )
                _tasteProfile.value = profile

                // 3. Generación de cada Shelf

                // ── B1. "Vuelve a escuchar" (historial reciente)
                val recentlyPlayedShelf = if (historySongs.isNotEmpty()) {
                    HomeShelf(
                        id = "recently_played",
                        title = "Vuelve a escuchar",
                        items = historySongs.distinctBy { it.id }.take(20)
                    )
                } else null

                // ── B2. "Recomendaciones para ti" (basadas en radio/automix de semillas)
                // Prioridad de semillas: 3 más recientes del historial + 2 de likes
                val recentSeeds = historySongs.distinctBy { it.id }.take(3)
                val likedSeeds = likedSongs.filterNot { l -> recentSeeds.any { it.id == l.id } }.take(2)
                val seedSongs = (recentSeeds + likedSeeds).take(5)

                val recommendedSongs = if (seedSongs.isNotEmpty()) {
                    val songScoreMap = mutableMapOf<String, Pair<SongItem, Int>>()
                    coroutineScope {
                        val jobs = seedSongs.map { seed ->
                            async(Dispatchers.IO) {
                                recommendationSemaphore.withPermit {
                                    runCatching {
                                        YouTubeRepository.getSongRadio(seed.id).getOrNull().orEmpty()
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                        jobs.map { it.await() }.forEach { list ->
                            list.forEach { song ->
                                // Filtrar las canciones que el usuario ya conoce
                                if (!profile.knownVideoIds.contains(song.id)) {
                                    val current = songScoreMap[song.id]
                                    val score = (current?.second ?: 0) + 1
                                    songScoreMap[song.id] = Pair(song, score)
                                }
                            }
                        }
                    }
                    songScoreMap.values
                        .sortedByDescending { it.second }
                        .map { it.first }
                        .distinctBy { it.id }
                        .take(20)
                } else {
                    emptyList()
                }

                // Fallback para recomendaciones: si no hay semillas o no hubo resultados, usar canciones del home
                val finalRecommendations = if (recommendedSongs.isNotEmpty()) {
                    recommendedSongs
                } else {
                    home?.sections
                        ?.flatMap { it.items }
                        ?.filterIsInstance<SongItem>()
                        ?.filterNot { profile.knownVideoIds.contains(it.id) }
                        ?.distinctBy { it.id }
                        ?.take(20)
                        ?: emptyList()
                }

                val recommendationsShelf = if (finalRecommendations.isNotEmpty()) {
                    HomeShelf(
                        id = "recommendations",
                        title = "Recomendaciones para ti",
                        items = finalRecommendations
                    )
                } else null

                // ── B3. "Álbumes y sencillos populares" (explore + cruce con artistas que le gustan)
                val newReleases = explore?.newReleaseAlbums.orEmpty()
                val popularAlbumsShelf = if (newReleases.isNotEmpty()) {
                    val (fromFavoriteArtists, otherReleases) = newReleases.partition { album ->
                        likedArtistIds.isNotEmpty() && album.artists?.any { it.id != null && likedArtistIds.contains(it.id) } == true
                    }
                    val sortedAlbums = (fromFavoriteArtists + otherReleases).distinctBy { it.browseId }.take(20)
                    HomeShelf(
                        id = "popular_albums",
                        title = "Álbumes y sencillos populares",
                        items = sortedAlbums
                    )
                } else null

                // ── B4. "Artistas para ti" (artistas relacionados con likes, o trending para cuentas nuevas)
                val popularArtistsShelf = if (likedArtistIds.isNotEmpty()) {
                    val seedArtists = likedArtistIds.take(3)
                    val artistScoreMap = mutableMapOf<String, Pair<ArtistItem, Int>>()
                    coroutineScope {
                        val jobs = seedArtists.map { artistId ->
                            async(Dispatchers.IO) {
                                recommendationSemaphore.withPermit {
                                    runCatching {
                                        YouTubeRepository.getRelatedArtists(artistId).getOrNull().orEmpty()
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                        jobs.map { it.await() }.forEach { list ->
                            list.forEach { artist ->
                                if (!likedArtistIds.contains(artist.id)) {
                                    val current = artistScoreMap[artist.id]
                                    val score = (current?.second ?: 0) + 1
                                    artistScoreMap[artist.id] = Pair(artist, score)
                                }
                            }
                        }
                    }
                    val sortedArtists = artistScoreMap.values
                        .sortedByDescending { it.second }
                        .map { it.first }
                        .distinctBy { it.id }
                        .take(15)

                    if (sortedArtists.isNotEmpty()) {
                        HomeShelf(
                            id = "popular_artists",
                            title = "Artistas para ti",
                            items = sortedArtists
                        )
                    } else null
                } else {
                    val homeArtists = home?.sections
                        ?.flatMap { it.items }
                        ?.filterIsInstance<ArtistItem>()
                        ?.distinctBy { it.id }
                        ?.take(15)
                        .orEmpty()
                    if (homeArtists.isNotEmpty()) {
                        HomeShelf(
                            id = "popular_artists",
                            title = "Artistas para ti",
                            items = homeArtists
                        )
                    } else null
                }

                // ── Secciones adicionales del home de YouTube Music (evitando duplicar estantes)
                val additionalShelves = home?.sections?.mapNotNull { section ->
                    val validItems = section.items.filter { it is SongItem || it is AlbumItem || it is ArtistItem }
                    val lowerTitle = section.title.lowercase()
                    if (validItems.isNotEmpty() &&
                        !lowerTitle.contains("vuelve a escuchar") &&
                        !lowerTitle.contains("escuchado recientemente") &&
                        !lowerTitle.contains("quick picks") &&
                        !lowerTitle.contains("artistas para ti")) {
                        HomeShelf(
                            id = "yt_section_${section.title.hashCode()}",
                            title = section.title,
                            items = validItems
                        )
                    } else null
                }.orEmpty()

                // Consolidar estantes
                val allShelves = listOfNotNull(
                    recentlyPlayedShelf,
                    recommendationsShelf,
                    popularAlbumsShelf,
                    popularArtistsShelf
                ) + additionalShelves

                _homeShelves.value = allShelves
                lastHomeContentTime = System.currentTimeMillis()

                // Prefetch de URLs de reproducción de las canciones iniciales para reproducción instantánea
                val prefetchSongIds = (finalRecommendations.take(3) + historySongs.take(3)).map { it.id }.distinct()
                if (prefetchSongIds.isNotEmpty()) {
                    prefetchStreamUrls(prefetchSongIds)
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al cargar contenido de Stream"
            } finally {
                _isLoadingHome.value = false
            }
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