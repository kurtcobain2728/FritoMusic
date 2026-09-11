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
    private const val KEY_FOLDER_NAMES = "custom_folder_names"

    private var prefs: SharedPreferences? = null

    private val audioTitlesMap = mutableMapOf<String, String>()
    private val folderNamesMap = mutableMapOf<String, String>()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val prefs = prefs ?: return
        audioTitlesMap.clear()
        folderNamesMap.clear()

        val audioJsonStr = prefs.getString(KEY_AUDIO_TITLES, null)
        if (!audioJsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(audioJsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    audioTitlesMap[key] = json.getString(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val folderJsonStr = prefs.getString(KEY_FOLDER_NAMES, null)
        if (!folderJsonStr.isNullOrBlank()) {
            try {
                val json = JSONObject(folderJsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    folderNamesMap[key] = json.getString(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun save() {
        val prefs = prefs ?: return
        try {
            val aObj = JSONObject()
            audioTitlesMap.forEach { (k, v) -> aObj.put(k, v) }
            val fObj = JSONObject()
            folderNamesMap.forEach { (k, v) -> fObj.put(k, v) }

            prefs.edit()
                .putString(KEY_AUDIO_TITLES, aObj.toString())
                .putString(KEY_FOLDER_NAMES, fObj.toString())
                .apply()
            _version.value++
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCustomAudioTitle(oldPath: String, newPath: String, newTitle: String) {
        audioTitlesMap.remove(oldPath)
        audioTitlesMap[newPath] = newTitle
        save()
    }

    fun getCustomAudioTitle(path: String): String? {
        return audioTitlesMap[path]
    }

    fun setCustomFolderName(oldPath: String, newPath: String, newName: String) {
        folderNamesMap.remove(oldPath)
        folderNamesMap[newPath] = newName
        save()
    }

    fun getCustomFolderName(path: String): String? {
        return folderNamesMap[path]
    }
}
