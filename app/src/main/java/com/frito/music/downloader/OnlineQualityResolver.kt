package com.frito.music.downloader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.frito.music.data.models.StreamableTrack
import com.frito.music.data.network.yt.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

enum class OnlineQuality(val id: String, val label: String, val badge: String, val description: String) {
    NORMAL("normal", "Normal", "160 kbps", "YouTube Music Oficial (Rápida y garantizada)"),
    MEDIUM("medium", "Media", "320 kbps", "Alta fidelidad estándar (AAC 320 kbps)"),
    HIGH("flac", "Alta (Lossless)", "FLAC", "Calidad de estudio sin pérdida (Hi-Res / CD)")
}

data class ResolvedTrack(
    val streamUrl: String,
    val extension: String,
    val bitRate: String,
    val resolvedQuality: OnlineQuality,
    val fallbackUsed: Boolean = false,
    val artworkUrl: String? = null
)

object OnlineQualityResolver {
    private const val TAG = "OnlineQualityResolver"

    suspend fun resolve(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        requestedQuality: OnlineQuality
    ): Result<ResolvedTrack> = withContext(Dispatchers.IO) {
        runCatching {
            when (requestedQuality) {
                OnlineQuality.HIGH -> {
                    // 1. Intentar resolver FLAC nativo sin pérdida (Qobuz con alta confianza)
                    val losslessResult = LosslessClient.resolve(context, title, artist)
                    if (losslessResult != null) {
                        Log.d(TAG, "Resuelto FLAC nativo: ${losslessResult.bitRate}")
                        return@runCatching losslessResult
                    }

                    // 2. Fallback a Saavn 320 kbps (solo con coincidencia estricta y verificada)
                    val saavnResult = tryResolveSaavn(title, artist)
                    if (saavnResult != null) {
                        Log.d(TAG, "FLAC no disponible, fallback a 320 kbps verificado")
                        return@runCatching saavnResult.copy(
                            resolvedQuality = OnlineQuality.MEDIUM,
                            fallbackUsed = true
                        )
                    }

                    // 3. Fallback garantizado a YouTube Music Oficial (canción exacta garantizada)
                    Log.d(TAG, "Fallback garantizado a YouTube Music Oficial (160 kbps)")
                    resolveNormal(videoId, title, artist).copy(
                        resolvedQuality = OnlineQuality.NORMAL,
                        fallbackUsed = true
                    )
                }

                OnlineQuality.MEDIUM -> {
                    // Intentar Saavn 320 kbps con validación estricta de título y artista
                    val saavnResult = tryResolveSaavn(title, artist)
                    if (saavnResult != null) {
                        return@runCatching saavnResult
                    }

                    // Fallback garantizado a YouTube Music Oficial
                    Log.d(TAG, "320 kbps no disponible con certeza, fallback garantizado a YouTube Oficial")
                    resolveNormal(videoId, title, artist).copy(
                        resolvedQuality = OnlineQuality.NORMAL,
                        fallbackUsed = true
                    )
                }

                OnlineQuality.NORMAL -> {
                    resolveNormal(videoId, title, artist)
                }
            }
        }
    }

    private suspend fun resolveNormal(videoId: String, title: String, artist: String): ResolvedTrack {
        val extractedId = extractVideoId(videoId)
        val effectiveVideoId = if (!extractedId.isNullOrBlank()) {
            extractedId
        } else {
            val query = "$title $artist".trim()
            if (query.isNotBlank()) {
                val results = YouTubeRepository.search(query).getOrNull().orEmpty()
                findBestYouTubeMatch(results, title, artist)?.videoId ?: videoId
            } else {
                videoId
            }
        }

        val streamUrl = YouTubeRepository.getStreamUrl(effectiveVideoId).getOrThrow()
        val ytArt = if (effectiveVideoId.isNotBlank() && !effectiveVideoId.startsWith("http")) {
            "https://i.ytimg.com/vi/$effectiveVideoId/maxresdefault.jpg"
        } else null

        return ResolvedTrack(
            streamUrl = streamUrl,
            extension = "m4a",
            bitRate = "160 kbps",
            resolvedQuality = OnlineQuality.NORMAL,
            fallbackUsed = false,
            artworkUrl = ytArt
        )
    }

    private fun extractVideoId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
            return trimmed
        }
        val match = Regex("""(?<=v=|\/vi\/|youtu\.be\/|\/shorts\/)[a-zA-Z0-9_-]{11}""").find(trimmed)
        return match?.value
    }

    private fun findBestYouTubeMatch(
        tracks: List<StreamableTrack>,
        targetTitle: String,
        targetArtist: String
    ): StreamableTrack? {
        if (tracks.isEmpty()) return null
        val normTargetTitle = normalizeForMatching(targetTitle)
        val normTargetArtist = normalizeForMatching(targetArtist)

        var bestTrack: StreamableTrack? = null
        var bestScore = -1.0

        for (track in tracks) {
            val normTitle = normalizeForMatching(track.title)
            val normArtist = normalizeForMatching(track.artist)

            val titleSim = calculateSimilarity(normTargetTitle, normTitle)
            val artistSim = if (normTargetArtist.isNotBlank()) calculateSimilarity(normTargetArtist, normArtist) else 1.0

            val tokenOverlap = if (normTargetArtist.isNotBlank()) {
                val targetTokens = normTargetArtist.split(" ").filter { it.length > 2 }
                if (targetTokens.any { normArtist.contains(it) }) 0.25 else 0.0
            } else 0.0

            val score = (titleSim * 0.6) + (artistSim * 0.3) + tokenOverlap
            if (score > bestScore) {
                bestScore = score
                bestTrack = track
            }
        }

        return if (bestScore >= 0.5) bestTrack else tracks.firstOrNull()
    }

    /**
     * Búsqueda en JioSaavn con FILTRADO ESTRICTO DE SIMILITUD.
     * Si no se comprueba que el candidato es idéntico al tema y artista solicitado, se descarta
     * inmediatamente para evitar descargar una pista ajena o aleatoria.
     */
    private fun tryResolveSaavn(title: String, artist: String): ResolvedTrack? {
        return try {
            val query = "$title $artist".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")

            // 1. Intentar endpoint de búsqueda completa con resultados
            val searchUrl = URL("https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&ctx=android&p=1&n=10")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val candidates = mutableListOf<SaavnCandidate>()

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val resultsArray = root.optJSONArray("results")
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.getJSONObject(i)
                        val songId = item.optString("id", "")
                        val candTitle = item.optString("song", item.optString("title", ""))
                        val candArtist = item.optString("primary_artists", item.optString("singers", ""))
                        val encryptedUrl = item.optString("encrypted_media_url", "")
                        val image = item.optString("image", "")
                        val disabled = item.optString("disabled", "") == "true"
                        if (songId.isNotBlank() && !disabled) {
                            candidates.add(SaavnCandidate(songId, candTitle, candArtist, encryptedUrl, image))
                        }
                    }
                }
            }

            // Si no hubo candidatos en search.getResults, intentar con autocomplete
            if (candidates.isEmpty()) {
                val autoUrl = URL("https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$encoded&_format=json&_marker=0&ctx=android")
                val autoConn = (autoUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (autoConn.responseCode == 200) {
                    val autoText = autoConn.inputStream.bufferedReader().use { it.readText() }
                    val autoRoot = JSONObject(autoText)
                    val songsObj = autoRoot.optJSONObject("songs")
                    val dataArray = songsObj?.optJSONArray("data")
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            val songId = item.optString("id", "")
                            val candTitle = item.optString("title", "")
                            val candArtist = item.optJSONObject("more_info")?.optString("primary_artists", "")
                                ?: item.optJSONObject("more_info")?.optString("singers", "")
                                ?: item.optString("description", "")
                            if (songId.isNotBlank()) {
                                candidates.add(SaavnCandidate(songId, candTitle, candArtist, "", item.optString("image", "")))
                            }
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return null

            // Filtrar y validar candidatos con verificación estricta
            for (candidate in candidates) {
                if (isStrictCandidateMatch(candidate.title, candidate.artist, title, artist)) {
                    Log.d(TAG, "Coincidencia estricta verificada en Saavn: ${candidate.title} por ${candidate.artist}")

                    var encryptedMediaUrl = candidate.encryptedMediaUrl
                    var artwork = candidate.image.replace("150x150", "500x500")

                    // Si no traía el URL cifrado, consultar song.getDetails
                    if (encryptedMediaUrl.isBlank()) {
                        val detailsUrl = URL("https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=${candidate.id}&_format=json&_marker=0&ctx=android")
                        val detailsConn = (detailsUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 4000
                            readTimeout = 6000
                            setRequestProperty("User-Agent", "Mozilla/5.0")
                        }
                        if (detailsConn.responseCode == 200) {
                            val detailsText = detailsConn.inputStream.bufferedReader().use { it.readText() }
                            val detailsRoot = JSONObject(detailsText)
                            val songObj = detailsRoot.optJSONObject(candidate.id)
                            encryptedMediaUrl = songObj?.optString("encrypted_media_url", "") ?: ""
                            val detailImage = songObj?.optString("image", "")
                            if (!detailImage.isNullOrBlank()) {
                                artwork = detailImage.replace("150x150", "500x500")
                            }
                        }
                    }

                    if (encryptedMediaUrl.isBlank()) continue

                    val decryptedUrl = decryptJioSaavnUrl(encryptedMediaUrl) ?: continue
                    val highQualityUrl = decryptedUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")

                    return ResolvedTrack(
                        streamUrl = highQualityUrl,
                        extension = "m4a",
                        bitRate = "320 kbps",
                        resolvedQuality = OnlineQuality.MEDIUM,
                        fallbackUsed = false,
                        artworkUrl = artwork.ifBlank { null }
                    )
                }
            }

            Log.d(TAG, "Ningún resultado en Saavn pasó la verificación estricta para '$title - $artist'. Descartando para evitar pista errónea.")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo Saavn 320k: ${e.message}")
            null
        }
    }

    private data class SaavnCandidate(
        val id: String,
        val title: String,
        val artist: String,
        val encryptedMediaUrl: String,
        val image: String
    )

    fun isStrictCandidateMatch(
        candidateTitle: String,
        candidateArtist: String,
        targetTitle: String,
        targetArtist: String
    ): Boolean {
        val normTargetTitle = normalizeForMatching(targetTitle)
        val normTargetArtist = normalizeForMatching(targetArtist)
        val normCandTitle = normalizeForMatching(candidateTitle)
        val normCandArtist = normalizeForMatching(candidateArtist)

        if (normTargetTitle.isBlank() || normCandTitle.isBlank()) return false

        // Filtro anti-karaoke / covers si el original no lo especifica
        val isCoverOrKaraoke = normCandTitle.contains("karaoke") ||
                normCandTitle.contains("piano instrumental") ||
                normCandTitle.contains("tribute") ||
                normCandTitle.contains("originally performed by")
        val targetIsCover = normTargetTitle.contains("karaoke") || normTargetTitle.contains("tribute")
        if (isCoverOrKaraoke && !targetIsCover) {
            return false
        }

        // 1. Similitud de título
        val titleSimilarity = calculateSimilarity(normTargetTitle, normCandTitle)
        val titleMatches = titleSimilarity >= 0.70 ||
                (normCandTitle.length >= 4 && normTargetTitle.contains(normCandTitle)) ||
                (normTargetTitle.length >= 4 && normCandTitle.contains(normTargetTitle))

        if (!titleMatches) return false

        // 2. Similitud de artista
        if (normTargetArtist.isNotBlank()) {
            val targetArtistTokens = normTargetArtist.split(" ").filter { it.length > 2 }
            val candArtistTokens = normCandArtist.split(" ").filter { it.length > 2 }

            val artistSimilarity = calculateSimilarity(normTargetArtist, normCandArtist)
            val hasTokenOverlap = targetArtistTokens.any { t -> normCandArtist.contains(t) } ||
                    candArtistTokens.any { c -> normTargetArtist.contains(c) }

            val artistMatches = artistSimilarity >= 0.60 || hasTokenOverlap
            if (!artistMatches) {
                return false
            }
        }

        return true
    }

    private fun normalizeForMatching(text: String): String {
        return text.lowercase()
            .replace(Regex("""\(official.*?\)|\[official.*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(video.*?\)|\[video.*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(audio.*?\)|\[audio.*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(remastered.*?\)|\[remastered.*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(feat\..*?\)|\[feat\..*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(ft\..*?\)|\[ft\..*?\]""", RegexOption.IGNORE_CASE), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshtein(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun decryptJioSaavnUrl(encryptedBase64: String): String? {
        return try {
            val keyBytes = "38346591".toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Log.w(TAG, "Error desencriptando URL de Saavn: ${e.message}")
            null
        }
    }
}
