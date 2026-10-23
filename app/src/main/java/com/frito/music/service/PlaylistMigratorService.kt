package com.frito.music.service

import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MigratedTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val originalUri: String? = null,
    var resolvedVideoId: String? = null
) {
    val searchTitle: String get() = "$artist - $title"
}

data class MigratedPlaylist(
    val title: String,
    val description: String? = null,
    val author: String,
    val coverUrl: String? = null,
    val platform: String,
    val tracks: MutableList<MigratedTrack>,
    val originalUrl: String
)

object PlaylistMigratorService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun detectPlatform(url: String): String {
        val lower = url.lowercase().trim()
        return when {
            lower.contains("spotify.com") -> "Spotify"
            lower.contains("deezer.com") || lower.contains("deezer.page.link") -> "Deezer"
            lower.contains("music.apple.com") || lower.contains("itunes.apple.com") -> "Apple Music"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube Music"
            lower.contains("soundcloud.com") -> "SoundCloud"
            lower.contains("tidal.com") -> "Tidal"
            else -> "Desconocida"
        }
    }

    private fun httpGet(urlStr: String, followRedirects: Boolean = true): Pair<Int, String> {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            conn.connect()

            val status = conn.responseCode
            if (followRedirects && (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == 307 || status == 308)) {
                val loc = conn.getHeaderField("Location")
                if (!loc.isNullOrBlank()) {
                    currentUrl = loc
                    redirects++
                    continue
                }
            }

            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            if (stream == null) return Pair(status, "")
            val reader = BufferedReader(InputStreamReader(stream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            return Pair(status, sb.toString())
        }
        throw Exception("Demasiadas redirecciones")
    }

    suspend fun parsePlaylistUrl(url: String): MigratedPlaylist = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val platform = detectPlatform(cleanUrl)

        when (platform) {
            "Spotify" -> parseSpotify(cleanUrl)
            "Deezer" -> parseDeezer(cleanUrl)
            "YouTube Music" -> parseYouTube(cleanUrl)
            "Apple Music" -> parseAppleMusic(cleanUrl)
            "SoundCloud" -> parseSoundCloud(cleanUrl)
            "Tidal" -> parseTidal(cleanUrl)
            else -> throw Exception("Plataforma no reconocida. Pega un enlace de Spotify, Deezer, Apple Music, YouTube Music, SoundCloud o Tidal.")
        }
    }

    private fun parseSpotify(url: String): MigratedPlaylist {
        val parsedUrl = URL(url)
        val path = parsedUrl.path
        val playlistIdx = path.indexOf("/playlist/")
        if (playlistIdx == -1) {
            throw Exception("La URL no parece ser una lista de reproducción válida de Spotify.")
        }
        val sub = path.substring(playlistIdx + "/playlist/".length)
        val playlistId = sub.substringBefore('/').substringBefore('?')
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

        val (status, body) = httpGet(embedUrl)
        if (status != 200) {
            throw Exception("No se pudo acceder a la lista de Spotify (código $status).")
        }

        val regex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(body)
        if (match != null) {
            try {
                val jsonStr = match.groupValues[1]
                val root = JSONObject(jsonStr)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    val title = entity.optString("name", "Lista de Spotify")
                    val author = entity.optString("subtitle", "Spotify")
                    val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                    val coverUrl = coverSources?.optJSONObject(0)?.optString("url")
                    val rawTracks = entity.optJSONArray("trackList") ?: JSONArray()

                    val tracks = mutableListOf<MigratedTrack>()
                    for (i in 0 until rawTracks.length()) {
                        val t = rawTracks.optJSONObject(i) ?: continue
                        val tTitle = t.optString("title")
                        val tArtist = t.optString("subtitle", "Desconocido")
                        val duration = t.optLong("duration", 0L).takeIf { it > 0 }
                        val originalUri = t.optString("uri")
                        if (tTitle.isNotBlank()) {
                            tracks.add(
                                MigratedTrack(
                                    title = tTitle,
                                    artist = tArtist,
                                    durationMs = duration,
                                    originalUri = originalUri
                                )
                            )
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        return MigratedPlaylist(
                            title = title,
                            author = author,
                            coverUrl = coverUrl,
                            platform = "Spotify",
                            tracks = tracks,
                            originalUrl = url
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        throw Exception("No se encontraron canciones en esta lista pública de Spotify. Asegúrate de que no sea privada.")
    }

    private fun parseDeezer(url: String): MigratedPlaylist {
        var targetUrl = url
        if (url.contains("deezer.page.link")) {
            val (status, _) = httpGet(url, followRedirects = true)
        }

        val parsed = URL(targetUrl)
        val path = parsed.path
        val playlistIdx = path.indexOf("/playlist/")
        if (playlistIdx == -1) {
            throw Exception("La URL no contiene un ID de lista de Deezer válido.")
        }
        val sub = path.substring(playlistIdx + "/playlist/".length)
        val playlistId = sub.substringBefore('/').substringBefore('?')

        val (status, body) = httpGet("https://api.deezer.com/playlist/$playlistId")
        if (status != 200) {
            throw Exception("Error al consultar lista en Deezer (código $status).")
        }

        val root = JSONObject(body)
        if (root.has("error")) {
            val msg = root.optJSONObject("error")?.optString("message") ?: "Lista no encontrada"
            throw Exception("Deezer indicó: $msg")
        }

        val title = root.optString("title", "Lista de Deezer")
        val author = root.optJSONObject("creator")?.optString("name") ?: "Deezer"
        val coverUrl = root.optString("picture_xl").ifBlank { root.optString("picture_medium") }
        val rawTracks = root.optJSONObject("tracks")?.optJSONArray("data") ?: JSONArray()

        val tracks = mutableListOf<MigratedTrack>()
        for (i in 0 until rawTracks.length()) {
            val t = rawTracks.optJSONObject(i) ?: continue
            val tTitle = t.optString("title")
            val tArtist = t.optJSONObject("artist")?.optString("name") ?: "Desconocido"
            val tAlbum = t.optJSONObject("album")?.optString("title")
            val durationSec = t.optLong("duration", 0L)
            if (tTitle.isNotBlank()) {
                tracks.add(
                    MigratedTrack(
                        title = tTitle,
                        artist = tArtist,
                        album = tAlbum,
                        durationMs = if (durationSec > 0) durationSec * 1000L else null,
                        originalUri = t.optString("link")
                    )
                )
            }
        }

        if (tracks.isEmpty()) {
            throw Exception("La lista de Deezer está vacía o es privada.")
        }

        return MigratedPlaylist(
            title = title,
            description = root.optString("description").takeIf { it.isNotBlank() },
            author = author,
            coverUrl = coverUrl,
            platform = "Deezer",
            tracks = tracks,
            originalUrl = url
        )
    }

    private suspend fun parseYouTube(url: String): MigratedPlaylist {
        val parsed = URL(url)
        val query = parsed.query ?: ""
        val pairs = query.split('&').associate {
            val parts = it.split('=')
            parts[0] to (parts.getOrNull(1) ?: "")
        }
        val playlistId = pairs["list"] ?: throw Exception("No se encontró el parámetro de lista (?list=...) en el enlace de YouTube.")

        val page = YouTube.playlist(playlistId).getOrThrow()
        val tracks = page.songs.map { s ->
            MigratedTrack(
                title = s.title,
                artist = s.artists.joinToString(", ") { it.name }.ifBlank { "YouTube" },
                album = s.album?.name,
                coverUrl = s.thumbnail,
                resolvedVideoId = s.id,
                originalUri = "https://youtube.com/watch?v=${s.id}"
            )
        }.toMutableList()

        return MigratedPlaylist(
            title = page.playlist.title,
            author = page.playlist.author?.name ?: "YouTube",
            coverUrl = page.playlist.thumbnail,
            platform = "YouTube Music",
            tracks = tracks,
            originalUrl = url
        )
    }

    private fun parseAppleMusic(url: String): MigratedPlaylist {
        val (status, body) = httpGet(url)
        if (status != 200) {
            throw Exception("No se pudo acceder a la página de Apple Music (código $status).")
        }

        val ogTitle = Regex("""<meta property="og:title" content="(.*?)"""").find(body)?.groupValues?.get(1) ?: "Lista de Apple Music"
        val ogImage = Regex("""<meta property="og:image" content="(.*?)"""").find(body)?.groupValues?.get(1)

        val tracks = mutableListOf<MigratedTrack>()
        val jsonLdMatches = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).findAll(body)
        for (m in jsonLdMatches) {
            try {
                val json = JSONObject(m.groupValues[1])
                val rawTracks = json.optJSONArray("track") ?: continue
                for (i in 0 until rawTracks.length()) {
                    val t = rawTracks.optJSONObject(i) ?: continue
                    val name = t.optString("name")
                    val artist = t.optJSONObject("byArtist")?.optString("name") ?: "Apple Music"
                    if (name.isNotBlank()) {
                        tracks.add(MigratedTrack(title = name, artist = artist))
                    }
                }
            } catch (_: Exception) {}
        }

        if (tracks.isEmpty()) {
            throw Exception("No se encontraron canciones públicas en esta lista de Apple Music.")
        }

        return MigratedPlaylist(
            title = ogTitle,
            author = "Apple Music",
            coverUrl = ogImage,
            platform = "Apple Music",
            tracks = tracks,
            originalUrl = url
        )
    }

    private fun parseSoundCloud(url: String): MigratedPlaylist {
        val oEmbedUrl = "https://soundcloud.com/oembed?url=" + URLEncoder.encode(url, "UTF-8") + "&format=json"
        val (status, oBody) = httpGet(oEmbedUrl)
        if (status != 200) {
            throw Exception("No se pudo acceder a la lista de SoundCloud (código $status).")
        }
        val oData = JSONObject(oBody)
        val title = oData.optString("title", "Lista de SoundCloud")
        val author = oData.optString("author_name", "SoundCloud")
        val coverUrl = oData.optString("thumbnail_url").takeIf { it.isNotBlank() }

        val tracks = mutableListOf<MigratedTrack>()
        val (pageStatus, pageBody) = httpGet(url)
        if (pageStatus == 200) {
            val trackRegex = Regex("""<article itemprop="track" itemscope[^>]*>.*?<h2 itemprop="name"><a[^>]*>(.*?)</a>.*?itemprop="byArtist"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            for (m in trackRegex.findAll(pageBody)) {
                val tTitle = m.groupValues[1].trim()
                val tArtist = m.groupValues[2].trim().ifBlank { author }
                if (tTitle.isNotBlank()) {
                    tracks.add(MigratedTrack(title = tTitle, artist = tArtist))
                }
            }
        }
        if (tracks.isEmpty()) {
            tracks.add(MigratedTrack(title = title, artist = author))
        }

        return MigratedPlaylist(
            title = title,
            author = author,
            coverUrl = coverUrl,
            platform = "SoundCloud",
            tracks = tracks,
            originalUrl = url
        )
    }

    private fun parseTidal(url: String): MigratedPlaylist {
        val (status, body) = httpGet(url)
        if (status != 200) {
            throw Exception("No se pudo acceder a la lista de Tidal (código $status).")
        }
        val title = Regex("""<meta property="og:title" content="(.*?)"""").find(body)?.groupValues?.get(1) ?: "Lista de Tidal"
        val coverUrl = Regex("""<meta property="og:image" content="(.*?)"""").find(body)?.groupValues?.get(1)

        val tracks = mutableListOf<MigratedTrack>()
        val jsonMatch = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(body)
        if (jsonMatch != null) {
            try {
                val json = JSONObject(jsonMatch.groupValues[1])
                val rawTracks = json.optJSONArray("track") ?: JSONArray()
                for (i in 0 until rawTracks.length()) {
                    val t = rawTracks.optJSONObject(i) ?: continue
                    val tName = t.optString("name")
                    val tArtist = t.optJSONObject("byArtist")?.optString("name") ?: "Tidal"
                    if (tName.isNotBlank()) {
                        tracks.add(MigratedTrack(title = tName, artist = tArtist))
                    }
                }
            } catch (_: Exception) {}
        }
        if (tracks.isEmpty()) {
            throw Exception("No se pudieron extraer canciones de esta lista de Tidal.")
        }
        return MigratedPlaylist(
            title = title,
            author = "Tidal",
            coverUrl = coverUrl,
            platform = "Tidal",
            tracks = tracks,
            originalUrl = url
        )
    }

    suspend fun resolveTracksWithYouTube(
        playlist: MigratedPlaylist,
        onProgress: (current: Int, total: Int, currentSong: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val total = playlist.tracks.size
        var resolvedCount = 0

        for (i in 0 until total) {
            val track = playlist.tracks[i]
            onProgress(i + 1, total, "${track.artist} - ${track.title}")

            if (!track.resolvedVideoId.isNullOrBlank()) {
                resolvedCount++
                continue
            }

            try {
                val query = "${track.artist} ${track.title}".trim()
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                val songs = result.getOrNull()?.items?.filterIsInstance<com.music.innertube.models.SongItem>()
                if (!songs.isNullOrEmpty()) {
                    track.resolvedVideoId = songs.first().id
                    resolvedCount++
                }
            } catch (_: Exception) {}

            if (i % 5 == 0 && i > 0) {
                delay(150L)
            }
        }
        resolvedCount
    }

    suspend fun syncToYouTubeAccount(playlist: MigratedPlaylist): String = withContext(Dispatchers.IO) {
        val videoIds = playlist.tracks
            .mapNotNull { it.resolvedVideoId }
            .filter { it.isNotBlank() }

        if (videoIds.isEmpty()) {
            throw Exception("No hay canciones resueltas en YouTube para crear la lista.")
        }

        var batchSucceeded = true
        val playlistId = try {
            YouTube.createPlaylist(playlist.title, videoIds)
        } catch (e: Exception) {
            batchSucceeded = false
            android.util.Log.w("PlaylistMigrator", "Fallo al crear playlist con canciones en lote, reintentando...", e)
            null
        } ?: YouTube.createPlaylist(playlist.title)
          ?: throw Exception("No se pudo crear la lista en tu cuenta de YouTube Music.")

        val cleanId = playlistId.removePrefix("VL")

        // Si la creación en lote falló y se creó vacía, agregamos los videos uno a uno
        if (!batchSucceeded) {
            for (id in videoIds) {
                try {
                    YouTube.addToPlaylist(cleanId, id)
                    delay(80L)
                } catch (e: Exception) {
                    android.util.Log.w("PlaylistMigrator", "Error agregando video $id a $cleanId", e)
                }
            }
        }

        // Vincular a la biblioteca de YouTube Music con Like/Save para que aparezca en FEmusic_liked_playlists
        try {
            YouTube.likePlaylist(cleanId, true)
            android.util.Log.i("PlaylistMigrator", "Playlist $cleanId vinculada a la biblioteca de YouTube")
        } catch (e: Exception) {
            android.util.Log.w("PlaylistMigrator", "Aviso al dar like a la playlist $cleanId", e)
        }

        // Guardar copia persistente inmediata en MigratedPlaylistsManager
        val cover = playlist.coverUrl ?: playlist.tracks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
            ?: "https://i.ytimg.com/vi/${videoIds.firstOrNull()}/hqdefault.jpg"

        val item = com.music.innertube.models.PlaylistItem(
            id = cleanId,
            title = playlist.title,
            author = com.music.innertube.models.Artist(name = "Tú", id = null),
            songCountText = "${videoIds.size} canciones",
            thumbnail = cover,
            playEndpoint = com.music.innertube.models.WatchEndpoint(playlistId = cleanId),
            shuffleEndpoint = com.music.innertube.models.WatchEndpoint(playlistId = cleanId),
            radioEndpoint = null,
            isEditable = true
        )
        com.frito.music.data.repository.MigratedPlaylistsManager.save(item)

        cleanId
    }
}
