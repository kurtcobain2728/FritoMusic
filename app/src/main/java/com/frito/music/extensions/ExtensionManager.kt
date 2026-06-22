package com.frito.music.extensions

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ExtensionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("extensions_prefs", Context.MODE_PRIVATE)
    private val extensionsDir = File(context.filesDir, "extensions").apply {
        if (!exists()) mkdirs()
    }

    suspend fun fetchRegistry(registryUrl: String): ExtensionRegistry? = withContext(Dispatchers.IO) {
        try {
            val url = URL(registryUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseRegistry(jsonString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun parseRegistry(jsonString: String): ExtensionRegistry {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val updatedAt = root.optString("updated_at", "")
        
        val extensionsArray = root.optJSONArray("extensions")
        val extensionsList = mutableListOf<ExtensionInfo>()
        
        if (extensionsArray != null) {
            for (i in 0 until extensionsArray.length()) {
                val extObj = extensionsArray.getJSONObject(i)
                
                val tagsArray = extObj.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.getString(j))
                    }
                }

                extensionsList.add(
                    ExtensionInfo(
                        id = extObj.optString("id"),
                        name = extObj.optString("name"),
                        displayName = extObj.optString("display_name"),
                        version = extObj.optString("version"),
                        description = extObj.optString("description"),
                        downloadUrl = extObj.optString("download_url"),
                        category = extObj.optString("category"),
                        tags = tagsList,
                        downloads = extObj.optInt("downloads", 0),
                        updatedAt = extObj.optString("updated_at"),
                        minAppVersion = extObj.optString("min_app_version"),
                        iconUrl = if (extObj.has("icon_url")) extObj.optString("icon_url") else null
                    )
                )
            }
        }
        return ExtensionRegistry(version, updatedAt, extensionsList)
    }

    suspend fun downloadExtension(
        extension: ExtensionInfo,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(extension.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val fileLength = connection.contentLength
            val outputFile = File(extensionsDir, "${extension.name}.spotiflac-ext")
            
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (inputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                outputStream.write(data, 0, count)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Guardar la versión y el nombre en SharedPreferences
            prefs.edit()
                .putString("ext_version_${extension.id}", extension.version)
                .putString("ext_name_${extension.id}", extension.displayName)
                .apply()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun getInstalledVersion(extensionId: String): String? {
        return prefs.getString("ext_version_$extensionId", null)
    }

    fun isExtensionInstalled(extensionId: String): Boolean {
        return prefs.contains("ext_version_$extensionId")
    }

    fun deleteExtension(extensionId: String, extensionName: String) {
        val file = File(extensionsDir, "$extensionName.spotiflac-ext")
        if (file.exists()) {
            file.delete()
        }
        prefs.edit()
            .remove("ext_version_$extensionId")
            .remove("ext_name_$extensionId")
            .apply()
    }

    fun getInstalledExtensionNames(): List<Pair<String, String>> {
        val installed = mutableListOf<Pair<String, String>>()
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (key.startsWith("ext_name_")) {
                val id = key.removePrefix("ext_name_")
                val name = prefs.getString(key, id) ?: id
                installed.add(id to name)
            }
        }
        return installed
    }
}
