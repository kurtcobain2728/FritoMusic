package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.models.ArtistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor local para almacenar y consultar artistas favoritos del usuario.
 * Utiliza SharedPreferences y JSON nativo (org.json) para persistencia rápida y ligera.
 */
object FavoriteArtistsManager {
    private const val PREFS_NAME = "frito_favorite_artists"
    private const val KEY_ARTISTS = "saved_favorite_artists"

    private var prefs: SharedPreferences? = null

    private val _favoriteArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val favoriteArtists: StateFlow<List<ArtistItem>> = _favoriteArtists.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFavorites()
    }

    private fun loadFavorites() {
        val jsonStr = prefs?.getString(KEY_ARTISTS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ArtistItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val thumbnail = if (obj.has("thumbnail")) obj.getString("thumbnail") else null
                if (id.isNotBlank()) {
                    list.add(
                        ArtistItem(
                            id = id,
                            title = title,
                            thumbnail = thumbnail,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        )
                    )
                }
            }
            _favoriteArtists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveFavorites() {
        try {
            val jsonArray = JSONArray()
            _favoriteArtists.value.forEach { artist ->
                val obj = JSONObject()
                obj.put("id", artist.id)
                obj.put("title", artist.title)
                artist.thumbnail?.let { obj.put("thumbnail", it) }
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_ARTISTS, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isFavorite(artistId: String): Boolean {
        if (artistId.isBlank()) return false
        return _favoriteArtists.value.any { it.id == artistId }
    }

    fun toggleFavorite(artist: ArtistItem) {
        if (artist.id.isBlank()) return
        val current = _favoriteArtists.value.toMutableList()
        val index = current.indexOfFirst { it.id == artist.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, artist)
        }
        _favoriteArtists.value = current
        saveFavorites()
    }

    fun removeFavorite(artistId: String) {
        if (artistId.isBlank()) return
        val current = _favoriteArtists.value.toMutableList()
        if (current.removeAll { it.id == artistId }) {
            _favoriteArtists.value = current
            saveFavorites()
        }
    }
}
