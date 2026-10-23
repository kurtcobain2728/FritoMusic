package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Gestor para persistir títulos de canciones y nombres de carpetas personalizados por el usuario.
 * Garantiza que los nombres renombrados no se pierdan ni se sobreescriban con los tags ID3 del audio.
 */
object CustomNamesManager {
    private const val PREFS_NAME = "frito_custom_names"
    private const val KEY_AUDIO_TITLES = "custom_audio_titles"
    private const val KEY_AUDIO_ARTISTS = "custom_audio_artists"
    private const val KEY_AUDIO_ALBUMS = "custom_audio_albums"
    private const val KEY_FOLDER_NAMES = "custom_folder_names"

    private var prefs: SharedPreferences? = null

    private val audioTitlesMap = mutableMapOf<String, String>()
    private val audioArtistsMap = mutableMapOf<String, String>()
    private val audioAlbumsMap = mutableMapOf<String, String>()
    private val folderNamesMap = mutableMapOf<String, String>()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun loadMap(prefs: SharedPreferences, key: String, targetMap: MutableMap<String, String>) {
        targetMap.clear()
        val jsonStr = prefs.getString(key, null)
        if (!jsonStr.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    targetMap[k] = json.getString(k)
                }
            }
        }
    }

    private fun load() {
        val p = prefs ?: return
        loadMap(p, KEY_AUDIO_TITLES, audioTitlesMap)
        loadMap(p, KEY_AUDIO_ARTISTS, audioArtistsMap)
        loadMap(p, KEY_AUDIO_ALBUMS, audioAlbumsMap)
        loadMap(p, KEY_FOLDER_NAMES, folderNamesMap)
    }

    private fun save() {
        val p = prefs ?: return
        runCatching {
            fun mapToJson(map: Map<String, String>): String {
                val obj = JSONObject()
                map.forEach { (k, v) -> obj.put(k, v) }
                return obj.toString()
            }

            p.edit()
                .putString(KEY_AUDIO_TITLES, mapToJson(audioTitlesMap))
                .putString(KEY_AUDIO_ARTISTS, mapToJson(audioArtistsMap))
                .putString(KEY_AUDIO_ALBUMS, mapToJson(audioAlbumsMap))
                .putString(KEY_FOLDER_NAMES, mapToJson(folderNamesMap))
                .apply()
            _version.value++
        }
    }

    fun setCustomAudioTitle(oldPath: String, newPath: String, newTitle: String) {
        audioTitlesMap.remove(oldPath)
        audioTitlesMap[newPath] = newTitle
        save()
    }

    fun setCustomAudioMetadata(path: String, title: String?, artist: String?, album: String?) {
        if (!title.isNullOrBlank()) audioTitlesMap[path] = title.trim()
        if (!artist.isNullOrBlank()) audioArtistsMap[path] = artist.trim()
        if (!album.isNullOrBlank()) audioAlbumsMap[path] = album.trim()
        save()
    }

    fun getCustomAudioTitle(path: String): String? = audioTitlesMap[path]
    fun getCustomAudioArtist(path: String): String? = audioArtistsMap[path]
    fun getCustomAudioAlbum(path: String): String? = audioAlbumsMap[path]

    fun setCustomFolderName(oldPath: String, newPath: String, newName: String) {
        folderNamesMap.remove(oldPath)
        folderNamesMap[newPath] = newName
        save()
    }

    fun getCustomFolderName(path: String): String? = folderNamesMap[path]
}
