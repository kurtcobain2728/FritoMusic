package com.frito.music.data.repository

import android.content.Context
import android.util.Log
import com.frito.music.data.models.LyricsData
import com.frito.music.data.network.yt.YouTubeRepository
import com.frito.music.utils.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class LyricsRepository(private val context: Context) {

    companion object {
        private const val TAG = "LyricsRepository"
        private const val LRCLIB_BASE_URL = "https://lrclib.net/api"
        private const val USER_AGENT = "FritoMusic/1.0 (Android; https://github.com/kurtcobain2728/FritoMusic)"
    }

    /**
     * Obtiene la letra de una canción buscando en cascada:
     * 1. Archivo .lrc local junto al archivo de audio (si es local)
     * 2. Caché persistente en disco (para modo offline), salvo si forceRefresh es true
     * 3. LRCLIB API (/api/get búsqueda exacta con título, artista y duración)
     * 4. LRCLIB API (/api/search búsqueda difusa)
     * 5. YouTube Music (como fallback de texto plano si hay videoId disponible)
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Long? = null,
        videoId: String? = null,
        localFilePath: String? = null,
        forceRefresh: Boolean = false
    ): LyricsData? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null

        val (cleanTitle, cleanArtist) = cleanMetadata(title, artist)
        val cacheKey = generateCacheKey(cleanArtist, cleanTitle)

        // ── 1. Buscar archivo .lrc homónimo local si la pista proviene de almacenamiento local ──
        if (!localFilePath.isNullOrBlank() && !localFilePath.startsWith("http")) {
            val localLrcFile = runCatching {
                val dotIndex = localFilePath.lastIndexOf('.')
                if (dotIndex > 0) File(localFilePath.substring(0, dotIndex) + ".lrc") else null
            }.getOrNull()

            if (localLrcFile != null && localLrcFile.exists() && localLrcFile.canRead()) {
                val content = runCatching { localLrcFile.readText() }.getOrNull()
                if (!content.isNullOrBlank()) {
                    val parsed = LrcParser.parse(content)
                    return@withContext LyricsData(
                        title = title,
                        artist = artist,
                        lines = parsed,
                        plainLyrics = content,
                        isSynced = parsed.isNotEmpty(),
                        source = "Archivo local .lrc"
                    )
                }
            }
        }

        // ── 2. Buscar en la caché persistente en disco (soporte 100% Offline) ──
        if (!forceRefresh) {
            val cached = getFromDiskCache(cacheKey, title, artist)
            if (cached != null) {
                return@withContext cached
            }
        }

        // ── 3. Consultar LRCLIB API (Endpoint directo /api/get) ──
        val lrcResult = fetchFromLrclibGet(cleanTitle, cleanArtist, durationSeconds)
        if (lrcResult != null) {
            saveToDiskCache(cacheKey, lrcResult)
            return@withContext lrcResult
        }

        // ── 4. Fallback LRCLIB API (Búsqueda difusa /api/search) ──
        val searchResult = fetchFromLrclibSearch(cleanTitle, cleanArtist)
        if (searchResult != null) {
            saveToDiskCache(cacheKey, searchResult)
            return@withContext searchResult
        }

        // ── 5. Fallback YouTube Music (Texto plano de YouTube) ──
        if (!videoId.isNullOrBlank()) {
            val ytLyrics = runCatching {
                YouTubeRepository.getLyrics(videoId).getOrNull()
            }.getOrNull()

            if (!ytLyrics.isNullOrBlank()) {
                val ytData = LyricsData(
                    title = title,
                    artist = artist,
                    lines = emptyList(),
                    plainLyrics = ytLyrics,
                    isSynced = false,
                    source = "YouTube Music"
                )
                saveToDiskCache(cacheKey, ytData)
                return@withContext ytData
            }
        }

        null
    }

    private fun fetchFromLrclibGet(title: String, artist: String, durationSec: Long?): LyricsData? {
        return runCatching {
            val queryParams = StringBuilder()
            queryParams.append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            if (artist.isNotBlank()) {
                queryParams.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            }
            if (durationSec != null && durationSec > 0) {
                queryParams.append("&duration=").append(durationSec)
            }

            val url = URL("$LRCLIB_BASE_URL/get?$queryParams")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                parseLrclibResponse(jsonStr, title, artist)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchFromLrclibSearch(title: String, artist: String): LyricsData? {
        return runCatching {
            val query = if (artist.isNotBlank()) "$artist $title" else title
            val url = URL("$LRCLIB_BASE_URL/search?q=" + URLEncoder.encode(query, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                // Priorizar el primer resultado que tenga letras sincronizadas
                var candidate: LyricsData? = null
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                    val plain = item.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                    if (synced != null) {
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            return@runCatching LyricsData(
                                title = title,
                                artist = artist,
                                lines = lines,
                                plainLyrics = plain ?: synced,
                                isSynced = true,
                                source = "LRCLIB"
                            )
                        }
                    }
                    if (candidate == null && plain != null) {
                        candidate = LyricsData(
                            title = title,
                            artist = artist,
                            lines = emptyList(),
                            plainLyrics = plain,
                            isSynced = false,
                            source = "LRCLIB"
                        )
                    }
                }
                candidate
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseLrclibResponse(jsonStr: String, defaultTitle: String, defaultArtist: String): LyricsData? {
        return runCatching {
            val json = JSONObject(jsonStr)
            val synced = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
            val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

            if (synced.isNullOrBlank() && plain.isNullOrBlank()) {
                return null
            }

            val lines = synced?.let { LrcParser.parse(it) } ?: emptyList()
            val isSynced = lines.isNotEmpty()

            LyricsData(
                title = defaultTitle,
                artist = defaultArtist,
                lines = lines,
                plainLyrics = plain ?: synced,
                isSynced = isSynced,
                source = "LRCLIB"
            )
        }.getOrNull()
    }

    // ── Gestión de Caché Persistente en Disco ──

    private fun getCacheDir(): File {
        val dir = File(context.filesDir, "lyrics_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun generateCacheKey(artist: String, title: String): String {
        val raw = "${artist.trim().lowercase()}_${title.trim().lowercase()}"
        return runCatching {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(raw.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault(raw.replace("[^a-zA-Z0-9]".toRegex(), "_"))
    }

    private fun getFromDiskCache(cacheKey: String, title: String, artist: String): LyricsData? {
        return runCatching {
            val file = File(getCacheDir(), "$cacheKey.json")
            if (!file.exists() || !file.canRead()) return null

            val jsonStr = file.readText()
            val json = JSONObject(jsonStr)
            val synced = json.optString("syncedLrc", "").takeIf { it.isNotBlank() }
            val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
            val source = json.optString("source", "Caché")

            val lines = synced?.let { LrcParser.parse(it) } ?: emptyList()
            LyricsData(
                title = title,
                artist = artist,
                lines = lines,
                plainLyrics = plain,
                isSynced = lines.isNotEmpty(),
                source = source
            )
        }.getOrNull()
    }

    private fun saveToDiskCache(cacheKey: String, data: LyricsData) {
        runCatching {
            val file = File(getCacheDir(), "$cacheKey.json")
            val json = JSONObject().apply {
                put("title", data.title)
                put("artist", data.artist)
                put("plainLyrics", data.plainLyrics ?: "")
                put("source", data.source)
                // Reconstruir texto LRC si existen líneas
                if (data.lines.isNotEmpty()) {
                    val lrcBuilder = StringBuilder()
                    data.lines.forEach { line ->
                        val min = line.timestampMs / 60000
                        val sec = (line.timestampMs % 60000) / 1000
                        val hundredths = (line.timestampMs % 1000) / 10
                        lrcBuilder.append(String.format("[%02d:%02d.%02d]%s\n", min, sec, hundredths, line.text))
                    }
                    put("syncedLrc", lrcBuilder.toString())
                }
            }
            file.writeText(json.toString())
        }
    }

    /**
     * Limpia sufijos ruidosos comunes de YouTube (ej. "(Official Video)", "[Remastered]")
     * para aumentar drásticamente la tasa de coincidencia en bases de datos de letras.
     */
    private fun cleanMetadata(title: String, artist: String): Pair<String, String> {
        var cleanTitle = title
            .replace("""(?i)\s*[\(\[](?:official\s*(?:video|audio|music\s*video|lyric\s*video)|video\s*oficial|audio\s*oficial|video|audio|remaster(?:ed)?(?:\s*\d{4})?|deluxe(?:\s*edition)?|hd|4k)[\)\]]""".toRegex(), "")
            .replace("""(?i)\s*[\(\[](?:ft\.|feat\.).*?[\)\]]""".toRegex(), "")
            .trim()

        var cleanArtist = artist
            .replace("""(?i)\s*-\s*topic$""".toRegex(), "")
            .replace("""(?i)\s*vevo$""".toRegex(), "")
            .trim()

        // Si el título contiene "Artista - Canción", separar limpiamente
        if (cleanTitle.contains(" - ") && cleanArtist.isBlank()) {
            val parts = cleanTitle.split(" - ", limit = 2)
            cleanArtist = parts[0].trim()
            cleanTitle = parts[1].trim()
        }

        return Pair(cleanTitle, cleanArtist)
    }
}
