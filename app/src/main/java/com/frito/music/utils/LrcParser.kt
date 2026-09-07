package com.frito.music.utils

import com.frito.music.data.models.LyricLine

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")

    /**
     * Parsea una cadena de texto en formato LRC estándar a una lista ordenada de LyricLine.
     */
    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val result = mutableListOf<LyricLine>()
        val lines = lrcContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Ignorar encabezados de metadatos como [ar:artista], [ti:titulo], [length:03:45], etc.
            val allMatches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (allMatches.isEmpty()) continue

            // El texto de la letra es la porción restante tras remover todas las etiquetas de tiempo
            val text = trimmed.replace(TIME_TAG_REGEX, "").trim()

            for (match in allMatches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fractionStr = match.groupValues.getOrNull(3).orEmpty()

                val millis = when (fractionStr.length) {
                    1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionStr.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val timestampMs = minutes * 60_000L + seconds * 1_000L + millis
                result.add(LyricLine(timestampMs = timestampMs, text = text))
            }
        }

        return result.sortedBy { it.timestampMs }
    }
}
