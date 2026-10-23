package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.frito.music.data.network.yt.YouTubeRepository
import com.music.innertube.models.ArtistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestor local y de sincronización para artistas/canales a los que el usuario se ha suscrito.
 * Persiste localmente en SharedPreferences (funciona 100% sin necesidad de cuenta de Google)
 * y sincroniza en segundo plano con YouTube Music si el usuario ha iniciado sesión.
 */
object SubscriptionsManager {
    private const val PREFS_NAME = "frito_subscriptions"
    private const val KEY_SUBSCRIPTIONS = "saved_subscriptions"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _subscribedArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val subscribedArtists: StateFlow<List<ArtistItem>> = _subscribedArtists.asStateFlow()

    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannelIds: StateFlow<Set<String>> = _subscribedChannelIds.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSubscriptions()
        if (YouTubeLoginManager.isLoggedIn.value) {
            scope.launch {
                syncWithYouTube()
            }
        }
    }

    private fun loadSubscriptions() {
        val jsonStr = prefs?.getString(KEY_SUBSCRIPTIONS, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ArtistItem>()
            val ids = mutableSetOf<String>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val thumbnail = if (obj.has("thumbnail")) obj.getString("thumbnail").takeIf { it.isNotBlank() } else null
                val channelId = if (obj.has("channelId")) obj.getString("channelId").takeIf { it.isNotBlank() } else null

                if (id.isNotBlank() || !channelId.isNullOrBlank()) {
                    val finalId = if (id.isNotBlank()) id else channelId!!
                    val artist = ArtistItem(
                        id = finalId,
                        title = title,
                        thumbnail = thumbnail,
                        channelId = channelId ?: finalId,
                        shuffleEndpoint = null,
                        radioEndpoint = null
                    )
                    list.add(artist)
                    ids.add(finalId)
                    if (!channelId.isNullOrBlank()) {
                        ids.add(channelId)
                    }
                }
            }
            _subscribedArtists.value = list
            _subscribedChannelIds.value = ids
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSubscriptions() {
        try {
            val jsonArray = JSONArray()
            _subscribedArtists.value.forEach { artist ->
                val obj = JSONObject()
                obj.put("id", artist.id)
                obj.put("title", artist.title)
                artist.thumbnail?.let { obj.put("thumbnail", it) }
                artist.channelId?.let { obj.put("channelId", it) }
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_SUBSCRIPTIONS, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateIdsSet(list: List<ArtistItem>) {
        val ids = mutableSetOf<String>()
        list.forEach { artist ->
            if (artist.id.isNotBlank()) ids.add(artist.id)
            if (!artist.channelId.isNullOrBlank()) ids.add(artist.channelId!!)
        }
        _subscribedChannelIds.value = ids
    }

    fun isSubscribed(artistId: String?, channelId: String? = null): Boolean {
        val ids = _subscribedChannelIds.value
        if (!artistId.isNullOrBlank() && ids.contains(artistId)) return true
        if (!channelId.isNullOrBlank() && ids.contains(channelId)) return true
        return false
    }

    fun toggleSubscription(artist: ArtistItem): Boolean {
        if (artist.id.isBlank() && artist.channelId.isNullOrBlank()) return false

        val current = _subscribedArtists.value.toMutableList()
        val targetId = artist.id
        val targetChannelId = artist.channelId

        val existingIndex = current.indexOfFirst { local ->
            (targetId.isNotBlank() && (local.id == targetId || local.channelId == targetId)) ||
            (!targetChannelId.isNullOrBlank() && (local.id == targetChannelId || local.channelId == targetChannelId))
        }

        val isNowSubscribed = if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            false
        } else {
            val toAdd = if (artist.channelId.isNullOrBlank()) {
                artist.copy(channelId = artist.id)
            } else {
                artist
            }
            current.add(0, toAdd)
            true
        }

        _subscribedArtists.value = current
        updateIdsSet(current)
        saveSubscriptions()

        // Sincronización remota en segundo plano si está logueado a YouTube (no bloquea ni revierte lo local)
        if (YouTubeLoginManager.isLoggedIn.value) {
            scope.launch {
                try {
                    val rawTarget = artist.channelId?.takeIf { it.isNotBlank() } ?: artist.id
                    val validChannelId = if (rawTarget.startsWith("UC")) {
                        rawTarget
                    } else {
                        val resolved = runCatching {
                            com.music.innertube.YouTube.artist(artist.id).getOrNull()?.artist?.channelId
                        }.getOrNull()?.takeIf { it.startsWith("UC") }
                        resolved ?: rawTarget
                    }

                    if (validChannelId.isNotBlank()) {
                        if (isNowSubscribed) {
                            YouTubeRepository.subscribeChannel(validChannelId)
                            android.util.Log.d("SubscriptionsManager", "Suscrito exitosamente a canal $validChannelId")
                        } else {
                            YouTubeRepository.unsubscribeChannel(validChannelId)
                            android.util.Log.d("SubscriptionsManager", "Desuscrito exitosamente de canal $validChannelId")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SubscriptionsManager", "Aviso en sync de suscripción: ${e.message}")
                }
            }
        }

        return isNowSubscribed
    }

    fun removeSubscription(artistId: String) {
        if (artistId.isBlank()) return
        val current = _subscribedArtists.value.toMutableList()
        val removed = current.removeAll { local ->
            local.id == artistId || local.channelId == artistId
        }
        if (removed) {
            _subscribedArtists.value = current
            updateIdsSet(current)
            saveSubscriptions()

            if (YouTubeLoginManager.isLoggedIn.value) {
                scope.launch {
                    try {
                        val validChannelId = if (artistId.startsWith("UC")) {
                            artistId
                        } else {
                            runCatching {
                                com.music.innertube.YouTube.artist(artistId).getOrNull()?.artist?.channelId
                            }.getOrNull()?.takeIf { it.startsWith("UC") } ?: artistId
                        }
                        YouTubeRepository.unsubscribeChannel(validChannelId)
                    } catch (e: Exception) {
                        android.util.Log.w("SubscriptionsManager", "Error desuscribiendo artista $artistId: ${e.message}")
                    }
                }
            }
        }
    }

    suspend fun syncWithYouTube() {
        if (!YouTubeLoginManager.isLoggedIn.value) return
        try {
            val result = YouTubeRepository.getSubscribedArtists()
            result.onSuccess { remoteArtists ->
                android.util.Log.d("SubscriptionsManager", "Sincronizados ${remoteArtists.size} artistas remotos desde YouTube")
                if (remoteArtists.isNotEmpty()) {
                    val current = _subscribedArtists.value.toMutableList()
                    var changed = false
                    remoteArtists.forEach { remote ->
                        val exists = current.any { local ->
                            local.id == remote.id ||
                            (!local.channelId.isNullOrBlank() && local.channelId == remote.channelId) ||
                            (!local.channelId.isNullOrBlank() && local.channelId == remote.id) ||
                            (!remote.channelId.isNullOrBlank() && local.id == remote.channelId)
                        }
                        if (!exists) {
                            current.add(remote)
                            changed = true
                        }
                    }
                    if (changed || current.size != _subscribedArtists.value.size) {
                        _subscribedArtists.value = current
                        updateIdsSet(current)
                        saveSubscriptions()
                    }
                }
            }.onFailure {
                android.util.Log.w("SubscriptionsManager", "Error al sincronizar artistas remotos: ${it.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionsManager", "Excepción al sincronizar artistas: ${e.message}")
        }
    }
}
