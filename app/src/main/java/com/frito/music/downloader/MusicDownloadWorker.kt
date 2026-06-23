package com.frito.music.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.frito.music.R
import com.frito.music.extensions.engine.ExtensionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MusicDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val PROGRESS = "Progress"
        const val SPEED = "Speed"
        const val DOWNLOADED_MB = "DownloadedMB"
        const val TOTAL_MB = "TotalMB"
        
        private const val CHANNEL_ID = "FritoMusicDownloads"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        val extensionId = inputData.getString("extensionId") ?: return@withContext Result.failure()
        val trackName = inputData.getString("trackName") ?: "Desconocido"
        val artistName = inputData.getString("artistName") ?: "Desconocido"
        val albumName = inputData.getString("albumName") ?: ""
        val trackUrl = inputData.getString("trackUrl")

        val notificationId = trackId.hashCode()
        createChannel()

        // Create foreground info immediately to satisfy Android 14+ requirements
        val initialNotification = createNotification(trackName, 0, 100, "Iniciando...", "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForeground(ForegroundInfo(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } else {
            setForeground(ForegroundInfo(notificationId, initialNotification))
        }

        try {
            // 1. Instanciar el motor y obtener URL real
            Log.d("MusicDownloadWorker", "Fetching download URL for $trackName ($trackId)")
            val engine = ExtensionEngine(applicationContext, extensionId)
            val downloadUrl = engine.getDownloadUrl(trackId, trackUrl)
            engine.destroy()

            if (downloadUrl.isNullOrEmpty()) {
                Log.e("MusicDownloadWorker", "No se pudo obtener URL de descarga")
                updateNotification(notificationId, trackName, 0, 100, "Error: Enlace no encontrado", "")
                return@withContext Result.failure(workDataOf("error" to "No download URL available"))
            }

            Log.d("MusicDownloadWorker", "Download URL obtained: $downloadUrl")

            // 2. Crear archivo local usando StorageUtils
            val fileStreamPair = StorageUtils.createAudioFileStream(
                applicationContext, artistName, albumName, trackName
            )
            
            if (fileStreamPair == null) {
                Log.e("MusicDownloadWorker", "No se pudo crear archivo de destino")
                updateNotification(notificationId, trackName, 0, 100, "Error: Sistema de archivos", "")
                return@withContext Result.failure(workDataOf("error" to "File creation failed"))
            }

            val (uri, outputStream) = fileStreamPair
            var success = false

            try {
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                if (connection.responseCode !in 200..299) {
                    throw Exception("HTTP Error ${connection.responseCode}")
                }

                val totalBytes = connection.contentLength.toLong()
                val inputStream: InputStream = connection.inputStream
                
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastUpdate = System.currentTimeMillis()
                var speedStr = ""

                var bytesSinceLastUpdate = 0L

                outputStream.use { out ->
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 1000) {
                            val diffSec = (now - lastUpdate) / 1000f
                            val speedMBps = (bytesSinceLastUpdate / (1024f * 1024f)) / diffSec
                            speedStr = String.format("%.1f MB/s", speedMBps)

                            val progressPercent = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                            val downloadedMB = downloadedBytes / (1024f * 1024f)
                            val totalMB = totalBytes / (1024f * 1024f)

                            setProgressAsync(
                                workDataOf(
                                    PROGRESS to progressPercent,
                                    DOWNLOADED_MB to downloadedMB,
                                    TOTAL_MB to totalMB,
                                    SPEED to speedStr
                                )
                            )

                            val detailText = if (totalBytes > 0) {
                                String.format("%.1f MB / %.1f MB", downloadedMB, totalMB)
                            } else {
                                String.format("%.1f MB", downloadedMB)
                            }
                            
                            updateNotification(notificationId, trackName, progressPercent, 100, detailText, "")
                            
                            lastUpdate = now
                            bytesSinceLastUpdate = 0L
                        }
                    }
                }
                success = true
                
            } catch (e: Exception) {
                Log.e("MusicDownloadWorker", "Stream download failed", e)
                StorageUtils.deleteAudioFile(applicationContext, uri)
                updateNotification(notificationId, trackName, 0, 100, "Error: ${e.message}", "")
                return@withContext Result.failure(workDataOf("error" to e.message))
            } finally {
                if (success) {
                    StorageUtils.commitAudioFile(applicationContext, uri)
                }
            }

            updateNotification(notificationId, trackName, 100, 100, "Descarga completada", "✔")
            delay(1000) // Mostrar el completado un segundo antes de que muera el worker
            
            Result.success(workDataOf("uri" to uri.toString()))
            
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(workDataOf("error" to e.localizedMessage))
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

    private fun createNotification(
        title: String,
        progress: Int,
        max: Int,
        content: String,
        subText: String
    ): android.app.Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Descargando: $title")
            .setContentText(content)
            .setSubText(subText.ifEmpty { null })
            // Reusando algún icono general, ya que no puedo saber el R.drawable exacto sin buscar,
            // pero normalmente R.drawable.ic_launcher_foreground o un icono nativo sirve.
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress < max)
            .setProgress(max, progress, max == 0)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(
        notificationId: Int,
        title: String,
        progress: Int,
        max: Int,
        content: String,
        subText: String
    ) {
        val notification = createNotification(title, progress, max, content, subText)
        notificationManager.notify(notificationId, notification)
    }
}
