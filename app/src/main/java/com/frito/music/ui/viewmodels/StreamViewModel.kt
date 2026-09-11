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

/**
 * Filtro estricto para asegurar que un elemento es una pista de música real y no un
 * video general de YouTube (podcast, entrevista, vlog, gameplay, reacción, etc.)
 */
fun SongItem.isRealMusicTrack(): Boolean {
    // 1. Excluir contenido marcado por YouTube como Video de Usuario (UGC: vlogs, gameplays, memes)
    if (musicVideoType == "MUSIC_VIDEO_TYPE_UGC") return false

    // 2. Duración típica de canciones: entre 30 segundos y 12 minutos (720s)
    //    Elimina shorts/memes (<30s) y podcasts, mixes largos o gameplays (>12m)
    val dur = duration
    if (dur != null && (dur < 30 || dur > 720)) return false

    // 3. Debe tener al menos un artista con nombre válido
    if (artists.isEmpty() || artists.all { it.name.trim().isBlank() }) return false

    // 4. Palabras clave en el título que delatan que es un video general y no una canción
    val lowerTitle = title.lowercase()
    val nonMusicKeywords = listOf(
        "podcast", "episodio", "episode", "gameplay", "walkthrough",
        "reacción", "reaction", "tutorial", "unboxing", "review",
        "detrás de cámaras", "behind the scenes", "entrevista", "interview",
        "making of", "vlog", "documental", "documentary", "compilación",
        "compilation", "funny moments", "capítulo", "chapter", "tiktok"
    )
    if (nonMusicKeywords.any { lowerTitle.contains(it) }) return false

    // 5. Nombres de canal/artistas que no son musicales
    val lowerArtists = artists.joinToString(" ") { it.name.lowercase() }
    val nonMusicArtistKeywords = listOf("podcast", "gaming", "channel", "canal", "noticias", "news", "clips")
    if (nonMusicArtistKeywords.any { lowerArtists.contains(it) }) return false

    return true
}

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

    private val _playlistResults = MutableStateFlow<List<PlaylistItem>?>(null)
    val playlistResults: StateFlow<List<PlaylistItem>?> = _playlistResults.asStateFlow()

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
    private val HOME_CACHE_TTL_MS = 3 * 60 * 1000L // 3 minutos para refrescos oportunos
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
        val initialHistory = StreamHistoryManager.recentSongs.value.filter { it.isRealMusicTrack() }
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
        // Precarga en background automática en cuanto se abre la app
        loadHomeContent()
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
            _playlistResults.value = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            _isSearching.value = true
            _errorMessage.value = null

            // Canciones, artistas y playlists EN PARALELO
            coroutineScope {
                val songsDeferred = async(Dispatchers.IO) {
                    YouTubeRepository.search(query)
                }
                val artistsDeferred = async(Dispatchers.IO) {
                    YouTubeRepository.searchArtists(query)
                }
                val playlistsDeferred = async(Dispatchers.IO) {
                    YouTubeRepository.searchPlaylists(query)
                }

                val searchResult = songsDeferred.await()
                searchResult
                    .onSuccess { results ->
                        _searchResults.value = results
                        prefetchStreamUrls(results.map { it.videoId })
                    }
                    .onFailure { error ->
                        if (error !is kotlinx.coroutines.CancellationException) {
                            _errorMessage.value = error.message ?: "Error searching"
                        }
                        _searchResults.value = null
                    }

                val artistResult = artistsDeferred.await()
                artistResult
                    .onSuccess { artists ->
                        _artistResults.value = artists
                    }
                    .onFailure { _artistResults.value = null }

                val playlistResult = playlistsDeferred.await()
                playlistResult
                    .onSuccess { playlists ->
                        _playlistResults.value = playlists
                    }
                    .onFailure { _playlistResults.value = null }
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

        playerViewModel.setPreparingAudio(startTrack.toAudioFile(""), startTrack.videoId)

        viewModelScope.launch {
            // Resolución con cadena completa: YouTube validado → alternativas
            // (JioSaavn/Qobuz) para canciones bloqueadas por bot-detection
            val streamUrl = withContext(Dispatchers.IO) {
                playerViewModel.resolveStreamWithFallback(
                    videoId = startTrack.videoId,
                    title = startTrack.title,
                    artist = startTrack.artist
                )
            }
            if (!streamUrl.isNullOrEmpty()) {
                // Solo el track inicial trae URL; el resto queda pendiente ("")
                val audios = tracks.mapIndexed { i, t ->
                    t.toAudioFile(if (i == safeStart) streamUrl else "")
                }
                playerViewModel.playStreamQueue(
                    audios = audios,
                    startIndex = safeStart,
                    videoIds = tracks.map { it.videoId }
                ) { videoId ->
                    val queueTrack = tracks.firstOrNull { it.videoId == videoId }
                    playerViewModel.resolveStreamWithFallback(
                        videoId = videoId,
                        title = queueTrack?.title ?: "",
                        artist = queueTrack?.artist ?: ""
                    )
                }
                loadLyrics(startTrack.videoId)
            } else {
                _playbackError.value = "No se pudo reproducir desde ninguna fuente"
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

    private fun createSeedArtist(id: String, name: String) = ArtistItem(
        id = id,
        title = name,
        thumbnail = null,
        channelId = null,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null
    )

    fun recordPlayedSong(song: SongItem) {
        if (song.id.isBlank() || !song.isRealMusicTrack()) return
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
                    if (error !is kotlinx.coroutines.CancellationException) {
                        _errorMessage.value = error.message ?: "Error loading artist"
                    }
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
                    if (error !is kotlinx.coroutines.CancellationException) {
                        _errorMessage.value = error.message ?: "Error loading album"
                    }
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

    private var homeJob: Job? = null

    fun loadHomeContent(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        // Si no es refresco forzado y ya tenemos recomendaciones completas (> 1 shelf) y están recientes (<3 min), saltar
        if (!forceRefresh && _homeShelves.value.size > 1 && (now - lastHomeContentTime < HOME_CACHE_TTL_MS)) {
            return
        }

        // Si ya hay una carga en progreso y no se está forzando refresco, dejar que continúe
        if (!forceRefresh && homeJob?.isActive == true) {
            return
        }

        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _isLoadingHome.value = true
            _errorMessage.value = null

            try {
                // 1. Carga simultánea y paralela de las 4 fuentes base principales
                val homeDeferred = async(Dispatchers.IO) { YouTubeRepository.getHome() }
                val historyDeferred = async(Dispatchers.IO) { YouTubeRepository.getMusicHistory() }
                val likedDeferred = async(Dispatchers.IO) { YouTubeRepository.getLikedSongs() }
                val exploreDeferred = async(Dispatchers.IO) { YouTubeRepository.getExplore() }

                val home = homeDeferred.await().getOrNull()
                val remoteHistorySongs = historyDeferred.await().getOrDefault(emptyList())
                val localHistorySongs = StreamHistoryManager.recentSongs.value
                val historySongs = (localHistorySongs + remoteHistorySongs)
                    .filter { it.isRealMusicTrack() }
                    .distinctBy { it.id }

                val likedSongs = likedDeferred.await().getOrDefault(emptyList())
                    .filter { it.isRealMusicTrack() }
                val explore = exploreDeferred.await().getOrNull()

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

                // ── B1. "Vuelve a escuchar" (historial reciente sin videos no musicales)
                val recentlyPlayedShelf = if (historySongs.isNotEmpty()) {
                    HomeShelf(
                        id = "recently_played",
                        title = "Vuelve a escuchar",
                        items = historySongs.take(20)
                    )
                } else null

                // ── B2. "Recomendaciones para ti"
                // Tomamos canciones recomendadas directamente del Home de YouTube Music (pre-calculadas por Google)
                // y opcionalmente 1 sola radio si necesitamos complementar.
                val homeRecommendedSongs = home?.sections
                    ?.flatMap { it.items }
                    ?.filterIsInstance<SongItem>()
                    ?.filter { it.isRealMusicTrack() }
                    ?.filterNot { profile.knownVideoIds.contains(it.id) }
                    ?.distinctBy { it.id }
                    .orEmpty()

                val seedSong = (historySongs.firstOrNull() ?: likedSongs.firstOrNull())
                val radioSongs = if (homeRecommendedSongs.size < 15 && seedSong != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            YouTubeRepository.getSongRadio(seedSong.id).getOrNull().orEmpty()
                        }.getOrDefault(emptyList())
                            .filter { it.isRealMusicTrack() }
                            .filterNot { profile.knownVideoIds.contains(it.id) }
                    }
                } else emptyList()

                val finalRecommendations = (homeRecommendedSongs + radioSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                val recommendationsShelf = if (finalRecommendations.isNotEmpty()) {
                    HomeShelf(
                        id = "recommendations",
                        title = "Recomendaciones para ti",
                        items = finalRecommendations
                    )
                } else null

                // ── B3. "Artistas para ti"
                // Extraer semillas de favoritos, historial de reproducción y likes
                val favArtistSeeds = favoriteArtists.filter { it.id.isNotBlank() }
                val historyArtistNames = historySongs.flatMap { it.artists }
                    .map { it.name }
                    .filter { it.isNotBlank() }
                val likedArtistNames = likedSongs.flatMap { it.artists }
                    .map { it.name }
                    .filter { it.isNotBlank() }

                val seedArtistIds = (favArtistSeeds.map { it.id } +
                    historySongs.flatMap { it.artists }.mapNotNull { it.id } +
                    likedSongs.flatMap { it.artists }.mapNotNull { it.id })
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)

                val seedArtistNames = (favArtistSeeds.map { it.title } + historyArtistNames + likedArtistNames)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)

                // 1. Obtener artistas relacionados de YouTube Music (traen thumbnail garantizado)
                val relatedArtists: List<ArtistItem> = if (seedArtistIds.isNotEmpty()) {
                    coroutineScope {
                        seedArtistIds.map { id ->
                            async(Dispatchers.IO) {
                                runCatching {
                                    YouTubeRepository.getRelatedArtists(id).getOrNull().orEmpty()
                                }.getOrDefault(emptyList())
                            }
                        }.map { it.await() }.flatten()
                    }
                } else emptyList()

                // 2. Si relatedArtists tiene pocos elementos, buscar artistas por nombre (traen thumbnail oficial de canal)
                val searchedArtists: List<ArtistItem> = if (relatedArtists.size < 12 && seedArtistNames.isNotEmpty()) {
                    coroutineScope {
                        seedArtistNames.take(2).map { name ->
                            async(Dispatchers.IO) {
                                runCatching {
                                    YouTubeRepository.searchArtists(name).getOrNull().orEmpty()
                                }.getOrDefault(emptyList())
                            }
                        }.map { it.await() }.flatten()
                    }
                } else emptyList()

                // 3. Artistas directos de las secciones de Home (traen thumbnail garantizado)
                val directHomeArtists: List<ArtistItem> = home?.sections
                    ?.flatMap { it.items }
                    ?.filterIsInstance<ArtistItem>()
                    .orEmpty()

                // 4. Consolidar ÚNICAMENTE artistas con thumbnail válido (NUNCA null o vacío)
                val candidateArtists: List<ArtistItem> = (relatedArtists + searchedArtists + directHomeArtists)
                    .filter { !it.thumbnail.isNullOrBlank() }
                    .filterNot { favoriteArtistIds.contains(it.id) || favoriteArtistNames.contains(it.title.trim().lowercase()) }
                    .distinctBy { it.id }

                val popularArtistsShelf = if (candidateArtists.isNotEmpty()) {
                    HomeShelf(
                        id = "popular_artists",
                        title = "Artistas para ti",
                        items = candidateArtists.shuffled().take(20)
                    )
                } else null

                // ── Secciones adicionales del home de YouTube Music (con filtro estricto isRealMusicTrack)
                val additionalShelves = home?.sections?.mapNotNull { section ->
                    val validItems = section.items.mapNotNull { item ->
                        when (item) {
                            is SongItem -> if (item.isRealMusicTrack()) item else null
                            is AlbumItem -> item
                            is ArtistItem -> if (!favoriteArtistIds.contains(item.id)) item else null
                            else -> null
                        }
                    }
                    val lowerTitle = section.title.lowercase()
                    if (validItems.isNotEmpty() &&
                        !lowerTitle.contains("vuelve a escuchar") &&
                        !lowerTitle.contains("escuchado recientemente") &&
                        !lowerTitle.contains("quick picks") &&
                        !lowerTitle.contains("artistas para ti") &&
                        !lowerTitle.contains("recomendaciones") &&
                        !lowerTitle.contains("álbumes y sencillos") &&
                        !lowerTitle.contains("albumes y sencillos") &&
                        !lowerTitle.contains("albums & singles")) {
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

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Si la corutina se cancela por refresco o navegación, no es un error para el usuario
                throw e
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
            val likedSeeds = _tasteProfile.value?.likedSongs?.flatMap { it.artists }?.mapNotNull { it.id }?.filter { it.isNotBlank() }.orEmpty()
            val exploreSeeds = _explorePage.value?.newReleaseAlbums?.flatMap { it.artists.orEmpty() }?.mapNotNull { it.id }.orEmpty()
            val homeSeeds = _homePage.value?.sections?.flatMap { it.items }?.filterIsInstance<ArtistItem>()?.map { it.id }.orEmpty()

            synchronized(artistDiscoveryQueue) {
                artistDiscoveryQueue.clear()
                seenArtistIds.clear()
                seenArtistIds.addAll(favIds) // Artistas favoritos no deben duplicarse aquí

                val seeds = (favs.map { it.id } + historySeeds + likedSeeds + exploreSeeds + homeSeeds)
                    .distinct()
                    .filter { it.isNotBlank() }
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
                    if (!isFav && !seenArtistIds.contains(artist.id) && !artist.thumbnail.isNullOrBlank()) {
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
                        if (!isFav && !seenArtistIds.contains(artist.id) && !artist.thumbnail.isNullOrBlank()) {
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
                            if (!isFav && !seenArtistIds.contains(artist.id) && !artist.thumbnail.isNullOrBlank()) {
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

    suspend fun findArtistIdByName(artistName: String): String? {
        val cleanName = artistName
            .replace(Regex("(?i)\\s*-\\s*topic\\b"), "")
            .replace(Regex("(?i)\\s*vevo\\b"), "")
            .trim()
        if (cleanName.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val result = com.music.innertube.YouTube.search(cleanName, com.music.innertube.YouTube.SearchFilter.FILTER_ARTIST)
            result.getOrNull()?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()?.id
        }
    }

    suspend fun getRelatedArtists(artistName: String, currentArtistId: String): List<ArtistItem> {
        val cleanName = artistName
            .replace(Regex("(?i)\\s*-\\s*topic\\b"), "")
            .replace(Regex("(?i)\\s*vevo\\b"), "")
            .trim()
        if (cleanName.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val result = com.music.innertube.YouTube.search(cleanName, com.music.innertube.YouTube.SearchFilter.FILTER_ARTIST)
                result.getOrNull()?.items?.filterIsInstance<ArtistItem>()
                    ?.filter { it.id != currentArtistId && !it.thumbnail.isNullOrBlank() }
                    ?.take(15) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}