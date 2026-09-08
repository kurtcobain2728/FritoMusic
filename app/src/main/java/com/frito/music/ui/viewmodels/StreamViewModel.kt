package com.frito.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.HomeShelf
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.models.TasteProfile
import com.frito.music.data.network.yt.YouTubeRepository
import com.frito.music.data.repository.FavoriteArtistsManager
import com.frito.music.data.repository.StreamHistoryManager
import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
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

    private val artistPagesCache = mutableMapOf<String, ArtistPage>()
    private val albumPagesCache = mutableMapOf<String, AlbumPage>()
    private val playlistPagesCache = mutableMapOf<String, PlaylistPage>()

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

    // Artistas paginados para "Ver todo" (Artistas para ti con scroll infinito)
    private val _paginatedArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val paginatedArtists: StateFlow<List<ArtistItem>> = _paginatedArtists.asStateFlow()

    private val _isLoadingMoreArtists = MutableStateFlow(false)
    val isLoadingMoreArtists: StateFlow<Boolean> = _isLoadingMoreArtists.asStateFlow()

    private val _isRefreshingArtists = MutableStateFlow(false)
    val isRefreshingArtists: StateFlow<Boolean> = _isRefreshingArtists.asStateFlow()

    private val artistDiscoveryQueue = ArrayDeque<String>()
    private val seenArtistIds = mutableSetOf<String>()

    private var searchJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        val initialHistory = StreamHistoryManager.recentSongs.value
        if (initialHistory.isNotEmpty()) {
            _recentlyPlayed.value = initialHistory
            _homeShelves.value = listOf(
                HomeShelf(
                    id = "recently_played",
                    title = "Vuelve a escuchar",
                    items = initialHistory
                )
            )
        }
    }

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
        
        // Registrar de inmediato en el historial de Stream para que Vuelve a escuchar se actualice al instante
        recordPlayedSong(startTrack.toSongItem())

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

    private fun StreamableTrack.toSongItem() = SongItem(
        id = videoId,
        title = title,
        artists = listOf(Artist(name = artist, id = null)),
        album = album?.let { Album(name = it, id = "") },
        duration = (durationMs / 1000).toInt(),
        thumbnail = thumbnailUrl,
        endpoint = null
    )

    fun recordPlayedSong(song: SongItem) {
        if (song.id.isBlank()) return
        StreamHistoryManager.recordSong(song)
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        val updated = current.take(25)
        _recentlyPlayed.value = updated

        // Actualizar de inmediato el shelf "recently_played" en homeShelves para reflejo instantáneo en UI
        val currentShelves = _homeShelves.value.toMutableList()
        val shelfIndex = currentShelves.indexOfFirst { it.id == "recently_played" }
        val newShelf = HomeShelf(
            id = "recently_played",
            title = "Vuelve a escuchar",
            items = updated
        )
        if (shelfIndex >= 0) {
            currentShelves[shelfIndex] = newShelf
        } else {
            currentShelves.add(0, newShelf)
        }
        _homeShelves.value = currentShelves
    }

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
    
    fun loadArtistDetails(browseId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cached = artistPagesCache[browseId]
            if (cached != null) {
                _selectedArtist.value = cached
                _isLoadingArtist.value = false
                return
            }
        }

        viewModelScope.launch {
            _isLoadingArtist.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getArtistDetails(browseId)
            }
            result
                .onSuccess { artistPage ->
                    artistPagesCache[browseId] = artistPage
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

    fun loadAlbumDetails(browseId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cached = albumPagesCache[browseId]
            if (cached != null) {
                _selectedAlbum.value = cached
                _isLoadingAlbum.value = false
                return
            }
        }

        viewModelScope.launch {
            _isLoadingAlbum.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getAlbumDetails(browseId)
            }
            result
                .onSuccess { albumPage ->
                    albumPagesCache[browseId] = albumPage
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

                val remoteHistorySongs = historyDeferred.await().getOrDefault(emptyList())
                val localHistorySongs = StreamHistoryManager.recentSongs.value
                val historySongs = (localHistorySongs + remoteHistorySongs).distinctBy { it.id }
                val likedSongs = likedDeferred.await().getOrDefault(emptyList())
                val homeResult = homeDeferred.await()
                val exploreResult = exploreDeferred.await()

                val home = homeResult.getOrNull()
                val explore = exploreResult.getOrNull()

                _homePage.value = home
                _explorePage.value = explore
                _recentlyPlayed.value = historySongs

                // 2. Construir perfil de gustos (TasteProfile)
                val favoriteArtists = FavoriteArtistsManager.favoriteArtists.value
                val favoriteArtistIds = favoriteArtists.map { it.id }.filter { it.isNotBlank() }.toSet()
                val favoriteArtistNames = favoriteArtists.map { it.title.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

                val likedArtistIds = (favoriteArtistIds + likedSongs.flatMap { it.artists }
                    .mapNotNull { it.id }
                    .filter { it.isNotBlank() }).toSet()
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

                // ── B2. "Recomendaciones para ti" (basadas en radio/automix de semillas rotativas)
                val allCandidateSeeds = (historySongs.distinctBy { it.id } + likedSongs.distinctBy { it.id }).distinctBy { it.id }
                val seedSongs = if (allCandidateSeeds.size > 5) {
                    allCandidateSeeds.shuffled().take(5)
                } else {
                    allCandidateSeeds
                }

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
                        .shuffled()
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
                        ?.shuffled()
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

                // ── B3. "Álbumes y sencillos populares" (Discografía de artistas favoritos y más escuchados)
                val favArtistSeeds = favoriteArtists.filter { it.id.isNotBlank() }
                val historyArtistCounts = historySongs
                    .flatMap { it.artists }
                    .filter { !it.id.isNullOrBlank() }
                    .groupingBy { it.id!! }
                    .eachCount()
                val topHistoryArtistIds = historyArtistCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .filterNot { favId -> favArtistSeeds.any { it.id == favId } }
                    .take(5)

                val targetArtistIds = (favArtistSeeds.map { it.id } + topHistoryArtistIds).distinct().take(6)

                val userArtistAlbums = if (targetArtistIds.isNotEmpty()) {
                    coroutineScope {
                        val albumJobs = targetArtistIds.map { artistId ->
                            val knownName = favArtistSeeds.firstOrNull { it.id == artistId }?.title
                            async(Dispatchers.IO) {
                                recommendationSemaphore.withPermit {
                                    runCatching {
                                        YouTubeRepository.getArtistAlbums(artistId, knownName).getOrNull().orEmpty()
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                        albumJobs.map { it.await() }.flatten().distinctBy { it.browseId }
                    }
                } else {
                    emptyList()
                }

                val finalAlbums = if (userArtistAlbums.isNotEmpty()) {
                    // Mezclar para variedad en cada refresco manteniendo relevancia absoluta
                    userArtistAlbums.shuffled().take(20)
                } else {
                    // Fallback exclusivo para cuentas nuevas sin favoritos ni historial
                    (explore?.newReleaseAlbums.orEmpty() + home?.sections?.flatMap { it.items }?.filterIsInstance<AlbumItem>().orEmpty())
                        .distinctBy { it.browseId }
                        .take(20)
                }

                val popularAlbumsShelf = if (finalAlbums.isNotEmpty()) {
                    HomeShelf(
                        id = "popular_albums",
                        title = "Álbumes y sencillos populares",
                        items = finalAlbums
                    )
                } else null

                // ── B4. "Artistas para ti" (artistas similares/relacionados, EXCLUYENDO favoritos guardados)
                val allSeedPool = (favoriteArtistIds + likedArtistIds + topHistoryArtistIds).distinct()
                val activeSeedArtistIds = if (allSeedPool.size > 4) allSeedPool.shuffled().take(4) else allSeedPool

                val popularArtistsShelf = if (activeSeedArtistIds.isNotEmpty()) {
                    val artistScoreMap = mutableMapOf<String, Pair<ArtistItem, Int>>()
                    coroutineScope {
                        val jobs = activeSeedArtistIds.map { artistId ->
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
                                val isFav = favoriteArtistIds.contains(artist.id) ||
                                            favoriteArtistNames.contains(artist.title.trim().lowercase())
                                if (!isFav && !activeSeedArtistIds.contains(artist.id)) {
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
                        .filterNot { favoriteArtistIds.contains(it.id) || favoriteArtistNames.contains(it.title.trim().lowercase()) }
                        .distinctBy { it.id }
                        .shuffled()
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
                        ?.filterNot { favoriteArtistIds.contains(it.id) || favoriteArtistNames.contains(it.title.trim().lowercase()) }
                        ?.distinctBy { it.id }
                        ?.shuffled()
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

                // Inicializar artistas paginados en background para "Ver todo"
                if (_paginatedArtists.value.isEmpty() || forceRefresh) {
                    initPaginatedArtists(forceRefresh = forceRefresh)
                }

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al cargar contenido de Stream"
            } finally {
                _isLoadingHome.value = false
            }
        }
    }

    /**
     * Inicializa la lista paginada de "Artistas para ti" con artistas semilla y recomendaciones sin límite.
     */
    fun initPaginatedArtists(forceRefresh: Boolean = false) {
        if (!forceRefresh && _paginatedArtists.value.isNotEmpty()) return

        viewModelScope.launch {
            _isRefreshingArtists.value = true
            val favs = FavoriteArtistsManager.favoriteArtists.value
            val favIds = favs.map { it.id }.filter { it.isNotBlank() }.toSet()
            val historySeeds = _recentlyPlayed.value.flatMap { it.artists }.mapNotNull { it.id }.filter { it.isNotBlank() }
            val homeSeeds = _homePage.value?.sections?.flatMap { it.items }?.filterIsInstance<ArtistItem>()?.map { it.id }.orEmpty()

            synchronized(artistDiscoveryQueue) {
                artistDiscoveryQueue.clear()
                seenArtistIds.clear()
                seenArtistIds.addAll(favIds) // Artistas favoritos no deben recomendarse

                val seeds = (favs.map { it.id } + historySeeds + homeSeeds).distinct().filter { it.isNotBlank() }
                artistDiscoveryQueue.addAll(seeds.shuffled())
            }

            val initialList = mutableListOf<ArtistItem>()
            var attempts = 0
            while (initialList.size < 18 && attempts < 6) {
                attempts++
                val seed = synchronized(artistDiscoveryQueue) {
                    if (artistDiscoveryQueue.isNotEmpty()) artistDiscoveryQueue.removeFirst() else null
                } ?: break

                val related = withContext(Dispatchers.IO) {
                    recommendationSemaphore.withPermit {
                        YouTubeRepository.getRelatedArtists(seed).getOrDefault(emptyList())
                    }
                }
                related.forEach { artist ->
                    val isFav = FavoriteArtistsManager.isFavorite(artist.id)
                    if (!isFav && !seenArtistIds.contains(artist.id)) {
                        seenArtistIds.add(artist.id)
                        initialList.add(artist)
                        synchronized(artistDiscoveryQueue) {
                            artistDiscoveryQueue.addLast(artist.id)
                        }
                    }
                }
            }

            _paginatedArtists.value = initialList
            _isRefreshingArtists.value = false
        }
    }

    /**
     * Carga más artistas similares de manera infinita a medida que el usuario hace scroll hacia abajo.
     */
    fun loadMoreArtists() {
        if (_isLoadingMoreArtists.value || _isRefreshingArtists.value) return

        viewModelScope.launch {
            _isLoadingMoreArtists.value = true

            val newArtists = mutableListOf<ArtistItem>()
            var attempts = 0
            while (newArtists.size < 12 && attempts < 5) {
                attempts++
                val seed = synchronized(artistDiscoveryQueue) {
                    if (artistDiscoveryQueue.isNotEmpty()) artistDiscoveryQueue.removeFirst() else null
                }

                if (seed != null) {
                    val related = withContext(Dispatchers.IO) {
                        recommendationSemaphore.withPermit {
                            YouTubeRepository.getRelatedArtists(seed).getOrDefault(emptyList())
                        }
                    }
                    related.forEach { artist ->
                        val isFav = FavoriteArtistsManager.isFavorite(artist.id)
                        if (!isFav && !seenArtistIds.contains(artist.id)) {
                            seenArtistIds.add(artist.id)
                            newArtists.add(artist)
                            synchronized(artistDiscoveryQueue) {
                                artistDiscoveryQueue.addLast(artist.id)
                            }
                        }
                    }
                } else {
                    // Si se agota la cola, buscar artistas similares usando nombres de los artistas ya descubiertos
                    val lastKnown = _paginatedArtists.value.takeLast(3).map { it.title }
                    for (query in lastKnown) {
                        val searchResults = withContext(Dispatchers.IO) {
                            YouTubeRepository.searchArtists(query).getOrDefault(emptyList())
                        }
                        searchResults.forEach { artist ->
                            val isFav = FavoriteArtistsManager.isFavorite(artist.id)
                            if (!isFav && !seenArtistIds.contains(artist.id)) {
                                seenArtistIds.add(artist.id)
                                newArtists.add(artist)
                                synchronized(artistDiscoveryQueue) {
                                    artistDiscoveryQueue.addLast(artist.id)
                                }
                            }
                        }
                    }
                    break
                }
            }

            if (newArtists.isNotEmpty()) {
                _paginatedArtists.value = _paginatedArtists.value + newArtists
            }
            _isLoadingMoreArtists.value = false
        }
    }

    /**
     * Refresca la lista de artistas recomendados paginados.
     */
    fun refreshRecommendedArtists() {
        initPaginatedArtists(forceRefresh = true)
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

    fun loadPlaylistSongs(playlistId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cached = playlistPagesCache[playlistId]
            if (cached != null) {
                _selectedPlaylistSongs.value = cached
                _isLoadingPlaylists.value = false
                return
            }
        }

        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            _errorMessage.value = null

            val result = withContext(Dispatchers.IO) {
                YouTubeRepository.getPlaylistSongs(playlistId)
            }
            result
                .onSuccess { playlistPage ->
                    playlistPagesCache[playlistId] = playlistPage
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