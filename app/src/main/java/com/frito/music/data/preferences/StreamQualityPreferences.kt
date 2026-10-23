package com.frito.music.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class AudioCodecPreference(
    val key: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    AUTO(
        key = "auto",
        title = "Automático",
        subtitle = "El mejor disponible según la velocidad de tu conexión",
        badge = "AUTO"
    ),
    OPUS(
        key = "opus",
        title = "OPUS (WebM)",
        subtitle = "Códec moderno con gran fidelidad acústica a bitrates medios",
        badge = "OPUS"
    ),
    AAC(
        key = "aac",
        title = "AAC (M4A)",
        subtitle = "Estándar universal de Apple y Bluetooth (sin recodificar)",
        badge = "AAC"
    );

    companion object {
        fun fromKey(key: String?): AudioCodecPreference {
            return entries.firstOrNull { it.key == key } ?: AUTO
        }
    }
}

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
        subtitle = "~160 - 256 kbps • Máxima fidelidad de audio",
        kbpsLabel = "Alta (~160-256 kbps)",
        targetBitrate = 256_000
    ),
    NORMAL(
        key = "normal",
        title = "Normal",
        subtitle = "~128 kbps • Balance óptimo de sonido y datos",
        kbpsLabel = "Normal (~128 kbps)",
        targetBitrate = 128_000
    ),
    LOW(
        key = "low",
        title = "Baja",
        subtitle = "~48 - 70 kbps • Ahorro máximo de datos móviles",
        kbpsLabel = "Baja (~48-70 kbps)",
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
    private const val KEY_CODEC_PREFERENCE = "codec_preference"

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

    fun getCodecPreference(context: Context): AudioCodecPreference {
        val key = getPrefs(context).getString(KEY_CODEC_PREFERENCE, AudioCodecPreference.AUTO.key)
        return AudioCodecPreference.fromKey(key)
    }

    fun setCodecPreference(context: Context, codec: AudioCodecPreference) {
        getPrefs(context).edit().putString(KEY_CODEC_PREFERENCE, codec.key).apply()
    }

    fun getQualityDescription(codec: AudioCodecPreference, quality: StreamQuality): String {
        return when (codec) {
            AudioCodecPreference.OPUS -> when (quality) {
                StreamQuality.HIGH -> "~160 kbps • Itag 251 • Máxima fidelidad de audio Opus"
                StreamQuality.NORMAL -> "~128 kbps • Itag 250 • Balance perfecto de compresión"
                StreamQuality.LOW -> "~50-70 kbps • Itag 249 • Ahorro extremo de datos móviles"
            }
            AudioCodecPreference.AAC -> when (quality) {
                StreamQuality.HIGH -> "~128-256 kbps • Itag 140/141 • Audio puro directo para Bluetooth"
                StreamQuality.NORMAL -> "~128 kbps • Itag 140 • Estándar universal AAC"
                StreamQuality.LOW -> "~48 kbps • Itag 139 • Modo ultraligero de baja latencia"
            }
            AudioCodecPreference.AUTO -> when (quality) {
                StreamQuality.HIGH -> "Prioriza máxima fidelidad disponible (~160-256 kbps)"
                StreamQuality.NORMAL -> "Ajuste dinámico balanceado (~128 kbps)"
                StreamQuality.LOW -> "Prioriza ahorro de datos (~48-64 kbps)"
            }
        }
    }

    fun getQualityKbpsLabel(codec: AudioCodecPreference, quality: StreamQuality): String {
        return when (codec) {
            AudioCodecPreference.OPUS -> when (quality) {
                StreamQuality.HIGH -> "~160 kbps"
                StreamQuality.NORMAL -> "~128 kbps"
                StreamQuality.LOW -> "~50-70 kbps"
            }
            AudioCodecPreference.AAC -> when (quality) {
                StreamQuality.HIGH -> "~128-256 kbps"
                StreamQuality.NORMAL -> "~128 kbps"
                StreamQuality.LOW -> "~48 kbps"
            }
            AudioCodecPreference.AUTO -> when (quality) {
                StreamQuality.HIGH -> "Máxima (~160-256k)"
                StreamQuality.NORMAL -> "Balance (~128k)"
                StreamQuality.LOW -> "Ahorro (~48-64k)"
            }
        }
    }

    fun getSummaryText(context: Context): String {
        val codec = getCodecPreference(context)
        val quality = getQuality(context)
        val label = getQualityKbpsLabel(codec, quality)
        return when (codec) {
            AudioCodecPreference.AUTO -> "🟢 Automático • ${quality.title} ($label)"
            AudioCodecPreference.OPUS -> "🔵 OPUS • ${quality.title} ($label)"
            AudioCodecPreference.AAC -> "🟣 AAC • ${quality.title} ($label)"
        }
    }
}

