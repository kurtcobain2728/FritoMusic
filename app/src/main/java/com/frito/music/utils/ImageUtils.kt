package com.frito.music.utils

object ImageUtils {
    /**
     * Convierte URLs de miniaturas pequeñas (Google / YouTube Music) a alta resolución (1080p).
     */
    fun highRes(url: String?, size: Int = 1080): String? {
        if (url.isNullOrEmpty()) return url
        // YouTube Music / Google: reescribir el sufijo de tamaño =wNNN-hNNN...
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            return if (url.contains(Regex("=w\\d+-h\\d+"))) {
                url.replace(Regex("=w\\d+-h\\d+.*$"), "=w$size-h$size-l90-rj")
            } else url
        }
        // Miniaturas de video de YouTube: pedir maxresdefault
        if (url.contains("i.ytimg.com")) {
            return url.replace(Regex("/(hq|mq|sd)?default\\."), "/maxresdefault.")
        }
        return url
    }
}
