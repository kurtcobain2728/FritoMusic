package com.frito.music.downloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cliente nativo Lossless (FLAC) para FritoMusic, portado de FridaMusic.
 * Resuelve transmisiones en calidad CD (16-bit/44.1kHz) y Hi-Res (24-bit/96kHz o 192kHz)
 * sin requerir motores JavaScript (Rhino) ni extensiones externas.
 */
object LosslessClient {
    private const val TAG = "LosslessClient"
    private const val PREFS_NAME = "qobuz_config"
    private const val DEFAULT_BASE_URL = "https://www.qobuz.com/api.json/0.2"

    data class ResolvedLosslessFile(
        val url: String,
        val formatId: Int,
        val bitRate: String,
        val sampleRate: Int?,
        val bitDepth: Int?
    )

    data class QobuzCandidate(
        val id: Long,
        val title: String,
        val artist: String,
        val durationSeconds: Int,
        val streamable: Boolean,
        val maxBitDepth: Int,
        val artworkUrl: String? = null
    )

    // Formatos en orden de máxima fidelidad
    private val QUALITY_FORMATS = intArrayOf(27, 7, 6) // 27 = HiRes 192kHz, 7 = HiRes 96kHz, 6 = CD FLAC

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appId = prefs.getString("app_id", "") ?: ""
        val appSecret = prefs.getString("app_secret", "") ?: ""
        val userToken = prefs.getString("user_token", "") ?: ""
        return appId.isNotBlank() && appSecret.isNotBlank() && userToken.isNotBlank()
    }

    fun setCredentials(context: Context, appId: String, appSecret: String, userToken: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("app_id", appId)
            .putString("app_secret", appSecret)
            .putString("user_token", userToken)
            .apply()
    }

    private fun getCredentials(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appId = prefs.getString("app_id", "") ?: ""
        val appSecret = prefs.getString("app_secret", "") ?: ""
        val userToken = prefs.getString("user_token", "") ?: ""
        return Triple(appId, appSecret, userToken)
    }

    /**
     * Resuelve una pista a enlace FLAC nativo mediante búsqueda y cálculo de confianza.
     */
    suspend fun resolve(
        context: Context,
        title: String,
        artist: String,
        durationSeconds: Long? = null
    ): ResolvedTrack? = withContext(Dispatchers.IO) {
        if (!isConfigured(context)) {
            Log.d(TAG, "Lossless provider no configurado con credenciales")
            return@withContext null
        }

        val (appId, appSecret, userToken) = getCredentials(context)
        val searchTerms = generateSearchTerms(artist, title)

        for (term in searchTerms) {
            val candidates = searchCandidates(term, appId, userToken)
            if (candidates.isEmpty()) continue

            // Filtrar y ordenar por confianza
            val eligible = candidates
                .filter { it.streamable && it.maxBitDepth >= 16 }
                .map { it to computeConfidence(artist, title, durationSeconds, it) }
                .filter { it.second >= 0.70f }
                .sortedByDescending { it.second }

            for ((candidate, conf) in eligible) {
                Log.d(TAG, "Candidato encontrado: ${candidate.artist} - ${candidate.title} (confianza: $conf)")
                val resolvedFile = resolveBestFileUrl(candidate.id, appId, appSecret, userToken)
                if (resolvedFile != null && resolvedFile.url.isNotBlank()) {
                    val qualityLabel = listOfNotNull(
                        resolvedFile.bitDepth?.let { "${it}bit" },
                        resolvedFile.sampleRate?.let { "${it}Hz" }
                    ).joinToString("/").ifBlank { "FLAC" }

                    return@withContext ResolvedTrack(
                        streamUrl = resolvedFile.url,
                        extension = "flac",
                        bitRate = qualityLabel,
                        resolvedQuality = OnlineQuality.HIGH,
                        fallbackUsed = false,
                        artworkUrl = candidate.artworkUrl
                    )
                }
            }
        }

        null
    }

    private fun searchCandidates(
        query: String,
        appId: String,
        userToken: String
    ): List<QobuzCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("$DEFAULT_BASE_URL/track/search?query=$encoded&limit=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FritoMusic/1.0")
                setRequestProperty("X-App-Id", appId)
                setRequestProperty("X-User-Auth-Token", userToken)
            }

            if (conn.responseCode != 200) return emptyList()

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val tracksObj = root.optJSONObject("tracks") ?: return emptyList()
            val items = tracksObj.optJSONArray("items") ?: return emptyList()

            val list = mutableListOf<QobuzCandidate>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val performer = item.optJSONObject("performer")
                val artistName = performer?.optString("name", "") ?: ""
                val albumObj = item.optJSONObject("album")
                val imageObj = albumObj?.optJSONObject("image")
                val artUrl = imageObj?.optString("large")
                    ?: imageObj?.optString("thumbnail")
                    ?: albumObj?.optString("cover")

                list.add(
                    QobuzCandidate(
                        id = item.optLong("id", 0L),
                        title = item.optString("title", ""),
                        artist = artistName,
                        durationSeconds = item.optInt("duration", 0),
                        streamable = item.optBoolean("streamable", true),
                        maxBitDepth = item.optInt("maximum_bit_depth", 16),
                        artworkUrl = artUrl
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error buscando en Qobuz: ${e.message}")
            emptyList()
        }
    }

    private fun resolveBestFileUrl(
        trackId: Long,
        appId: String,
        appSecret: String,
        userToken: String
    ): ResolvedLosslessFile? {
        for (formatId in QUALITY_FORMATS) {
            val file = getFileUrl(trackId, formatId, appId, appSecret, userToken)
            if (file != null) return file
        }
        return null
    }

    private fun getFileUrl(
        trackId: Long,
        formatId: Int,
        appId: String,
        appSecret: String,
        userToken: String
    ): ResolvedLosslessFile? {
        return try {
            val timestamp = (System.currentTimeMillis() / 1000L).toString()
            val intent = "stream"
            val sigPayload = "trackgetFileUrlformat_id${formatId}intent${intent}track_id${trackId}${timestamp}${appSecret}"

            val md5 = MessageDigest.getInstance("MD5")
            val sig = md5.digest(sigPayload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

            val urlString = "$DEFAULT_BASE_URL/track/getFileUrl?format_id=$formatId&intent=$intent&track_id=$trackId&request_ts=$timestamp&request_sig=$sig"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FritoMusic/1.0")
                setRequestProperty("X-App-Id", appId)
                setRequestProperty("X-User-Auth-Token", userToken)
            }

            if (conn.responseCode != 200) return null

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)

            val isSample = root.optBoolean("sample", false)
            if (isSample) return null

            val streamUrl = root.optString("url", "")
            if (streamUrl.isBlank() || !streamUrl.startsWith("http") || streamUrl.contains("sample", ignoreCase = true)) {
                return null
            }

            val restrictions = root.opt("restrictions")
            if (restrictions != null && restrictions != JSONObject.NULL) {
                if (restrictions is JSONArray && restrictions.length() > 0) return null
                if (restrictions is JSONObject && restrictions.length() > 0) return null
            }

            val samplingRate = root.optDouble("sampling_rate", 44.1)
            val sampleRateInt = if (samplingRate >= 1000) samplingRate.toInt() else (samplingRate * 1000).toInt()
            val bitDepth = root.optInt("bit_depth", 16)
            val bitRate = root.optInt("bit_rate", 0)

            ResolvedLosslessFile(
                url = streamUrl,
                formatId = root.optInt("format_id", formatId),
                bitRate = if (bitRate > 0) "${bitRate} kbps" else "FLAC",
                sampleRate = sampleRateInt,
                bitDepth = bitDepth
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo stream URL para track $trackId formato $formatId: ${e.message}")
            null
        }
    }

    private fun generateSearchTerms(artist: String, title: String): List<String> {
        val cleanArtist = cleanSearchTerm(artist)
        val cleanTitle = cleanSearchTerm(title)
        val terms = mutableListOf<String>()

        terms.add("$artist $title".trim())
        if (cleanArtist.isNotEmpty() && cleanTitle.isNotEmpty()) {
            terms.add("$cleanArtist $cleanTitle".trim())
        }

        val primary = artist.substringBefore(",").substringBefore("&").trim()
        if (primary.isNotEmpty() && !primary.equals(artist.trim(), ignoreCase = true)) {
            terms.add("$primary $title".trim())
            val cleanPrimary = cleanSearchTerm(primary)
            if (cleanPrimary.isNotEmpty() && cleanTitle.isNotEmpty()) {
                terms.add("$cleanPrimary $cleanTitle".trim())
            }
        }
        return terms.distinct()
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("(?i)\\b(official\\s*video|official\\s*audio|video\\s*oficial|audio\\s*oficial|lyric\\s*video|letra|remastered|remaster)\\b"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*\\]"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*"), " ")
            .replace(Regex("[''`]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\p{S}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun jaccard(a: String, b: String): Float {
        val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val intersection = setA.intersect(setB).size.toFloat()
        val union = setA.union(setB).size.toFloat()
        return intersection / union
    }

    private fun artistSimilarity(a: String, b: String): Float {
        val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f

        val intersection = setA.intersect(setB)
        val union = setA.union(setB)
        val jaccardScore = intersection.size.toFloat() / union.size.toFloat()

        val smallerSize = min(setA.size, setB.size)
        val smallerFullyCovered = intersection.size == smallerSize
        val hasDistinctiveOverlap = intersection.any { token ->
            token.length > 3 || token.any { ch -> !ch.isLetterOrDigit() }
        }

        val coverageScore = if (smallerFullyCovered && hasDistinctiveOverlap) 1.0f else 0f
        return max(jaccardScore, coverageScore)
    }

    private fun computeConfidence(
        queryArtist: String,
        queryTitle: String,
        queryDuration: Long?,
        candidate: QobuzCandidate
    ): Float {
        if (!candidate.streamable) return 0f

        val titleSim = jaccard(normalize(queryTitle), normalize(candidate.title))
        val artistSim = artistSimilarity(normalize(queryArtist), normalize(candidate.artist))

        val durationFactor: Float = run {
            val querySec = queryDuration ?: return@run 1.0f
            if (querySec <= 0 || candidate.durationSeconds <= 0) return@run 1.0f
            val drift = abs(querySec - candidate.durationSeconds).toDouble() / querySec.toDouble()
            when {
                drift < 0.05 -> 1.0f
                drift < 0.10 -> 0.85f
                drift < 0.20 -> 0.6f
                else -> 0.3f
            }
        }

        return titleSim * artistSim * durationFactor
    }
}
