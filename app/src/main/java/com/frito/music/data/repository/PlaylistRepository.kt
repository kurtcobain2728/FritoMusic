package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.frito.music.data.models.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frito_playlists", Context.MODE_PRIVATE)
    
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val jsonString = prefs.getString("playlists_data", "[]") ?: "[]"
        val list = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val pathsArray = obj.getJSONArray("audioPaths")
                val paths = mutableListOf<String>()
                for (j in 0 until pathsArray.length()) {
                    paths.add(pathsArray.getString(j))
                }
                list.add(Playlist(id, name, paths))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _playlists.value = list
    }

    private fun savePlaylists(list: List<Playlist>) {
        val jsonArray = JSONArray()
        for (playlist in list) {
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val pathsArray = JSONArray()
            for (path in playlist.audioPaths) {
                pathsArray.put(path)
            }
            obj.put("audioPaths", pathsArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString("playlists_data", jsonArray.toString()).apply()
        _playlists.value = list
    }

    fun createPlaylist(name: String): Playlist {
        val current = _playlists.value.toMutableList()
        val newPlaylist = Playlist(UUID.randomUUID().toString(), name, emptyList())
        current.add(newPlaylist)
        savePlaylists(current)
        return newPlaylist
    }

    fun addToPlaylist(playlistId: String, audioPath: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = current[index]
            if (!playlist.audioPaths.contains(audioPath)) {
                val newPaths = playlist.audioPaths.toMutableList()
                newPaths.add(audioPath)
                current[index] = playlist.copy(audioPaths = newPaths)
                savePlaylists(current)
            }
        }
    }
}
