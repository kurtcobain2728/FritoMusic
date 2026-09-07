package com.frito.music.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LyricsTranslator {

    private val cache = mutableMapOf<String, List<String>>()

    /**
     * Traduce una lista de líneas de letra al idioma objetivo (por defecto español 'es').
     * Utiliza la API JSON directa de Google Translate GTX vía POST con parsing de JSONArray,
     * garantizando texto puro y libre de basura HTML, scripts, estilos o etiquetas.
     */
    suspend fun translate(lines: List<String>, targetLang: String = "es"): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()

        val cacheKey = "$targetLang:${lines.joinToString("\n")}"
        cache[cacheKey]?.let { return@withContext it }

        // Método 1: Traducción directa por lotes usando el endpoint JSON oficial de GTX
        val directResult = runCatching { translateViaGtx(lines, targetLang) }.getOrNull()
        if (!directResult.isNullOrEmpty() && directResult.size == lines.size) {
            val cleaned = directResult.map { cleanLine(it) }
            cache[cacheKey] = cleaned
            return@withContext cleaned
        }

        // Método 2: Chunking por bloques pequeños de 20 líneas en caso de canciones muy largas
        val chunkedResult = runCatching { translateViaGtxChunked(lines, targetLang) }.getOrNull()
        if (!chunkedResult.isNullOrEmpty() && chunkedResult.size == lines.size) {
            val cleaned = chunkedResult.map { cleanLine(it) }
            cache[cacheKey] = cleaned
            return@withContext cleaned
        }

        lines
    }

    private fun translateViaGtx(lines: List<String>, targetLang: String): List<String>? {
        val textToTranslate = lines.joinToString("\n")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        val postData = "q=" + URLEncoder.encode(textToTranslate, "UTF-8")
        conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val sentencesArray = jsonArray.optJSONArray(0) ?: return null

            val translatedBuilder = StringBuilder()
            for (i in 0 until sentencesArray.length()) {
                val sentence = sentencesArray.optJSONArray(i) ?: continue
                val part = sentence.optString(0, "")
                translatedBuilder.append(part)
            }

            val fullText = translatedBuilder.toString()
            val splitLines = fullText.split("\n")
            return lines.indices.map { i ->
                cleanLine(splitLines.getOrNull(i)?.trim().orEmpty())
            }
        }
        return null
    }

    private fun translateViaGtxChunked(lines: List<String>, targetLang: String): List<String> {
        val chunkSize = 20
        val chunks = lines.chunked(chunkSize)
        val result = mutableListOf<String>()

        for (chunk in chunks) {
            val translatedChunk = translateViaGtx(chunk, targetLang)
            if (translatedChunk != null && translatedChunk.size == chunk.size) {
                result.addAll(translatedChunk)
            } else {
                result.addAll(chunk)
            }
        }

        return result
    }

    /**
     * Limpia cualquier residuo de etiquetas HTML, scripts, estilos o entidades.
     */
    private fun cleanLine(text: String): String {
        return text
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }
}
