package com.frito.music.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class OnlineMusicDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_QUALITY = "quality"
        const val KEY_ALBUM_ART_URL = "album_art_url"
        const val KEY_ALBUM_NAME = "album_name"

        const val CHANNEL_ID = "frito_music_downloads"
        private const val TAG = "OnlineMusicDownloadWorker"

        const val PROGRESS = "progress"
        const val SPEED = "speed"
        const val DOWNLOADED_MB = "downloaded_mb"
        const val TOTAL_MB = "total_mb"
        const val TRACK_NAME = "track_name"
        const val ARTIST_NAME = "artist_name"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID)
            ?: inputData.getString("trackId")
            ?: inputData.getString("videoId")
            ?: ""

        val trackName = inputData.getString(KEY_TITLE)
            ?: inputData.getString("trackName")
            ?: inputData.getString("title")
            ?: "Canción desconocida"

        val artistName = inputData.getString(KEY_ARTIST)
            ?: inputData.getString("artistName")
            ?: inputData.getString("artist")
            ?: "Artista desconocido"

        val albumArtUrl = inputData.getString(KEY_ALBUM_ART_URL)
            ?: inputData.getString("albumArtUrl")
            ?: inputData.getString("thumbnailUrl")
            ?: inputData.getString("image")
            ?: ""

        val albumName = inputData.getString(KEY_ALBUM_NAME)
            ?: inputData.getString("albumName")
            ?: inputData.getString("album")
            ?: ""

        val qualityStr = inputData.getString(KEY_QUALITY)
            ?: inputData.getString("quality")
            ?: "normal"

        val quality = when (qualityStr.lowercase()) {
            "flac", "high", "lossless" -> OnlineQuality.HIGH
            "320kbps", "medium", "saavn" -> OnlineQuality.MEDIUM
            else -> OnlineQuality.NORMAL
        }

        createChannel()
        val notificationId = (videoId.ifEmpty { trackName }).hashCode()

        runCatching {
            setForeground(createForegroundInfo(notificationId, trackName))
        }.onFailure {
            Log.w(TAG, "No se pudo establecer foreground info: ${it.message}")
        }

        // Informar nombre y estado inicial para el Gestor de Descargas inmediatamente
        setProgressAsync(
            workDataOf(
                PROGRESS to 0,
                "trackName" to trackName,
                TRACK_NAME to trackName,
                ARTIST_NAME to artistName,
                SPEED to "Iniciando...",
                DOWNLOADED_MB to 0f,
                TOTAL_MB to 0f
            )
        )

        val tempDir = File(applicationContext.cacheDir, "online_downloads").apply { mkdirs() }
        val tempFile = File(tempDir, "temp_${System.currentTimeMillis()}.tmp")

        try {
            updateNotification(notificationId, trackName, 0, 100, "Resolviendo mejor calidad...", "")

            val resolvedResult = OnlineQualityResolver.resolve(
                context = applicationContext,
                videoId = videoId,
                title = trackName,
                artist = artistName,
                requestedQuality = quality
            )

            if (resolvedResult.isFailure) {
                val err = resolvedResult.exceptionOrNull()?.message ?: "No se pudo obtener enlace de descarga"
                Log.e(TAG, "Fallo al resolver track: $err")
                updateNotification(notificationId, trackName, 0, 100, "Error: $err", "")
                return@withContext Result.failure(
                    workDataOf(
                        "error" to err,
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }

            val resolved = resolvedResult.getOrThrow()
            val finalExtension = resolved.extension

            // Descarga HTTP directa de la URL resuelta (FLAC, Saavn 320k o YouTube 160k)
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(resolved.streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val msg = "Error HTTP ${response.code}"
                updateNotification(notificationId, trackName, 0, 100, msg, "")
                return@withContext Result.failure(
                    workDataOf(
                        "error" to msg,
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                workDataOf(
                    "error" to "Cuerpo de respuesta vacío",
                    "trackName" to trackName,
                    "artistName" to artistName
                )
            )
            val totalBytes = body.contentLength()
            val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    var lastUpdate = 0L
                    var lastBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive || isStopped) {
                            tempFile.delete()
                            return@withContext Result.failure(
                                workDataOf(
                                    "error" to "Descarga cancelada",
                                    "trackName" to trackName,
                                    "artistName" to artistName
                                )
                            )
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 700) {
                            val elapsedSec = (now - lastUpdate) / 1000f
                            val speedKbps = if (elapsedSec > 0) ((downloadedBytes - lastBytes) / 1024f) / elapsedSec else 0f
                            val speedText = if (speedKbps > 1024) String.format("%.1f MB/s", speedKbps / 1024f) else String.format("%.0f KB/s", speedKbps)
                            val downloadedMb = downloadedBytes / (1024f * 1024f)
                            val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99) else 0

                            lastUpdate = now
                            lastBytes = downloadedBytes

                            setProgressAsync(
                                workDataOf(
                                    PROGRESS to percent,
                                    DOWNLOADED_MB to downloadedMb,
                                    TOTAL_MB to totalMb,
                                    SPEED to speedText,
                                    "trackName" to trackName,
                                    TRACK_NAME to trackName,
                                    ARTIST_NAME to artistName
                                )
                            )
                            val progressText = if (totalMb > 0) String.format("%.1f / %.1f MB", downloadedMb, totalMb) else String.format("%.1f MB", downloadedMb)
                            updateNotification(notificationId, trackName, percent, 100, progressText, speedText)
                        }
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                updateNotification(notificationId, trackName, 0, 100, "Error: archivo vacío", "")
                return@withContext Result.failure(
                    workDataOf(
                        "error" to "Archivo descargado vacío",
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }

            // Mover al destino final en Almacenamiento Principal / FritoMusic / <Artista> / <Canción>.<ext>
            val destinationFile = StorageUtils.createDirectAudioFile(artistName, trackName, finalExtension)
            tempFile.copyTo(destinationFile, overwrite = true)
            tempFile.delete()

            // 1. Descargar y guardar la carátula en máxima resolución HD (1200x1200 / 1080p)
            val targetCoverUrl = getHighResCoverUrl(albumArtUrl, resolved.artworkUrl, videoId)

            if (!targetCoverUrl.isNullOrBlank()) {
                runCatching {
                    var artRequest = Request.Builder()
                        .url(targetCoverUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    var artResponse = client.newCall(artRequest).execute()

                    // Si maxresdefault da 404 (algunos videos no tienen miniatura 1080p), fallback a hqdefault
                    if (!artResponse.isSuccessful && targetCoverUrl.contains("maxresdefault.jpg")) {
                        val fallbackUrl = targetCoverUrl.replace("maxresdefault.jpg", "hqdefault.jpg")
                        artRequest = Request.Builder()
                            .url(fallbackUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()
                        artResponse = client.newCall(artRequest).execute()
                    }

                    if (artResponse.isSuccessful) {
                        val artBytes = artResponse.body?.bytes()
                        if (artBytes != null && artBytes.isNotEmpty()) {
                            val songCoverFile = File(destinationFile.parentFile, "${destinationFile.nameWithoutExtension}.jpg")
                            val folderCoverFile = File(destinationFile.parentFile, "cover.jpg")
                            songCoverFile.writeBytes(artBytes)
                            if (!folderCoverFile.exists()) {
                                folderCoverFile.writeBytes(artBytes)
                            }
                            StorageUtils.scanAudioFile(applicationContext, songCoverFile)
                            Log.d(TAG, "Carátula HD descargada y guardada en: ${songCoverFile.absolutePath} (${artBytes.size / 1024} KB)")
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "No se pudo descargar la carátula: ${it.message}")
                }
            }

            // 2. Indexar en MediaStore con metadatos para que aparezca de inmediato en "Inicio"
            StorageUtils.scanAudioFile(
                context = applicationContext,
                file = destinationFile,
                title = trackName,
                artist = artistName,
                album = albumName.ifEmpty { artistName }
            )

            Log.d(TAG, "Descarga completada y guardada en: ${destinationFile.absolutePath}")
            updateNotification(notificationId, trackName, 100, 100, "Descarga completada", "✔", artistName)
            setProgressAsync(
                workDataOf(
                    PROGRESS to 100,
                    "trackName" to trackName,
                    TRACK_NAME to trackName,
                    ARTIST_NAME to artistName,
                    SPEED to "Completado",
                    DOWNLOADED_MB to (destinationFile.length() / (1024f * 1024f)),
                    TOTAL_MB to (destinationFile.length() / (1024f * 1024f))
                )
            )
            delay(1000)

            Result.success(
                workDataOf(
                    "uri" to destinationFile.absolutePath,
                    "trackName" to trackName,
                    "artistName" to artistName
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la descarga online", e)
            tempFile.delete()
            updateNotification(notificationId, trackName, 0, 100, "Error: ${e.message}", "", artistName)
            Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Error desconocido"),
                    "trackName" to trackName,
                    "artistName" to artistName
                )
            )
        }
    }

    private fun getHighResCoverUrl(albumArtUrl: String, resolvedArtUrl: String?, videoId: String): String? {
        val raw = when {
            albumArtUrl.isNotBlank() -> albumArtUrl
            !resolvedArtUrl.isNullOrBlank() -> resolvedArtUrl
            videoId.isNotBlank() && !videoId.startsWith("http") && videoId.length == 11 ->
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            else -> null
        } ?: return null

        return when {
            raw.contains("googleusercontent.com") -> {
                val base = if (raw.contains("=w")) raw.split("=w")[0]
                else if (raw.contains("=s")) raw.split("=s")[0]
                else raw
                "$base=w1200-h1200-l90-rj"
            }
            raw.contains("yt3.ggpht.com") -> {
                val base = raw.split("=")[0].split("-s")[0]
                "$base=s1200"
            }
            raw.contains("i.ytimg.com") -> {
                raw.replace(Regex("""(default|mqdefault|hqdefault|sddefault)\.jpg"""), "maxresdefault.jpg")
            }
            raw.contains("saavncdn.com") -> {
                raw.replace("150x150", "500x500").replace("50x50", "500x500")
            }
            else -> raw
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Descargas de Música"
            val descriptionText = "Muestra el progreso de descargas de Frito Music"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(notificationId: Int, trackName: String, artistName: String = ""): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(trackName)
            .setContentText(if (artistName.isNotBlank() && artistName != "Artista desconocido") artistName else "Iniciando descarga...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(
        notificationId: Int,
        trackName: String,
        progress: Int,
        max: Int,
        text: String,
        subText: String,
        artistName: String = ""
    ) {
        runCatching {
            val content = if (artistName.isNotBlank() && artistName != "Artista desconocido") {
                "$artistName • $text"
            } else {
                text
            }
            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(trackName)
                .setContentText(content)
                .setSubText(subText)
                .setSmallIcon(if (progress >= 100) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setProgress(max, progress, progress == 0 && text.contains("Resolviendo"))
                .setOngoing(progress < 100)

            notificationManager.notify(notificationId, builder.build())
        }
    }
}
