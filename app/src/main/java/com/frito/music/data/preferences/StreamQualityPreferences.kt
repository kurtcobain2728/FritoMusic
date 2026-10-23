package com.frito.music.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class StreamQuality(
    val key: String,
    val title: String,
    val subtitle: String,
    val kbpsLabel: String,
    val targetBitrate: Int
) {
    HIGH(
        key = "high",
        title = "Alta",
        subtitle = "~256 kbps • Máxima fidelidad de audio (Recomendado)",
        kbpsLabel = "256 kbps",
        targetBitrate = 256_000
    ),
    NORMAL(
        key = "normal",
        title = "Normal",
        subtitle = "~128 kbps • Balance óptimo de sonido y datos",
        kbpsLabel = "128 kbps",
        targetBitrate = 128_000
    ),
    LOW(
        key = "low",
        title = "Baja",
        subtitle = "~64 kbps • Ahorro máximo de datos móviles (Opus / AAC)",
        kbpsLabel = "64 kbps",
        targetBitrate = 64_000
    );

    companion object {
        fun fromKey(key: String?): StreamQuality {
            return entries.firstOrNull { it.key == key } ?: HIGH
        }
    }
}

object StreamQualityPreferences {
    private const val PREFS_NAME = "frito_stream_quality_prefs"
    private const val KEY_STREAM_QUALITY = "stream_quality"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getQuality(context: Context): StreamQuality {
        val key = getPrefs(context).getString(KEY_STREAM_QUALITY, StreamQuality.HIGH.key)
        return StreamQuality.fromKey(key)
    }

    fun setQuality(context: Context, quality: StreamQuality) {
        getPrefs(context).edit().putString(KEY_STREAM_QUALITY, quality.key).apply()
    }
}
