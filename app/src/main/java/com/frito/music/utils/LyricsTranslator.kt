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
     * Realiza la petición usando POST a la API pública de Google Translate GTX,
     * preservando saltos de línea y formateo.
     */
    suspend fun translate(lines: List<String>, targetLang: String = "es"): List<String> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext emptyList()

        val cacheKey = "$targetLang:${lines.joinToString("\n")}"
        cache[cacheKey]?.let { return@withContext it }

        runCatching {
            val delimiter = "\n"
            val textToTranslate = lines.joinToString(delimiter)

            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            val postData = "q=" + URLEncoder.encode(textToTranslate, "UTF-8")
            conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val sentencesArray = jsonArray.optJSONArray(0) ?: return@runCatching lines

                val translatedBuilder = StringBuilder()
                for (i in 0 until sentencesArray.length()) {
                    val sentence = sentencesArray.optJSONArray(i) ?: continue
                    val part = sentence.optString(0, "")
                    translatedBuilder.append(part)
                }

                val fullTranslatedText = translatedBuilder.toString()
                val resultLines = fullTranslatedText.split("\n")

                // Asegurar que coincida exactamente con la cantidad de líneas originales
                val finalLines = lines.indices.map { i ->
                    resultLines.getOrNull(i)?.trim().orEmpty()
                }

                cache[cacheKey] = finalLines
                finalLines
            } else {
                lines
            }
        }.getOrDefault(lines)
    }
}
