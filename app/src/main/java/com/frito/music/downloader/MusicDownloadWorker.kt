package com.frito.music.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.frito.music.extensions.engine.DownloadState
import com.frito.music.extensions.engine.ExtensionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Worker de descargas. Sigue la arquitectura de SpotiFLAC:
 * la EXTENSIÓN ejecuta la descarga completa (resolución de URL, segmentos,
 * escritura a disco vía FileBridge) y el worker orquesta: progreso,
 * notificación, y traslado del archivo final a MediaStore (Music/FritoM/...).
 */
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
        val trackId = inputData.getString("trackId") ?: return@withContext failure("Falta trackId", "Desconocido")
        val extensionId = inputData.getString("extensionId") ?: return@withContext failure("Falta extensionId", "Desconocido")
        val trackName = inputData.getString("trackName") ?: "Desconocido"
        val artistName = inputData.getString("artistName") ?: "Desconocido"
        val albumName = inputData.getString("albumName") ?: ""
        val quality = inputData.getString("quality") ?: "LOSSLESS"

        // Sembrar trackName inmediatamente en progress para evitar ítems sin nombre
        setProgressAsync(workDataOf("trackName" to trackName, PROGRESS to 0))

        val notificationId = trackId.hashCode()
        createChannel()

        // Foreground inmediato (requisito Android 14+)
        val initialNotification = createNotification(trackName, 0, 100, "Iniciando...", "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForeground(ForegroundInfo(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } else {
            setForeground(ForegroundInfo(notificationId, initialNotification))
        }

        DownloadState.cancelRequested = false
        var engine: ExtensionEngine? = null
        val tempDir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        val tempBase = File(tempDir, "track_${trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.part")

        try {
            Log.d("MusicDownloadWorker", "Iniciando descarga: $trackName ($trackId) vía $extensionId [$quality]")
            engine = ExtensionEngine(applicationContext, extensionId)

            if (!engine.hasDownloadCapability()) {
                updateNotification(notificationId, trackName, 0, 100, "Error: servidor sin descargas", "")
                return@withContext failure("Este servidor no soporta descargas", trackName)
            }

            var lastUpdate = 0L
            val resultJson = engine.downloadTrack(trackId, quality, tempBase.absolutePath) { percent ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 800 && percent < 100) {
                    lastUpdate = now
                    val downloadedMB = tempBase.length() / (1024f * 1024f)
                    setProgressAsync(
                        workDataOf(
                            PROGRESS to percent,
                            DOWNLOADED_MB to downloadedMB,
                            TOTAL_MB to 0f,
                            SPEED to "",
                            "trackName" to trackName
                        )
                    )
                    updateNotification(notificationId, trackName, percent, 100, String.format("%.1f MB", downloadedMB), "")
                }
            }
            engine.destroy()
            engine = null

            if (resultJson.isNullOrEmpty()) {
                updateNotification(notificationId, trackName, 0, 100, "Error: sin respuesta", "")
                return@withContext failure("La extensión no devolvió resultado", trackName)
            }

            val result = try { JSONObject(resultJson) } catch (e: Exception) {
                Log.e("MusicDownloadWorker", "Respuesta no-JSON de la extensión: ${resultJson.take(200)}")
                return@withContext failure("Respuesta inválida de la extensión", trackName)
            }

            if (!result.optBoolean("success", false)) {
                val errorMessage = result.optString("error_message").ifEmpty { "Error desconocido de la extensión" }
                Log.e("MusicDownloadWorker", "Descarga fallida: $errorMessage")
                cleanupTemp(tempDir, tempBase)
                val userMessage = humanizeError(errorMessage)
                updateNotification(notificationId, trackName, 0, 100, "Error: $userMessage", "")
                return@withContext failure(userMessage, trackName)
            }

            // Archivo descargado por la extensión (puede haber cambiado la extensión/ruta)
            val resultPath = result.optString("file_path").ifEmpty { tempBase.absolutePath }
            val downloadedFile = File(resultPath)
            if (!downloadedFile.exists() || downloadedFile.length() == 0L) {
                cleanupTemp(tempDir, tempBase)
                updateNotification(notificationId, trackName, 0, 100, "Error: archivo vacío", "")
                return@withContext failure("El archivo descargado no existe o está vacío", trackName)
            }

            // Extensión real del archivo (la reporta la extensión o se deduce del path)
            val realExtension = result.optString("actual_extension")
                .ifEmpty { result.optString("output_extension") }
                .ifEmpty { downloadedFile.extension.let { if (it.isNotEmpty()) ".$it" else "" } }
                .removePrefix(".")
                .ifEmpty { extensionForQuality(quality) }

            // Trasladar a MediaStore: Music/FritoM/{Artista}/{Álbum}/Título.ext
            val finalTitle = result.optString("title").ifEmpty { trackName }
            val finalArtist = result.optString("artist").ifEmpty { artistName }
            val finalAlbum = result.optString("album").ifEmpty { albumName }

            val fileStreamPair = StorageUtils.createAudioFileStream(
                applicationContext, finalArtist, finalAlbum, finalTitle, realExtension
            )
            if (fileStreamPair == null) {
                cleanupTemp(tempDir, tempBase)
                updateNotification(notificationId, trackName, 0, 100, "Error: Sistema de archivos", "")
                return@withContext failure("No se pudo crear el archivo de destino", trackName)
            }

            val (uri, outputStream) = fileStreamPair
            try {
                downloadedFile.inputStream().use { input ->
                    outputStream.use { out -> input.copyTo(out) }
                }
                StorageUtils.commitAudioFile(applicationContext, uri)
            } catch (e: Exception) {
                Log.e("MusicDownloadWorker", "Error guardando en MediaStore", e)
                StorageUtils.deleteAudioFile(applicationContext, uri)
                cleanupTemp(tempDir, tempBase)
                updateNotification(notificationId, trackName, 0, 100, "Error: ${e.message}", "")
                return@withContext failure("Error al guardar: ${e.message}", trackName)
            }

            cleanupTemp(tempDir, tempBase)
            Log.d("MusicDownloadWorker", "Descarga completada: $finalTitle -> $uri")
            updateNotification(notificationId, finalTitle, 100, 100, "Descarga completada", "✔")
            setProgressAsync(workDataOf(PROGRESS to 100, "trackName" to finalTitle))
            delay(1000) // Mostrar el completado un segundo antes de que muera el worker

            Result.success(
                workDataOf(
                    "uri" to uri.toString(),
                    "trackName" to finalTitle,
                    "artistName" to finalArtist
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("MusicDownloadWorker", "Error inesperado en descarga", e)
            runCatching { engine?.destroy() }
            cleanupTemp(tempDir, tempBase)
            updateNotification(notificationId, trackName, 0, 100, "Error: ${e.message}", "")
            failure(e.localizedMessage ?: e.javaClass.simpleName, trackName)
        }
    }

    private fun failure(message: String, trackName: String): Result {
        return Result.failure(
            workDataOf(
                "error" to message,
                "trackName" to trackName
            )
        )
    }

    /** Traduce errores técnicos de la extensión a mensajes entendibles. */
    private fun humanizeError(raw: String): String {
        return when {
            raw.contains("VERIFY_REQUIRED", ignoreCase = true) ||
            raw.contains("verification_required", ignoreCase = true) ||
            raw.contains("signed session", ignoreCase = true) ->
                "Verificación requerida: abre Descargar Música y verifica la sesión del servidor"
            raw.contains("PREVIEW", ignoreCase = true) ->
                "El servidor solo devolvió una vista previa"
            raw.contains("HTTP 403", ignoreCase = true) ->
                "Acceso denegado por el servidor (403)"
            raw.contains("HTTP 404", ignoreCase = true) ->
                "La canción no se encontró en el servidor (404)"
            raw.contains("HTTP 429", ignoreCase = true) ->
                "Límite de peticiones alcanzado, inténtalo más tarde"
            else -> raw.take(120)
        }
    }

    private fun extensionForQuality(quality: String): String {
        return when {
            quality.contains("FLAC", ignoreCase = true) ||
                quality.contains("HI_RES", ignoreCase = true) ||
                quality.contains("LOSSLESS", ignoreCase = true) ||
                quality.contains("Hi-Res", ignoreCase = true) -> "flac"
            quality.contains("opus", ignoreCase = true) -> "opus"
            quality.contains("ogg", ignoreCase = true) -> "ogg"
            quality.startsWith("256") -> "m4a"
            else -> "mp3"
        }
    }

    private fun cleanupTemp(tempDir: File, tempBase: File) {
        runCatching {
            tempDir.listFiles()?.forEach { f ->
                if (f.name.startsWith(tempBase.nameWithoutExtension)) f.delete()
            }
            tempBase.delete()
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
