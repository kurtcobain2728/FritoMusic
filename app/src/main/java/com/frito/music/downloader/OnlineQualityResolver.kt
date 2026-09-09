package com.frito.music.downloader

import android.content.Context
import android.util.Base64
import android.util.Log
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
    val fallbackUsed: Boolean = false
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
                    // 1. Intentar resolver FLAC nativo sin pérdida (Qobuz)
                    val losslessResult = LosslessClient.resolve(context, title, artist)
                    if (losslessResult != null) {
                        Log.d(TAG, "Resuelto FLAC nativo: ${losslessResult.bitRate}")
                        return@runCatching losslessResult
                    }

                    // 2. Fallback a Saavn 320 kbps
                    val saavnResult = tryResolveSaavn(title, artist)
                    if (saavnResult != null) {
                        Log.d(TAG, "FLAC no disponible, fallback a Saavn 320 kbps")
                        return@runCatching saavnResult.copy(
                            resolvedQuality = OnlineQuality.MEDIUM,
                            fallbackUsed = true
                        )
                    }

                    // 3. Fallback final a YouTube Music 160 kbps
                    Log.d(TAG, "Fallback final a YouTube Music Normal")
                    resolveNormal(videoId, title, artist).copy(
                        resolvedQuality = OnlineQuality.NORMAL,
                        fallbackUsed = true
                    )
                }

                OnlineQuality.MEDIUM -> {
                    // Intentar Saavn 320 kbps
                    val saavnResult = tryResolveSaavn(title, artist)
                    if (saavnResult != null) {
                        return@runCatching saavnResult
                    }

                    // Fallback a YouTube Normal
                    Log.d(TAG, "Saavn 320k no disponible, fallback a YouTube Normal")
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
        val effectiveVideoId = if (videoId.isNotBlank() && !videoId.startsWith("http") && videoId.length == 11) {
            videoId
        } else {
            val query = "$title $artist".trim()
            if (query.isNotBlank()) {
                YouTubeRepository.search(query).getOrNull()?.firstOrNull()?.videoId ?: videoId
            } else {
                videoId
            }
        }

        val streamUrl = YouTubeRepository.getStreamUrl(effectiveVideoId).getOrThrow()
        return ResolvedTrack(
            streamUrl = streamUrl,
            extension = "m4a",
            bitRate = "160 kbps",
            resolvedQuality = OnlineQuality.NORMAL,
            fallbackUsed = false
        )
    }

    private fun tryResolveSaavn(title: String, artist: String): ResolvedTrack? {
        return try {
            val query = "$title $artist".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = URL("https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$encoded&_format=json&_marker=0&ctx=android")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return null
            val searchJson = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(searchJson)
            val songsObj = root.optJSONObject("songs") ?: return null
            val dataArray = songsObj.optJSONArray("data") ?: return null
            if (dataArray.length() == 0) return null

            val firstSong = dataArray.getJSONObject(0)
            val songId = firstSong.optString("id", "")
            if (songId.isBlank()) return null

            val detailsUrl = URL("https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=$songId&_format=json&_marker=0&ctx=android")
            val detailsConn = (detailsUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (detailsConn.responseCode != 200) return null
            val detailsJson = detailsConn.inputStream.bufferedReader().use { it.readText() }
            val detailsRoot = JSONObject(detailsJson)
            val songObj = detailsRoot.optJSONObject(songId) ?: return null
            val encryptedUrl = songObj.optString("encrypted_media_url", "")
            if (encryptedUrl.isBlank()) return null

            val decryptedUrl = decryptJioSaavnUrl(encryptedUrl) ?: return null
            val highQualityUrl = decryptedUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")

            ResolvedTrack(
                streamUrl = highQualityUrl,
                extension = "m4a",
                bitRate = "320 kbps",
                resolvedQuality = OnlineQuality.MEDIUM,
                fallbackUsed = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo Saavn 320k: ${e.message}")
            null
        }
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
