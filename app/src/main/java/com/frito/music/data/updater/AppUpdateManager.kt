package com.frito.music.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.frito.music.data.network.yt.PlaybackHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val isUpToDate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long
)

object AppUpdateManager {

    private const val GITHUB_OWNER = "kurtcobain2728"
    private const val GITHUB_REPO = "FritoMusic"
    private const val API_LATEST = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val API_ALL_RELEASES = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"

    fun getAppVersion(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName ?: "1.0"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            }
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Compara dos versiones semánticas (por ej. "1.1.0" vs "1.0.0" o "v2.0" vs "1.5").
     * Devuelve true si remoteVersion es mayor que currentVersion.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val currentVersion = getAppVersion(context)
        val client = PlaybackHttpClient.client

        val request = Request.Builder()
            .url(API_LATEST)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "FritoMusic-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            val jsonString = response.body.string()

            var releaseObj: JSONObject? = null
            if (response.isSuccessful && jsonString.isNotBlank()) {
                releaseObj = JSONObject(jsonString)
            } else if (response.code == 404) {
                // Fallback: intentar listar los releases por si latest no está indexado
                val listRequest = Request.Builder()
                    .url(API_ALL_RELEASES)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "FritoMusic-Android-App")
                    .build()
                val listResp = client.newCall(listRequest).execute()
                val listJson = listResp.body.string()
                if (listResp.isSuccessful && listJson.isNotBlank()) {
                    val arr = JSONArray(listJson)
                    if (arr.length() > 0) {
                        releaseObj = arr.getJSONObject(0)
                    }
                }
            }

            if (releaseObj == null) {
                // No hay releases creados en el repositorio aún
                return@withContext Result.success(
                    UpdateInfo(
                        isUpdateAvailable = false,
                        isUpToDate = true,
                        currentVersion = currentVersion,
                        latestVersion = currentVersion,
                        releaseTitle = "Versión actual",
                        releaseNotes = "No se encontraron nuevas versiones publicadas en GitHub.",
                        apkUrl = null,
                        apkName = null,
                        apkSizeBytes = 0L
                    )
                )
            }

            val tagName = releaseObj.optString("tag_name", "").trim()
            val cleanTagName = tagName.removePrefix("v").removePrefix("V")
            val releaseName = releaseObj.optString("name", tagName)
            val releaseNotes = releaseObj.optString("body", "Sin notas de versión disponibles.")

            // Buscar el APK dentro de los assets
            val assets = releaseObj.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) || url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = isNewerVersion(cleanTagName, currentVersion)
            val hasApk = !apkUrl.isNullOrEmpty()

            Result.success(
                UpdateInfo(
                    isUpdateAvailable = isNewer && hasApk,
                    isUpToDate = !isNewer,
                    currentVersion = currentVersion,
                    latestVersion = cleanTagName.ifBlank { currentVersion },
                    releaseTitle = releaseName,
                    releaseNotes = releaseNotes,
                    apkUrl = apkUrl,
                    apkName = apkName ?: "FritoMusic-v$cleanTagName.apk",
                    apkSizeBytes = apkSize
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Error al verificar actualización", e)
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetFileName: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val client = PlaybackHttpClient.client

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "FritoMusic-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code} al descargar APK"))
            }

            val body = response.body
            val totalBytes = body.contentLength()

            // Guardar en la carpeta de Descargas pública de Android
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationDir = if (publicDownloads.exists() || publicDownloads.mkdirs()) {
                publicDownloads
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            }

            val outputFile = File(destinationDir, targetFileName)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        withContext(Dispatchers.Main) {
                            onProgress(progress, totalRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Error descargando APK", e)
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "El archivo de actualización no fue encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        // Permiso de instalación de fuentes desconocidas en Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(
                        context,
                        "Habilita el permiso para instalar la actualización",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    android.util.Log.w("AppUpdateManager", "No se pudo abrir ajustes de fuentes desconocidas", e)
                }
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Error al iniciar instalador", e)
            Toast.makeText(context, "No se pudo iniciar el instalador: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }
}
