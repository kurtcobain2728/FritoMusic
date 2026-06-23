package com.frito.music.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.frito.music.extensions.ExtensionManager
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.frito.music.extensions.engine.SearchResult
import com.frito.music.extensions.engine.TrackResult
import com.frito.music.extensions.engine.AlbumResult
import com.frito.music.extensions.engine.ArtistResult
import com.frito.music.extensions.engine.ExtensionEngine
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val extensionManager = ExtensionManager(application)

    private val _installedServers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val installedServers: StateFlow<List<Pair<String, String>>> = _installedServers.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    private val _selectedQuality = MutableStateFlow("320kbps")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _availableQualities = MutableStateFlow(listOf("128kbps", "320kbps"))
    val availableQualities: StateFlow<List<String>> = _availableQualities.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Search state
    private val _searchResults = MutableStateFlow<SearchResult?>(null)
    val searchResults: StateFlow<SearchResult?> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var activeEngine: ExtensionEngine? = null

    init {
        loadServers()
    }

    fun loadServers() {
        val servers = extensionManager.getInstalledExtensionNames()
        _installedServers.value = servers
        if (servers.isNotEmpty() && _selectedServerId.value == null) {
            selectServer(servers.first().first)
        }
    }

    fun selectServer(id: String) {
        _selectedServerId.value = id

        // Calidades según servidor
        when {
            id.contains("spotify", ignoreCase = true) -> {
                _availableQualities.value = listOf("128kbps", "160kbps", "320kbps")
                _selectedQuality.value = "320kbps"
            }
            id.contains("youtube", ignoreCase = true) -> {
                _availableQualities.value = listOf("128kbps", "256kbps")
                _selectedQuality.value = "128kbps"
            }
            id.contains("deezer", ignoreCase = true) -> {
                _availableQualities.value = listOf("128kbps", "320kbps")
                _selectedQuality.value = "320kbps"
            }
            id.contains("tidal", ignoreCase = true) -> {
                _availableQualities.value = listOf("128kbps", "320kbps", "FLAC", "Hi-Res")
                _selectedQuality.value = "FLAC"
            }
            id.contains("qobuz", ignoreCase = true) -> {
                _availableQualities.value = listOf("320kbps", "FLAC", "Hi-Res")
                _selectedQuality.value = "FLAC"
            }
            else -> {
                _availableQualities.value = listOf("128kbps", "320kbps")
                _selectedQuality.value = "320kbps"
            }
        }

        // Clear search when switching server
        _searchResults.value = null
        _searchQuery.value = ""

        initEngine(id)
    }

    private fun initEngine(extensionId: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    activeEngine?.destroy()
                    Log.d("DownloadViewModel", "Initializing engine for $extensionId")
                    activeEngine = ExtensionEngine(getApplication(), extensionId)
                }
            } catch (e: Throwable) {
                Log.e("DownloadViewModel", "Error loading extension $extensionId", e)
                _errorMessage.value = "Error al cargar la extensión: ${e.message}"
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = null
            _searchQuery.value = ""
            return
        }

        if (_searchQuery.value == query && _searchResults.value != null) {
            return
        }

        _searchQuery.value = query
        viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null
            try {
                val results = withContext(Dispatchers.IO) {
                    withTimeout(20000L) {
                        var extResults = activeEngine?.performSearch(query)
                        if (extResults == null) {
                            extResults = SearchResult(emptyList(), emptyList(), emptyList())
                        }
                        
                        if (extResults.tracks.isEmpty() || extResults.albums.isEmpty() || extResults.artists.isEmpty()) {
                            Log.d("DownloadViewModel", "Extension missing some results, merging with Deezer API")
                            val deezerResults = searchNativeDeezer(query)
                            extResults = SearchResult(
                                tracks = extResults.tracks.ifEmpty { deezerResults.tracks },
                                albums = extResults.albums.ifEmpty { deezerResults.albums },
                                artists = extResults.artists.ifEmpty { deezerResults.artists }
                            )
                        }
                        extResults
                    }
                }
                Log.d("DownloadViewModel", "Search '$query': ${results.tracks.size} tracks")
                _searchResults.value = results
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("DownloadViewModel", "Search timeout for '$query'")
                _searchResults.value = null
                _errorMessage.value = "Búsqueda tardó demasiado."
            } catch (e: Throwable) {
                Log.e("DownloadViewModel", "Search error for '$query'", e)
                _searchResults.value = null
                _errorMessage.value = "Error en la búsqueda: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun searchNativeDeezer(query: String): SearchResult {
        val tracks = mutableListOf<TrackResult>()
        val albums = mutableListOf<AlbumResult>()
        val artists = mutableListOf<ArtistResult>()
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.deezer.com/search?q=$encodedQuery&limit=30")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val track = data.getJSONObject(i)
                        
                        val trackId = track.optString("id")
                        val trackName = track.optString("title")
                        val duration = track.optLong("duration", 0) * 1000L
                        val externalUrl = track.optString("link")

                        val artistObj = track.optJSONObject("artist")
                        val artistName = artistObj?.optString("name") ?: "Unknown Artist"

                        val albumObj = track.optJSONObject("album")
                        val albumName = albumObj?.optString("title") ?: "Unknown Album"
                        val coverUrl = albumObj?.optString("cover_xl") ?: ""

                        tracks.add(
                            TrackResult(
                                id = trackId,
                                name = trackName,
                                artists = artistName,
                                album = albumName,
                                duration_ms = duration,
                                imageUrl = coverUrl,
                                external_url = externalUrl,
                                platform = "deezer"
                            )
                        )
                    }
                }
            }
            connection.disconnect()
            
            // Also search albums
            val urlAlbums = URL("https://api.deezer.com/search/album?q=$encodedQuery&limit=10")
            val connAlbums = urlAlbums.openConnection() as HttpURLConnection
            if (connAlbums.responseCode == 200) {
                val responseStr = connAlbums.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(responseStr).optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val album = data.getJSONObject(i)
                        albums.add(
                            AlbumResult(
                                id = album.optString("id"),
                                name = album.optString("title"),
                                artists = album.optJSONObject("artist")?.optString("name") ?: "Unknown Artist",
                                imageUrl = album.optString("cover_xl"),
                                platform = "deezer"
                            )
                        )
                    }
                }
            }
            connAlbums.disconnect()
            
            // Also search artists
            val urlArtists = URL("https://api.deezer.com/search/artist?q=$encodedQuery&limit=10")
            val connArtists = urlArtists.openConnection() as HttpURLConnection
            if (connArtists.responseCode == 200) {
                val responseStr = connArtists.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(responseStr).optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val artist = data.getJSONObject(i)
                        artists.add(
                            ArtistResult(
                                id = artist.optString("id"),
                                name = artist.optString("name"),
                                imageUrl = artist.optString("picture_xl"),
                                platform = "deezer"
                            )
                        )
                    }
                }
            }
            connArtists.disconnect()
            
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Native Deezer API fallback failed", e)
        }

        return SearchResult(tracks, albums, artists)
    }

    fun clearSearch() {
        _searchResults.value = null
        _searchQuery.value = ""
    }

    fun getEngine(): ExtensionEngine? = activeEngine

    // Native Deezer Fallbacks for Album and Artist Detail
    suspend fun getAlbumDetails(albumId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try extension first
                val extResult = activeEngine?.fetchAlbum(albumId)
                if (!extResult.isNullOrEmpty() && extResult != "null" && extResult != "undefined" && extResult.trim() != "{}") {
                    return@withContext extResult
                }
                
                // Fallback to native Deezer API
                Log.d("DownloadViewModel", "Falling back to Deezer API for Album $albumId")
                val url = URL("https://api.deezer.com/album/$albumId")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    
                    // Convert Deezer format to Engine format
                    val engineJson = JSONObject()
                    engineJson.put("id", json.optString("id"))
                    engineJson.put("name", json.optString("title"))
                    val artistObj = json.optJSONObject("artist")
                    engineJson.put("artists", artistObj?.optString("name") ?: "")
                    engineJson.put("image_url", json.optString("cover_xl"))
                    
                    val tracksArr = JSONArray()
                    val deezerTracks = json.optJSONObject("tracks")?.optJSONArray("data")
                    if (deezerTracks != null) {
                        for (i in 0 until deezerTracks.length()) {
                            val dt = deezerTracks.getJSONObject(i)
                            val t = JSONObject()
                            t.put("id", dt.optString("id"))
                            t.put("name", dt.optString("title"))
                            t.put("artists", dt.optJSONObject("artist")?.optString("name") ?: "")
                            t.put("duration_ms", dt.optLong("duration", 0) * 1000L)
                            tracksArr.put(t)
                        }
                    }
                    engineJson.put("tracks", tracksArr as Any)
                    engineJson.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Error in getAlbumDetails", e)
                null
            }
        }
    }

    suspend fun getArtistDetails(artistId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try extension first
                val extResult = activeEngine?.fetchArtist(artistId)
                if (!extResult.isNullOrEmpty() && extResult != "null" && extResult != "undefined" && extResult.trim() != "{}") {
                    return@withContext extResult
                }
                
                // Fallback to native Deezer API
                Log.d("DownloadViewModel", "Falling back to Deezer API for Artist $artistId")
                
                // Fetch artist info
                val url = URL("https://api.deezer.com/artist/$artistId")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val artistJson = if (connection.responseCode == 200) {
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } else JSONObject()
                
                // Fetch top tracks
                val topUrl = URL("https://api.deezer.com/artist/$artistId/top?limit=10")
                val topConn = topUrl.openConnection() as HttpURLConnection
                topConn.connectTimeout = 10000
                topConn.readTimeout = 10000
                val topTracksDeezer = if (topConn.responseCode == 200) {
                    JSONObject(topConn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data")
                } else null
                
                // Fetch albums
                val albumsUrl = URL("https://api.deezer.com/artist/$artistId/albums?limit=20")
                val albumsConn = albumsUrl.openConnection() as HttpURLConnection
                albumsConn.connectTimeout = 10000
                albumsConn.readTimeout = 10000
                val albumsDeezer = if (albumsConn.responseCode == 200) {
                    JSONObject(albumsConn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data")
                } else null
                
                // Convert to Engine Format
                val engineJson = JSONObject()
                engineJson.put("id", artistJson.optString("id", artistId))
                engineJson.put("name", artistJson.optString("name", "Unknown Artist"))
                engineJson.put("image_url", artistJson.optString("picture_xl"))
                
                val tracksArr = JSONArray()
                if (topTracksDeezer != null) {
                    for (i in 0 until topTracksDeezer.length()) {
                        val dt = topTracksDeezer.getJSONObject(i)
                        val t = JSONObject()
                        t.put("id", dt.optString("id"))
                        t.put("name", dt.optString("title"))
                        t.put("artists", dt.optJSONObject("artist")?.optString("name") ?: "")
                        t.put("album", dt.optJSONObject("album")?.optString("title") ?: "")
                        t.put("duration_ms", dt.optLong("duration", 0) * 1000L)
                        t.put("image_url", dt.optJSONObject("album")?.optString("cover_xl") ?: "")
                        tracksArr.put(t)
                    }
                }
                engineJson.put("top_tracks", tracksArr as Any)
                
                val albumsArr = JSONArray()
                if (albumsDeezer != null) {
                    for (i in 0 until albumsDeezer.length()) {
                        val da = albumsDeezer.getJSONObject(i)
                        val a = JSONObject()
                        a.put("id", da.optString("id"))
                        a.put("name", da.optString("title"))
                        a.put("artists", artistJson.optString("name", ""))
                        a.put("image_url", da.optString("cover_xl"))
                        albumsArr.put(a)
                    }
                }
                engineJson.put("albums", albumsArr as Any)
                
                engineJson.toString()
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Error in getArtistDetails", e)
                null
            }
        }
    }

    fun selectQuality(quality: String) {
        _selectedQuality.value = quality
    }

    override fun onCleared() {
        super.onCleared()
        activeEngine?.destroy()
    }
}
