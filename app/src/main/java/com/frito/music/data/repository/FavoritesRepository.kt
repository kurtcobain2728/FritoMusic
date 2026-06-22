package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frito_favorites", Context.MODE_PRIVATE)
    
    // Maintain a stateflow of favorite paths
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    init {
        _favorites.value = prefs.getStringSet("favorite_paths", emptySet())?.toSet() ?: emptySet()
    }

    fun toggleFavorite(path: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _favorites.value = current
        prefs.edit().putStringSet("favorite_paths", current).apply()
    }

    fun isFavorite(path: String): Boolean {
        return _favorites.value.contains(path)
    }
}
