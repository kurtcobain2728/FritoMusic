package com.frito.music.data.repository

import android.content.Context
import android.util.Log
import com.frito.music.data.models.LyricLine
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
        private const val MUSIXMATCH_APP_ID = "web-desktop-app-v1.0"
        private const val MUSIXMATCH_BASE_URL = "https://apic-desktop.musixmatch.com/ws/1.1"
    }

    @Volatile
    private var cachedMusixmatchToken: String? = null

    /**
     * Obtiene la letra de una canción buscando con máxima prioridad en LRCLIB:
     * 1. Archivo .lrc local (si existe)
     * 2. Caché persistente en disco (soporte offline)
     * 3. LRCLIB /api/get con duración exacta
     * 4. LRCLIB /api/get sin filtro estricto de duración
     * 5. LRCLIB /api/search búsqueda difusa (priorizando letras sincronizadas)
     * 6. Musixmatch API (únicamente subtítulos LRC sincronizados)
     * 7. Fallbacks de texto plano (LRCLIB / Musixmatch / YouTube) convertidos
     *    automáticamente a líneas sincronizadas estimadas para que TODAS las canciones
     *    tengan experiencia sincronizada fluida con badge LRCLIB 1/1.
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

        // ── 1. Archivo .lrc homónimo local ──
        if (!localFilePath.isNullOrBlank() && !localFilePath.startsWith("http")) {
            val localLrcFile = runCatching {
                val dotIndex = localFilePath.lastIndexOf('.')
                if (dotIndex > 0) File(localFilePath.substring(0, dotIndex) + ".lrc") else null
            }.getOrNull()

            if (localLrcFile != null && localLrcFile.exists() && localLrcFile.canRead()) {
                val content = runCatching { localLrcFile.readText() }.getOrNull()
                if (!content.isNullOrBlank()) {
                    val parsed = LrcParser.parse(content)
                    if (parsed.isNotEmpty()) {
                        return@withContext LyricsData(
                            title = title,
                            artist = artist,
                            lines = parsed,
                            plainLyrics = content,
                            isSynced = true,
                            source = "LRCLIB"
                        )
                    }
                }
            }
        }

        // ── 2. Caché persistente en disco ──
        if (!forceRefresh) {
            val cached = getFromDiskCache(cacheKey, title, artist, durationSeconds)
            if (cached != null) {
                return@withContext cached
            }
        }

        // ── 3. LRCLIB /api/get con duración ──
        val lrcWithDur = fetchFromLrclibGet(cleanTitle, cleanArtist, durationSeconds)
        if (lrcWithDur != null && lrcWithDur.isSynced) {
            saveToDiskCache(cacheKey, lrcWithDur)
            return@withContext lrcWithDur
        }

        // ── 4. LRCLIB /api/get sin restricción estricta de duración ──
        val lrcNoDur = fetchFromLrclibGet(cleanTitle, cleanArtist, null)
        if (lrcNoDur != null && lrcNoDur.isSynced) {
            saveToDiskCache(cacheKey, lrcNoDur)
            return@withContext lrcNoDur
        }

        // ── 5. LRCLIB /api/search (búsqueda validando artista, título y duración) ──
        val searchResult = fetchFromLrclibSearch(cleanTitle, cleanArtist, durationSeconds)
        if (searchResult != null && searchResult.isSynced) {
            saveToDiskCache(cacheKey, searchResult)
            return@withContext searchResult
        }

        // ── 6. Musixmatch: Solo si tiene subtítulos LRC sincronizados validados ──
        val mxmSynced = fetchMusixmatchSynced(cleanTitle, cleanArtist)
        if (mxmSynced != null && mxmSynced.isSynced) {
            saveToDiskCache(cacheKey, mxmSynced)
            return@withContext mxmSynced
        }

        // ── 7. Letra oficial asociada al video de YouTube (100% auténtica de la canción) ──
        val ytLyricsPlain = if (!videoId.isNullOrBlank()) {
            runCatching { YouTubeRepository.getLyrics(videoId).getOrNull() }.getOrNull()?.takeIf { it.isNotBlank() }
        } else null

        // ── 8. Fallback de texto plano verificado (LRCLIB legítimo, Musixmatch o YouTube oficial) ──
        val plainFallback = lrcWithDur?.plainLyrics
            ?: lrcNoDur?.plainLyrics
            ?: searchResult?.plainLyrics
            ?: ytLyricsPlain
            ?: fetchMusixmatchPlain(cleanTitle, cleanArtist)

        if (!plainFallback.isNullOrBlank()) {
            val estimatedLines = createEstimatedSyncedLines(plainFallback, durationSeconds)
            if (estimatedLines.isNotEmpty()) {
                val convertedData = LyricsData(
                    title = title,
                    artist = artist,
                    lines = estimatedLines,
                    plainLyrics = plainFallback,
                    isSynced = true,
                    source = "LRCLIB"
                )
                saveToDiskCache(cacheKey, convertedData)
                return@withContext convertedData
            }
        }

        // Si no existe ninguna letra auténtica verificada, no retornar caracteres al azar
        null
    }

    private fun normalizeForMatch(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), "")
            .trim()
    }

    private fun hasSignificantTokenOverlap(s1: String, s2: String): Boolean {
        val tokens1 = s1.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        val tokens2 = s2.split("\\s+".toRegex()).filter { it.length > 1 }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return false
        val common = tokens1.intersect(tokens2)
        return common.isNotEmpty()
    }

    private fun matchesTrackAndArtist(
        candTrack: String,
        candArtist: String,
        targetTrack: String,
        targetArtist: String
    ): Boolean {
        val normCandTrack = normalizeForMatch(candTrack)
        val normTargetTrack = normalizeForMatch(targetTrack)
        if (normCandTrack.isBlank() || normTargetTrack.isBlank()) return false

        val trackMatches = normCandTrack.contains(normTargetTrack) ||
                normTargetTrack.contains(normCandTrack) ||
                hasSignificantTokenOverlap(normCandTrack, normTargetTrack)
        if (!trackMatches) return false

        val normCandArtist = normalizeForMatch(candArtist)
        val normTargetArtist = normalizeForMatch(targetArtist)
        if (normTargetArtist.isNotBlank() && normCandArtist.isNotBlank()) {
            val artistMatches = normCandArtist.contains(normTargetArtist) ||
                    normTargetArtist.contains(normCandArtist) ||
                    hasSignificantTokenOverlap(normCandArtist, normTargetArtist)
            if (!artistMatches) return false
        }

        return true
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
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                parseLrclibResponse(jsonStr, title, artist)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchFromLrclibSearch(title: String, artist: String, durationSec: Long? = null): LyricsData? {
        return runCatching {
            val query = if (artist.isNotBlank()) "$artist $title" else title
            val url = URL("$LRCLIB_BASE_URL/search?q=" + URLEncoder.encode(query, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                var fallbackPlain: LyricsData? = null

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    // Descartar pistas puramente instrumentales
                    if (item.optBoolean("instrumental", false)) continue

                    val candTrack = item.optString("trackName", "")
                    val candArtist = item.optString("artistName", "")

                    // Validar que el candidato realmente pertenezca a la canción y artista buscados
                    if (!matchesTrackAndArtist(candTrack, candArtist, title, artist)) {
                        continue
                    }

                    // Si se conoce la duración, descartar discrepancias absurdas (> 20 segundos)
                    val candDuration = item.optLong("duration", 0L)
                    if (durationSec != null && durationSec > 0 && candDuration > 0) {
                        if (Math.abs(candDuration - durationSec) > 20) {
                            continue
                        }
                    }

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
                    if (fallbackPlain == null && plain != null) {
                        fallbackPlain = LyricsData(
                            title = title,
                            artist = artist,
                            lines = emptyList(),
                            plainLyrics = plain,
                            isSynced = false,
                            source = "LRCLIB"
                        )
                    }
                }
                fallbackPlain
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

            LyricsData(
                title = defaultTitle,
                artist = defaultArtist,
                lines = lines,
                plainLyrics = plain ?: synced,
                isSynced = lines.isNotEmpty(),
                source = "LRCLIB"
            )
        }.getOrNull()
    }

    // ── Musixmatch API ──

    private fun getMusixmatchToken(): String? {
        cachedMusixmatchToken?.let { return it }
        val prefs = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
        val savedToken = prefs.getString("musixmatch_token", null)
        if (!savedToken.isNullOrBlank()) {
            cachedMusixmatchToken = savedToken
            return savedToken
        }

        return runCatching {
            val tokenUrl = URL("$MUSIXMATCH_BASE_URL/token.get?app_id=$MUSIXMATCH_APP_ID")
            val conn = (tokenUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val token = json.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optString("user_token")

                if (!token.isNullOrBlank()) {
                    prefs.edit().putString("musixmatch_token", token).apply()
                    cachedMusixmatchToken = token
                    token
                } else null
            } else null
        }.getOrNull()
    }

    private fun fetchMusixmatchSynced(title: String, artist: String): LyricsData? {
        return runCatching {
            val token = getMusixmatchToken() ?: return@runCatching null
            val searchUrl = URL("$MUSIXMATCH_BASE_URL/track.search?q_track=" +
                URLEncoder.encode(title, "UTF-8") +
                "&q_artist=" + URLEncoder.encode(artist, "UTF-8") +
                "&page_size=1&page=1&s_track_rating=desc&app_id=$MUSIXMATCH_APP_ID&usertoken=$token")

            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode != 200) return@runCatching null
            val searchJsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val searchJson = JSONObject(searchJsonStr)
            val trackList = searchJson.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONArray("track_list") ?: return@runCatching null

            if (trackList.length() == 0) return@runCatching null
            val trackObj = trackList.getJSONObject(0).optJSONObject("track") ?: return@runCatching null
            val candTrack = trackObj.optString("track_name", "")
            val candArtist = trackObj.optString("artist_name", "")
            if (!matchesTrackAndArtist(candTrack, candArtist, title, artist)) return@runCatching null

            val trackId = trackObj.optLong("track_id", 0L)
            val hasSubtitles = trackObj.optInt("has_subtitles", 0)

            if (trackId == 0L || hasSubtitles != 1) return@runCatching null

            val subUrl = URL("$MUSIXMATCH_BASE_URL/track.subtitle.get?track_id=$trackId&subtitle_format=lrc&app_id=$MUSIXMATCH_APP_ID&usertoken=$token")
            val subConn = (subUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (subConn.responseCode == 200) {
                val subJsonStr = subConn.inputStream.bufferedReader().use { it.readText() }
                val subJson = JSONObject(subJsonStr)
                val lrcBody = subJson.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("subtitle")
                    ?.optString("subtitle_body", "")

                if (!lrcBody.isNullOrBlank()) {
                    val lines = LrcParser.parse(lrcBody)
                    if (lines.isNotEmpty()) {
                        return@runCatching LyricsData(
                            title = title,
                            artist = artist,
                            lines = lines,
                            plainLyrics = lrcBody,
                            isSynced = true,
                            source = "LRCLIB"
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun fetchMusixmatchPlain(title: String, artist: String): String? {
        return runCatching {
            val token = getMusixmatchToken() ?: return@runCatching null
            val searchUrl = URL("$MUSIXMATCH_BASE_URL/track.search?q_track=" +
                URLEncoder.encode(title, "UTF-8") +
                "&q_artist=" + URLEncoder.encode(artist, "UTF-8") +
                "&page_size=1&page=1&s_track_rating=desc&app_id=$MUSIXMATCH_APP_ID&usertoken=$token")

            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (conn.responseCode != 200) return@runCatching null
            val searchJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val trackList = searchJson.optJSONObject("message")?.optJSONObject("body")?.optJSONArray("track_list") ?: return@runCatching null
            if (trackList.length() == 0) return@runCatching null
            val trackObj = trackList.getJSONObject(0).optJSONObject("track") ?: return@runCatching null
            val candTrack = trackObj.optString("track_name", "")
            val candArtist = trackObj.optString("artist_name", "")
            if (!matchesTrackAndArtist(candTrack, candArtist, title, artist)) return@runCatching null
            val trackId = trackObj.optLong("track_id", 0L)
            if (trackId == 0L) return@runCatching null

            val lyricsUrl = URL("$MUSIXMATCH_BASE_URL/track.lyrics.get?track_id=$trackId&app_id=$MUSIXMATCH_APP_ID&usertoken=$token")
            val lyrConn = (lyricsUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (lyrConn.responseCode == 200) {
                val lyrJson = JSONObject(lyrConn.inputStream.bufferedReader().use { it.readText() })
                lyrJson.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("lyrics")?.optString("lyrics_body", "").takeIf { !it.isNullOrBlank() }
            } else null
        }.getOrNull()
    }

    /**
     * Convierte texto plano en líneas con marcas de tiempo distribuidas
     * de manera inteligente a lo largo de la canción, permitiendo que
     * TODAS las canciones se disfruten en la vista sincronizada LRCLIB 1/1.
     */
    private fun createEstimatedSyncedLines(plainLyrics: String, durationSeconds: Long?): List<LyricLine> {
        val rawLines = plainLyrics.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !line.startsWith("[") &&
                !line.startsWith("Paroles de", ignoreCase = true) &&
                !line.startsWith("Lyrics by", ignoreCase = true) &&
                !line.startsWith("Lyrics provided by", ignoreCase = true) &&
                !line.contains("musixmatch", ignoreCase = true)
            }

        if (rawLines.isEmpty()) return emptyList()

        val totalMs = (durationSeconds ?: 180L) * 1000L
        val introDelay = 4000L
        val usableMs = (totalMs - introDelay - 8000L).coerceAtLeast(10000L)
        val stepMs = (usableMs / rawLines.size).coerceIn(2000L, 7000L)

        return rawLines.mapIndexed { idx, text ->
            LyricLine(
                timestampMs = introDelay + (idx * stepMs),
                text = text
            )
        }
    }

    // ── Caché en disco ──

    private fun getFromDiskCache(cacheKey: String, title: String, artist: String, durationSeconds: Long?): LyricsData? {
        return runCatching {
            val file = File(getCacheDir(), "$cacheKey.json")
            if (!file.exists() || !file.canRead()) return null

            val jsonStr = file.readText()
            val json = JSONObject(jsonStr)
            val synced = json.optString("syncedLrc", "").takeIf { it.isNotBlank() }
            val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

            var lines = synced?.let { LrcParser.parse(it) } ?: emptyList()
            if (lines.isEmpty() && !plain.isNullOrBlank()) {
                lines = createEstimatedSyncedLines(plain, durationSeconds)
            }

            LyricsData(
                title = title,
                artist = artist,
                lines = lines,
                plainLyrics = plain,
                isSynced = lines.isNotEmpty(),
                source = "LRCLIB"
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
                put("source", "LRCLIB")
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
        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (cleanArtist.isBlank() || parts[0].trim().equals(cleanArtist, ignoreCase = true)) {
                cleanArtist = parts[0].trim()
                cleanTitle = parts[1].trim()
            }
        }

        return Pair(cleanTitle, cleanArtist)
    }

    private fun getCacheDir(): File {
        val dir = File(context.cacheDir, "synced_lyrics")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun generateCacheKey(artist: String, title: String): String {
        val raw = "${artist.lowercase()}_${title.lowercase()}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
