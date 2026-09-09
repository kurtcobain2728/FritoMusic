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

            // Indexar en MediaStore para que aparezca de inmediato en "Inicio"
            StorageUtils.scanAudioFile(applicationContext, destinationFile)

            Log.d(TAG, "Descarga completada y guardada en: ${destinationFile.absolutePath}")
            updateNotification(notificationId, trackName, 100, 100, "Descarga completada", "✔")
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
            updateNotification(notificationId, trackName, 0, 100, "Error: ${e.message}", "")
            Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Error desconocido"),
                    "trackName" to trackName,
                    "artistName" to artistName
                )
            )
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

    private fun createForegroundInfo(notificationId: Int, trackName: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Descargando: $trackName")
            .setContentText("Iniciando descarga...")
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
        subText: String
    ) {
        runCatching {
            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(trackName)
                .setContentText(text)
                .setSubText(subText)
                .setSmallIcon(if (progress >= 100) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
                .setProgress(max, progress, progress == 0 && text.contains("Resolviendo"))
                .setOngoing(progress < 100)

            notificationManager.notify(notificationId, builder.build())
        }
    }
}
