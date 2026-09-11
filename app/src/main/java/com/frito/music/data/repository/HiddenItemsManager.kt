package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HiddenItem(
    val path: String,
    val name: String,
    val isFolder: Boolean,
    val realPath: String = "",
    val dateHidden: Long = System.currentTimeMillis()
)

/**
 * Gestor local para ocultar y restaurar carpetas y canciones en la pantalla de Inicio.
 * Utiliza SharedPreferences y JSON nativo para persistencia liviana y confiable.
 */
object HiddenItemsManager {
    private const val PREFS_NAME = "frito_hidden_items"
    private const val KEY_ITEMS = "saved_hidden_items"

    private var prefs: SharedPreferences? = null

    private val _hiddenItems = MutableStateFlow<List<HiddenItem>>(emptyList())
    val hiddenItems: StateFlow<List<HiddenItem>> = _hiddenItems.asStateFlow()

    private val hiddenFolderPaths = HashSet<String>()
    private val hiddenAudioPaths = HashSet<String>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHiddenItems()
    }

    private fun loadHiddenItems() {
        val jsonStr = prefs?.getString(KEY_ITEMS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<HiddenItem>()
            hiddenFolderPaths.clear()
            hiddenAudioPaths.clear()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val path = obj.optString("path")
                val name = obj.optString("name")
                val isFolder = obj.optBoolean("isFolder", false)
                val realPath = obj.optString("realPath", "")
                val dateHidden = obj.optLong("dateHidden", System.currentTimeMillis())

                if (path.isNotBlank()) {
                    val item = HiddenItem(
                        path = path,
                        name = name,
                        isFolder = isFolder,
                        realPath = realPath,
                        dateHidden = dateHidden
                    )
                    list.add(item)
                    if (isFolder) {
                        hiddenFolderPaths.add(path)
                        if (realPath.isNotBlank()) hiddenFolderPaths.add(realPath)
                    } else {
                        hiddenAudioPaths.add(path)
                        if (realPath.isNotBlank()) hiddenAudioPaths.add(realPath)
                    }
                }
            }
            _hiddenItems.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHiddenItems() {
        try {
            val jsonArray = JSONArray()
            _hiddenItems.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("path", item.path)
                    put("name", item.name)
                    put("isFolder", item.isFolder)
                    put("realPath", item.realPath)
                    put("dateHidden", item.dateHidden)
                }
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_ITEMS, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isFolderHidden(path: String, realPath: String = ""): Boolean {
        if (path.isBlank() && realPath.isBlank()) return false
        if (hiddenFolderPaths.contains(path) || (realPath.isNotBlank() && hiddenFolderPaths.contains(realPath))) {
            return true
        }
        // Si una carpeta contenedora superior está oculta
        return hiddenFolderPaths.any { hiddenPath ->
            (path.isNotBlank() && (path == hiddenPath || path.startsWith("$hiddenPath/"))) ||
            (realPath.isNotBlank() && (realPath == hiddenPath || realPath.startsWith("$hiddenPath/")))
        }
    }

    fun isAudioHidden(path: String): Boolean {
        if (path.isBlank()) return false
        if (hiddenAudioPaths.contains(path)) return true
        // O si su carpeta contenedora está oculta
        return hiddenFolderPaths.any { folderPath ->
            path.startsWith("$folderPath/") || path.contains(folderPath)
        }
    }

    fun hideFolder(path: String, name: String, realPath: String = "") {
        if (path.isBlank() && realPath.isBlank()) return
        val current = _hiddenItems.value.toMutableList()
        val key = path.ifBlank { realPath }
        if (current.none { it.path == key }) {
            current.add(0, HiddenItem(path = key, name = name, isFolder = true, realPath = realPath))
            hiddenFolderPaths.add(key)
            if (realPath.isNotBlank()) hiddenFolderPaths.add(realPath)
            _hiddenItems.value = current
            saveHiddenItems()
        }
    }

    fun hideAudio(path: String, name: String) {
        if (path.isBlank()) return
        val current = _hiddenItems.value.toMutableList()
        if (current.none { it.path == path }) {
            current.add(0, HiddenItem(path = path, name = name, isFolder = false))
            hiddenAudioPaths.add(path)
            _hiddenItems.value = current
            saveHiddenItems()
        }
    }

    fun hideMultiple(
        folders: List<Triple<String, String, String>>, // (path, name, realPath)
        audios: List<Pair<String, String>>             // (path, name)
    ) {
        val current = _hiddenItems.value.toMutableList()
        var changed = false
        folders.forEach { (path, name, realPath) ->
            val key = path.ifBlank { realPath }
            if (key.isNotBlank() && current.none { it.path == key }) {
                current.add(0, HiddenItem(path = key, name = name, isFolder = true, realPath = realPath))
                hiddenFolderPaths.add(key)
                if (realPath.isNotBlank()) hiddenFolderPaths.add(realPath)
                changed = true
            }
        }
        audios.forEach { (path, name) ->
            if (path.isNotBlank() && current.none { it.path == path }) {
                current.add(0, HiddenItem(path = path, name = name, isFolder = false))
                hiddenAudioPaths.add(path)
                changed = true
            }
        }
        if (changed) {
            _hiddenItems.value = current
            saveHiddenItems()
        }
    }

    fun unhideItem(path: String) {
        val current = _hiddenItems.value.toMutableList()
        val item = current.find { it.path == path }
        val removed = current.removeAll { it.path == path }
        if (removed) {
            hiddenFolderPaths.remove(path)
            hiddenAudioPaths.remove(path)
            if (item != null && item.realPath.isNotBlank()) {
                hiddenFolderPaths.remove(item.realPath)
                hiddenAudioPaths.remove(item.realPath)
            }
            _hiddenItems.value = current
            saveHiddenItems()
        }
    }

    fun unhideAll() {
        _hiddenItems.value = emptyList()
        hiddenFolderPaths.clear()
        hiddenAudioPaths.clear()
        prefs?.edit()?.remove(KEY_ITEMS)?.apply()
    }
}
