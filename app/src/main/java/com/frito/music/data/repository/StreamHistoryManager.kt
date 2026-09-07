package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor local para almacenar el historial de reproducción de Stream (para "Vuelve a escuchar")
 * de manera instantánea y persistente, garantizando actualización inmediata al reproducir.
 */
object StreamHistoryManager {
    private const val PREFS_NAME = "frito_stream_history"
    private const val KEY_HISTORY = "recent_stream_songs"
    private const val MAX_HISTORY_ITEMS = 30

    private var prefs: SharedPreferences? = null

    private val _recentSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val recentSongs: StateFlow<List<SongItem>> = _recentSongs.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val jsonStr = prefs?.getString(KEY_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<SongItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val thumbnail = obj.optString("thumbnail", "")
                val duration = obj.optInt("duration", 0)
                val artistName = obj.optString("artist", "")
                val artistId = obj.optString("artistId", "").takeIf { it.isNotBlank() }
                val albumName = obj.optString("album", "").takeIf { it.isNotBlank() }
                val albumId = obj.optString("albumId", "").takeIf { it.isNotBlank() }

                if (id.isNotBlank() && title.isNotBlank()) {
                    list.add(
                        SongItem(
                            id = id,
                            title = title,
                            artists = listOf(Artist(name = artistName, id = artistId)),
                            album = albumName?.let { Album(name = it, id = albumId ?: "") },
                            duration = duration,
                            thumbnail = thumbnail,
                            endpoint = null
                        )
                    )
                }
            }
            _recentSongs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory() {
        try {
            val jsonArray = JSONArray()
            _recentSongs.value.take(MAX_HISTORY_ITEMS).forEach { song ->
                val obj = JSONObject()
                obj.put("id", song.id)
                obj.put("title", song.title)
                obj.put("thumbnail", song.thumbnail)
                obj.put("duration", song.duration ?: 0)
                val firstArtist = song.artists.firstOrNull()
                obj.put("artist", firstArtist?.name.orEmpty())
                obj.put("artistId", firstArtist?.id.orEmpty())
                obj.put("album", song.album?.name.orEmpty())
                obj.put("albumId", song.album?.id.orEmpty())
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_HISTORY, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun recordSong(song: SongItem) {
        if (song.id.isBlank()) return
        val current = _recentSongs.value.toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        if (current.size > MAX_HISTORY_ITEMS) {
            _recentSongs.value = current.take(MAX_HISTORY_ITEMS)
        } else {
            _recentSongs.value = current
        }
        saveHistory()
    }
}
