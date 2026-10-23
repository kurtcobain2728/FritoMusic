package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.WatchEndpoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor persistente de playlists migradas y creadas por el usuario.
 * Garantiza que las playlists aparezcan inmediatamente tanto en Stream ("3 rayas")
 * como en Biblioteca Online ("Más -> Playlists -> Online") sin depender de la
 * latencia de indexación de los servidores de YouTube Music.
 */
object MigratedPlaylistsManager {
    private const val PREFS_NAME = "frito_migrated_playlists_prefs"
    private const val KEY_PLAYLISTS = "saved_migrated_playlists"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAll(): List<PlaylistItem> {
        val sp = prefs ?: return emptyList()
        val jsonStr = sp.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        val list = mutableListOf<PlaylistItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val songCount = obj.optString("songCount", "")
                val thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                val authorName = obj.optString("author", "Tú")
                list.add(
                    PlaylistItem(
                        id = id,
                        title = title,
                        author = Artist(name = authorName, id = null),
                        songCountText = songCount,
                        thumbnail = thumbnail,
                        playEndpoint = WatchEndpoint(playlistId = id),
                        shuffleEndpoint = WatchEndpoint(playlistId = id),
                        radioEndpoint = null,
                        isEditable = true
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MigratedPlaylists", "Error cargando playlists migradas", e)
        }
        return list
    }

    fun save(item: PlaylistItem) {
        val sp = prefs ?: return
        val current = getAll().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item) // Más reciente al inicio

        val arr = JSONArray()
        for (pl in current) {
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("title", pl.title)
            obj.put("songCount", pl.songCountText ?: "")
            obj.put("thumbnail", pl.thumbnail ?: "")
            obj.put("author", pl.author?.name ?: "Tú")
            arr.put(obj)
        }
        sp.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun delete(playlistId: String) {
        val sp = prefs ?: return
        val current = getAll().toMutableList()
        if (current.removeAll { it.id == playlistId }) {
            val arr = JSONArray()
            for (pl in current) {
                val obj = JSONObject()
                obj.put("id", pl.id)
                obj.put("title", pl.title)
                obj.put("songCount", pl.songCountText ?: "")
                obj.put("thumbnail", pl.thumbnail ?: "")
                obj.put("author", pl.author?.name ?: "Tú")
                arr.put(obj)
            }
            sp.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
        }
    }
}
