package com.frito.music.utils

import com.frito.music.data.models.LyricLine

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val SYLLABLE_TAG_REGEX = Regex("""<(\d{1,2}:\d{2}(?:\.\d{1,3})?|\d+)>""")
    private val KARAOKE_TAG_REGEX = Regex("""\{[^}]*\}""")
    private val METADATA_TAG_REGEX = Regex("""^\[(ti|ar|al|by|offset|re|ve|length):.*\]""", RegexOption.IGNORE_CASE)

    /**
     * Parsea una cadena de texto en formato LRC estándar o extendido a una lista ordenada de LyricLine.
     * Limpia etiquetas de tiempo por palabra/sílaba, efectos karaoke y metadatos no musicales.
     */
    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val result = mutableListOf<LyricLine>()
        val lines = lrcContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Ignorar líneas que son exclusivamente metadatos del archivo LRC
            if (METADATA_TAG_REGEX.matches(trimmed)) continue

            val allMatches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (allMatches.isEmpty()) continue

            // Remover etiquetas de tiempo de línea, marcas por sílaba (<00:12.34>) y tags karaoke ({...})
            val text = trimmed
                .replace(TIME_TAG_REGEX, "")
                .replace(SYLLABLE_TAG_REGEX, "")
                .replace(KARAOKE_TAG_REGEX, "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .trim()

            // Si la línea quedó vacía tras retirar las marcas de tiempo, omitir
            if (text.isEmpty()) continue

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
