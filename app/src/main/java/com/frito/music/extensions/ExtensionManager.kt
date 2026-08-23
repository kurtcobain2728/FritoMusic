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
import java.util.zip.ZipFile

class ExtensionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("extensions_prefs", Context.MODE_PRIVATE)
    private val extensionsDir = File(context.filesDir, "extensions").apply {
        if (!exists()) mkdirs()
    }

    // Caché en memoria de los manifest.json leídos de cada extensión
    private val manifestCache = mutableMapOf<String, JSONObject?>()

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

                // Guardar el "type" declarado en el registry (p. ej.
                // ["metadata_provider"] o ["download_provider"]) para poder
                // clasificar la extensión ANTES de instalarla.
                val typesArray = extObj.optJSONArray("type")
                if (typesArray != null && typesArray.length() > 0) {
                    val joined = (0 until typesArray.length()).joinToString(",") { typesArray.optString(it) }
                    prefs.edit().putString("ext_type_${extObj.optString("id")}", joined).apply()
                } else {
                    prefs.edit().remove("ext_type_${extObj.optString("id")}").apply()
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
            // El archivo se guarda con el ID de la extensión (así lo busca ExtensionEngine)
            val outputFile = File(extensionsDir, "${extension.id}.spotiflac-ext")
            
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

            manifestCache.remove(extension.id)
            // Guardar la versión y el nombre en SharedPreferences
            prefs.edit()
                .putString("ext_version_${extension.id}", extension.version)
                .putString("ext_name_${extension.id}", extension.displayName)
                .remove("ext_incompatible_${extension.id}")
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
        manifestCache.remove(extensionId)
        prefs.edit()
            .remove("ext_version_$extensionId")
            .remove("ext_name_$extensionId")
            .remove("ext_incompatible_$extensionId")
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

    /**
     * Revisa si index.js contiene async/await/fetch que Mozilla Rhino no soporta.
     */
    fun isCompatible(extensionId: String): Boolean {
        if (prefs.contains("ext_incompatible_$extensionId")) {
            return !prefs.getBoolean("ext_incompatible_$extensionId", false)
        }
        val file = File(extensionsDir, "$extensionId.spotiflac-ext")
        val incompatible = try {
            if (!file.exists()) false
            else {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("index.js") ?: return@use false
                    val js = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    js.contains("async function") || js.contains("await ") || js.contains("fetch(")
                }
            }
        } catch (e: Exception) { false }
        prefs.edit().putBoolean("ext_incompatible_$extensionId", incompatible).apply()
        return !incompatible
    }

    /**
     * Lee el manifest.json del paquete de la extensión instalada (.spotiflac-ext / .sflx son ZIP).
     * Devuelve null si no existe o no se puede leer.
     */
    fun getExtensionManifest(extensionId: String): JSONObject? {
        if (manifestCache.containsKey(extensionId)) return manifestCache[extensionId]
        val manifest: JSONObject? = try {
            val extFile = File(extensionsDir, "$extensionId.spotiflac-ext")
            if (!extFile.exists()) {
                null
            } else {
                ZipFile(extFile).use { zip ->
                    val entry = zip.getEntry("manifest.json")
                    entry?.let { JSONObject(zip.getInputStream(it).bufferedReader().use { r -> r.readText() }) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        manifestCache[extensionId] = manifest
        return manifest
    }

    /**
     * true si la extensión declara el tipo "download_provider".
     * Orden de resolución:
     *  1. manifest.json de la extensión instalada (fuente de verdad)
     *  2. "type" guardado del registry (para extensiones NO instaladas)
     *  3. default true (no romper compatibilidad si no hay datos)
     */
    fun isDownloadProvider(extensionId: String): Boolean {
        val manifest = getExtensionManifest(extensionId)
        if (manifest != null) {
            val types = manifest.optJSONArray("type") ?: return true
            for (i in 0 until types.length()) {
                if (types.optString(i) == "download_provider") return true
            }
            return false
        }
        // No instalada: usar el type del registry si lo tenemos cacheado
        val regTypes = prefs.getString("ext_type_$extensionId", null)
        if (regTypes != null) {
            return regTypes.split(",").any { it.trim() == "download_provider" }
        }
        return true
    }

    /**
     * Motivo por el que una extensión NO puede usarse como servidor de descarga,
     * o null si sí es utilizable. Para mensajes explicativos en la UI.
     */
    fun getServerExclusionReason(extensionId: String): String? {
        return when {
            !isCompatible(extensionId) -> "incompatible con el motor"
            !isDownloadProvider(extensionId) -> "solo metadatos, sin descargas"
            else -> null
        }
    }

    /**
     * Calidades declaradas por la extensión en su manifest (qualityOptions).
     * Devuelve pares (id -> label). Lista vacía si la extensión no las declara.
     */
    fun getQualityOptions(extensionId: String): List<Pair<String, String>> {
        val manifest = getExtensionManifest(extensionId) ?: return emptyList()
        val options = manifest.optJSONArray("qualityOptions") ?: return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until options.length()) {
            val opt = options.optJSONObject(i) ?: continue
            val id = opt.optString("id")
            val label = opt.optString("label").ifEmpty { id }
            if (id.isNotEmpty()) result.add(id to label)
        }
        return result
    }

    /**
     * Configuración "signedSession" del manifest, si la extensión la declara.
     */
    fun getSignedSessionConfig(extensionId: String): JSONObject? {
        val manifest = getExtensionManifest(extensionId) ?: return null
        return manifest.optJSONObject("signedSession")
    }

    fun getExtensionFile(extensionId: String): File {
        return File(extensionsDir, "$extensionId.spotiflac-ext")
    }
}
