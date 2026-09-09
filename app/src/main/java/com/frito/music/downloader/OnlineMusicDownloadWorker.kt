package com.frito.music.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.frito.music.MainActivity
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
        const val KEY_TRACK_NUMBER = "track_number"
        const val KEY_TOTAL_TRACKS = "total_tracks"

        const val CHANNEL_ID = "frito_music_downloads"
        const val CHANNEL_ID_COMPLETE = "frito_music_downloads_complete"
        private const val TAG = "OnlineMusicDownloadWorker"

        const val PROGRESS = "progress"
        const val SPEED = "speed"
        const val DOWNLOADED_MB = "downloaded_mb"
        const val TOTAL_MB = "total_mb"
        const val TRACK_NAME = "track_name"
        const val ARTIST_NAME = "artist_name"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val progressChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Descargas en Progreso",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Muestra el progreso y velocidad de descarga en tiempo real"
                    setShowBadge(false)
                }

                val completeChannel = NotificationChannel(
                    CHANNEL_ID_COMPLETE,
                    "Descargas Completadas",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Avisa cuando una canción termina de descargarse"
                    setShowBadge(true)
                }

                notificationManager.createNotificationChannel(progressChannel)
                notificationManager.createNotificationChannel(completeChannel)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val videoId = inputData.getString(KEY_VIDEO_ID)
            ?: inputData.getString("trackId")
            ?: inputData.getString("videoId")
            ?: ""

        val trackName = inputData.getString(KEY_TITLE)
            ?: inputData.getString("trackName")
            ?: inputData.getString("title")
            ?: "Descargando canción"

        val artistName = inputData.getString(KEY_ARTIST)
            ?: inputData.getString("artistName")
            ?: inputData.getString("artist")
            ?: ""

        val trackNumber = inputData.getInt(KEY_TRACK_NUMBER, 0).let { if (it > 0) it else null }
            ?: inputData.getInt("trackNumber", 0).let { if (it > 0) it else null }
        val totalTracks = inputData.getInt(KEY_TOTAL_TRACKS, 0).let { if (it > 0) it else null }
            ?: inputData.getInt("totalTracks", 0).let { if (it > 0) it else null }

        val notificationId = Math.abs((videoId.ifEmpty { trackName }).hashCode())
        return createForegroundInfo(notificationId, trackName, artistName, trackNumber, totalTracks)
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

        val trackNumber = inputData.getInt(KEY_TRACK_NUMBER, 0).let { if (it > 0) it else null }
            ?: inputData.getInt("trackNumber", 0).let { if (it > 0) it else null }

        val totalTracks = inputData.getInt(KEY_TOTAL_TRACKS, 0).let { if (it > 0) it else null }
            ?: inputData.getInt("totalTracks", 0).let { if (it > 0) it else null }

        val qualityStr = inputData.getString(KEY_QUALITY)
            ?: inputData.getString("quality")
            ?: "normal"

        val quality = when (qualityStr.lowercase()) {
            "flac", "high", "lossless" -> OnlineQuality.HIGH
            "320kbps", "medium", "saavn" -> OnlineQuality.MEDIUM
            else -> OnlineQuality.NORMAL
        }

        createNotificationChannels(applicationContext)
        val notificationId = Math.abs((videoId.ifEmpty { trackName }).hashCode())
        val completionNotificationId = notificationId + 100000

        runCatching {
            setForeground(createForegroundInfo(notificationId, trackName, artistName, trackNumber, totalTracks))
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
            val resolvingText = if (trackNumber != null && totalTracks != null && totalTracks > 1) {
                "Pista $trackNumber de $totalTracks • Resolviendo calidad..."
            } else {
                "Resolviendo mejor calidad..."
            }
            updateNotification(notificationId, trackName, 0, 100, resolvingText, "", artistName, trackNumber, totalTracks)

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
                showErrorNotification(completionNotificationId, trackName, artistName, err)
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
                showErrorNotification(completionNotificationId, trackName, artistName, msg)
                return@withContext Result.failure(
                    workDataOf(
                        "error" to msg,
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }

            val body = response.body ?: run {
                val msg = "Cuerpo de respuesta vacío"
                showErrorNotification(completionNotificationId, trackName, artistName, msg)
                return@withContext Result.failure(
                    workDataOf(
                        "error" to msg,
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }
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
                            updateNotification(notificationId, trackName, percent, 100, progressText, speedText, artistName, trackNumber, totalTracks)
                        }
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                updateNotification(notificationId, trackName, 0, 100, "Error: archivo vacío", "", artistName, trackNumber, totalTracks)
                return@withContext Result.failure(
                    workDataOf(
                        "error" to "Archivo descargado vacío",
                        "trackName" to trackName,
                        "artistName" to artistName
                    )
                )
            }

            // Mover al destino final en Almacenamiento Principal / FritoMusic / <Artista> / [<Álbum>/] <01 - Canción>.<ext>
            val destinationFile = StorageUtils.createDirectAudioFile(
                artistName = artistName,
                trackName = trackName,
                extension = finalExtension,
                albumName = albumName.ifEmpty { null },
                trackNumber = trackNumber
            )
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

            // 2. Descargar y guardar la letra sincronizada (.lrc) para reproducción 100% OFFLINE
            runCatching {
                val lyricsRepo = com.frito.music.data.repository.LyricsRepository(applicationContext)
                val lyrics = lyricsRepo.getLyrics(
                    title = trackName,
                    artist = artistName,
                    videoId = videoId.takeIf { it.isNotBlank() },
                    localFilePath = destinationFile.absolutePath
                )
                if (lyrics != null) {
                    val lrcFile = File(destinationFile.parentFile, "${destinationFile.nameWithoutExtension}.lrc")
                    val lrcContent = if (lyrics.lines.isNotEmpty()) {
                        val sb = StringBuilder()
                        lyrics.lines.forEach { line ->
                            val min = line.timestampMs / 60000
                            val sec = (line.timestampMs % 60000) / 1000
                            val hundredths = (line.timestampMs % 1000) / 10
                            sb.append(String.format("[%02d:%02d.%02d]%s\n", min, sec, hundredths, line.text))
                        }
                        sb.toString()
                    } else {
                        lyrics.plainLyrics ?: ""
                    }
                    if (lrcContent.isNotBlank() && !lrcFile.exists()) {
                        lrcFile.writeText(lrcContent)
                        Log.d(TAG, "Letra sincronizada descargada y guardada en: ${lrcFile.absolutePath}")
                    }
                }
            }.onFailure {
                Log.w(TAG, "No se pudo obtener letra para $trackName: ${it.message}")
            }

            // 3. Indexar en MediaStore con metadatos para que aparezca de inmediato en "Inicio"
            StorageUtils.scanAudioFile(
                context = applicationContext,
                file = destinationFile,
                title = trackName,
                artist = artistName,
                album = albumName.ifEmpty { artistName },
                trackNumber = trackNumber
            )

            Log.d(TAG, "Descarga completada y guardada en: ${destinationFile.absolutePath}")
            showCompletionNotification(
                completionNotificationId = completionNotificationId,
                trackName = trackName,
                artistName = artistName,
                albumName = albumName,
                trackNumber = trackNumber,
                totalTracks = totalTracks
            )
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
            delay(500)

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
            showErrorNotification(completionNotificationId, trackName, artistName, e.message ?: "Error desconocido")
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

    private fun createForegroundInfo(
        notificationId: Int,
        trackName: String,
        artistName: String = "",
        trackNumber: Int? = null,
        totalTracks: Int? = null
    ): ForegroundInfo {
        val title = if (trackNumber != null && totalTracks != null && totalTracks > 1) {
            "($trackNumber/$totalTracks) $trackName"
        } else {
            trackName
        }
        val content = if (artistName.isNotBlank() && artistName != "Artista desconocido") {
            if (trackNumber != null && totalTracks != null && totalTracks > 1) {
                "$artistName • Pista $trackNumber de $totalTracks"
            } else {
                artistName
            }
        } else "Iniciando descarga..."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
        artistName: String = "",
        trackNumber: Int? = null,
        totalTracks: Int? = null
    ) {
        runCatching {
            val title = if (trackNumber != null && totalTracks != null && totalTracks > 1) {
                "($trackNumber/$totalTracks) $trackName"
            } else {
                trackName
            }
            val content = if (artistName.isNotBlank() && artistName != "Artista desconocido") {
                "$artistName • $text"
            } else {
                text
            }
            val effectiveSubText = if (trackNumber != null && totalTracks != null && totalTracks > 1) {
                if (subText.isNotBlank()) "$subText • $trackNumber/$totalTracks" else "$trackNumber/$totalTracks"
            } else {
                subText
            }
            val isIndeterminate = progress == 0 && text.contains("Resolviendo")
            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSubText(effectiveSubText)
                .setSmallIcon(if (progress >= 100) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setProgress(max, progress, isIndeterminate)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            notificationManager.notify(notificationId, builder.build())
        }
    }

    private fun showCompletionNotification(
        completionNotificationId: Int,
        trackName: String,
        artistName: String = "",
        albumName: String = "",
        trackNumber: Int? = null,
        totalTracks: Int? = null
    ) {
        runCatching {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                completionNotificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isAlbum = trackNumber != null && totalTracks != null && totalTracks > 1
            val isLastTrack = isAlbum && trackNumber == totalTracks

            val titleText = when {
                isLastTrack && albumName.isNotBlank() -> "¡Álbum completo descargado!"
                isAlbum -> "($trackNumber/$totalTracks) $trackName"
                else -> trackName
            }

            val content = when {
                isLastTrack && albumName.isNotBlank() -> "$albumName • $artistName"
                isAlbum -> "$artistName • Pista $trackNumber de $totalTracks descargada"
                artistName.isNotBlank() && artistName != "Artista desconocido" -> "$artistName • Descarga completada"
                else -> "Descarga completada con éxito"
            }

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID_COMPLETE)
                .setContentTitle(titleText)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManager.notify(completionNotificationId, builder.build())
        }
    }

    private fun showErrorNotification(
        completionNotificationId: Int,
        trackName: String,
        artistName: String = "",
        errorMessage: String
    ) {
        runCatching {
            val content = if (artistName.isNotBlank() && artistName != "Artista desconocido") {
                "$artistName • $errorMessage"
            } else {
                errorMessage
            }

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID_COMPLETE)
                .setContentTitle("Error al descargar: $trackName")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManager.notify(completionNotificationId, builder.build())
        }
    }
}
