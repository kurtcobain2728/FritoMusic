package com.frito.music.utils

/**
 * Redimensiona y optimiza URLs de imágenes de YouTube Music y Google CDN
 * al tamaño exacto solicitado para evitar descargar y decodificar bitmaps gigantes en listas y grillas.
 */
fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    val targetSize = width ?: height ?: 500

    if (this.contains("i.ytimg.com")) {
        // maxresdefault solo si se pide tamaño grande (reproductor a pantalla completa).
        // En listas se usa hqdefault o mqdefault para carga rápida y liviana.
        val targetQuality = if (targetSize >= 1000) {
            "maxresdefault.jpg"
        } else if (targetSize <= 120) {
            "mqdefault.jpg"
        } else {
            "hqdefault.jpg"
        }
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            targetQuality
        )
    }

    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val size = if (targetSize >= 1000) 1080 else if (targetSize <= 240) targetSize else 500
        return "$baseUrl=w$size-h$size-l90-rj"
    }

    if (this.contains("yt3.ggpht.com")) {
        val baseUrl = this.split("=")[0].split("-s")[0]
        val size = if (targetSize >= 1000) 1080 else targetSize
        return "$baseUrl=s$size"
    }

    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val baseUrl = this.split("=")[0]
        val size = if (targetSize >= 1000) 1080 else if (targetSize <= 240) targetSize else 500
        return "$baseUrl=w$size-h$size-l90-rj"
    }

    return this
}

@JvmName("resizeNullable")
fun String?.resize(
    width: Int? = null,
    height: Int? = null,
): String? {
    if (this == null) return null
    return this.resize(width, height)
}

object ImageUtils {
    /**
     * Convierte URLs de miniaturas a alta resolución (1080p).
     * Usar EXCLUSIVAMENTE en la carátula principal del reproductor, NO en listas o celdas.
     */
    fun highRes(url: String?, size: Int = 1080): String? {
        if (url.isNullOrEmpty()) return url
        return url.resize(size, size)
    }
}
