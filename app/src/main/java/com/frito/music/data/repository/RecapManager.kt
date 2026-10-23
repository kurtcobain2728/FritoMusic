package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class RecapPeriod {
    WEEKLY,
    MONTHLY,
    ALL_TIME
}

data class PlayRecord(
    val title: String,
    val artist: String,
    val album: String = "",
    val albumUri: String? = null,
    val durationMs: Long = 180000L,
    val timestamp: Long = System.currentTimeMillis()
)

data class TopSongStat(
    val title: String,
    val artist: String,
    val albumUri: String?,
    val playCount: Int,
    val minutesPlayed: Long
)

data class TopArtistStat(
    val name: String,
    val playCount: Int,
    val minutesPlayed: Long,
    val percentage: Int
)

data class RecapSummary(
    val period: RecapPeriod,
    val totalMinutes: Long,
    val totalPlays: Int,
    val uniqueTracksCount: Int,
    val topSongs: List<TopSongStat>,
    val topArtists: List<TopArtistStat>,
    val topArtistName: String
)

object RecapManager {
    private const val PREFS_NAME = "frito_music_recap"
    private const val KEY_PLAY_HISTORY = "playback_events"
    private const val MAX_SAVED_EVENTS = 200

    private var prefs: SharedPreferences? = null

    private val _events = MutableStateFlow<List<PlayRecord>>(emptyList())
    val events: StateFlow<List<PlayRecord>> = _events.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadEvents()
    }

    private fun loadEvents() {
        val jsonStr = prefs?.getString(KEY_PLAY_HISTORY, null)
        if (jsonStr.isNullOrBlank()) {
            return
        }
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<PlayRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlayRecord(
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album", ""),
                        albumUri = obj.optString("albumUri", "").takeIf { it.isNotBlank() },
                        durationMs = obj.optLong("durationMs", 180000L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _events.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveEvents() {
        try {
            val arr = JSONArray()
            _events.value.take(MAX_SAVED_EVENTS).forEach { rec ->
                val obj = JSONObject()
                obj.put("title", rec.title)
                obj.put("artist", rec.artist)
                obj.put("album", rec.album)
                obj.put("albumUri", rec.albumUri.orEmpty())
                obj.put("durationMs", rec.durationMs)
                obj.put("timestamp", rec.timestamp)
                arr.put(obj)
            }
            prefs?.edit()?.putString(KEY_PLAY_HISTORY, arr.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun recordPlayback(
        title: String,
        artist: String,
        album: String = "",
        albumUri: String? = null,
        durationMs: Long = 180000L
    ) {
        if (title.isBlank()) return
        val record = PlayRecord(
            title = title.trim(),
            artist = artist.trim().ifBlank { "Desconocido" },
            album = album.trim(),
            albumUri = albumUri,
            durationMs = if (durationMs > 0) durationMs else 180000L,
            timestamp = System.currentTimeMillis()
        )
        val current = _events.value.toMutableList()
        current.add(0, record)
        if (current.size > MAX_SAVED_EVENTS) {
            _events.value = current.take(MAX_SAVED_EVENTS)
        } else {
            _events.value = current
        }
        saveEvents()
    }

    fun getSummary(period: RecapPeriod): RecapSummary {
        val now = System.currentTimeMillis()
        val cutoff = when (period) {
            RecapPeriod.WEEKLY -> now - (7L * 24 * 60 * 60 * 1000)
            RecapPeriod.MONTHLY -> now - (30L * 24 * 60 * 60 * 1000)
            RecapPeriod.ALL_TIME -> 0L
        }

        var relevant = _events.value.filter { it.timestamp >= cutoff }

        // Si la lista guardada aún es pequeña, sembrar con las canciones de StreamHistoryManager
        if (relevant.isEmpty()) {
            val fallbackSongs = StreamHistoryManager.recentSongs.value
            if (fallbackSongs.isNotEmpty()) {
                val seeded = fallbackSongs.mapIndexed { idx, song ->
                    PlayRecord(
                        title = song.title,
                        artist = song.artists.firstOrNull()?.name ?: "Desconocido",
                        album = song.album?.name.orEmpty(),
                        albumUri = song.thumbnail,
                        durationMs = (song.duration ?: 180).toLong() * 1000L,
                        timestamp = now - (idx * 3600000L)
                    )
                }
                relevant = seeded
            }
        }

        val totalMinutes = (relevant.sumOf { it.durationMs } / 60000L).coerceAtLeast(if (relevant.isNotEmpty()) 12L else 0L)
        val totalPlays = relevant.size

        // Top Canciones
        val songGroups = relevant.groupBy { "${it.title}::${it.artist}" }
        val topSongs = songGroups.map { (_, records) ->
            val first = records.first()
            TopSongStat(
                title = first.title,
                artist = first.artist,
                albumUri = first.albumUri,
                playCount = records.size,
                minutesPlayed = records.sumOf { it.durationMs } / 60000L
            )
        }.sortedByDescending { it.playCount }.take(5)

        // Top Artistas
        val artistGroups = relevant.groupBy { it.artist.ifBlank { "Desconocido" } }
        val topArtists = artistGroups.map { (artistName, records) ->
            val count = records.size
            val pct = if (totalPlays > 0) ((count.toFloat() / totalPlays) * 100).toInt() else 0
            TopArtistStat(
                name = artistName,
                playCount = count,
                minutesPlayed = records.sumOf { it.durationMs } / 60000L,
                percentage = pct
            )
        }.sortedByDescending { it.playCount }.take(5)

        val topArtistName = topArtists.firstOrNull()?.name ?: "Ninguno aún"

        return RecapSummary(
            period = period,
            totalMinutes = totalMinutes,
            totalPlays = totalPlays,
            uniqueTracksCount = songGroups.size,
            topSongs = topSongs,
            topArtists = topArtists,
            topArtistName = topArtistName
        )
    }
}
