package com.frito.music.extensions.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.mozilla.javascript.Scriptable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(
    @JvmField val statusCode: Int,
    @JvmField val body: String?,
    @JvmField val error: String?,
    @JvmField val headers: Map<String, String>? = null
)

class LocalTime(
    @JvmField val timezone: String,
    @JvmField val offsetMinutes: Int
)

class HttpBridge {
    fun get(urlString: String, headersObj: Any?): HttpResponse {
        return request("GET", urlString, null, headersObj)
    }

    fun post(urlString: String, body: String?, headersObj: Any?): HttpResponse {
        return request("POST", urlString, body, headersObj)
    }

    private fun request(method: String, urlString: String, body: String?, headersObj: Any?): HttpResponse {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (headersObj is Scriptable) {
                for (id in headersObj.ids) {
                    val key = id.toString()
                    val value = headersObj.get(key, headersObj)?.toString()
                    if (value != null) {
                        connection.setRequestProperty(key, value)
                    }
                }
            }

            if (body != null && method == "POST") {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val responseCode = connection.responseCode
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, value) ->
                if (key != null) {
                    responseHeaders[key] = value.joinToString("; ")
                }
            }

            val stream: InputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = stream.bufferedReader().use { it.readText() }
            return HttpResponse(responseCode, responseBody, null, responseHeaders)
        } catch (e: Exception) {
            e.printStackTrace()
            return HttpResponse(0, null, e.message, null)
        } finally {
            connection?.disconnect()
        }
    }
}

class LogBridge {
    fun info(vararg args: Any?) {
        Log.i("ExtensionEngine", args.joinToString(" "))
    }

    fun debug(vararg args: Any?) {
        Log.d("ExtensionEngine", args.joinToString(" "))
    }

    fun error(vararg args: Any?) {
        Log.e("ExtensionEngine", args.joinToString(" "))
    }
}

class GoBackendBridge {
    fun getLocalTime(): LocalTime {
        return LocalTime("America/New_York", 300)
    }
}

class UtilsBridge {
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:126.0) Gecko/20100101 Firefox/126.0"
    )

    fun randomUserAgent(): String {
        return userAgents[(Math.random() * userAgents.size).toInt()]
    }

    fun appUserAgent(): String {
        return "SpotiFLAC/1.2.5"
    }

    fun appVersion(): String {
        return "1.2.5"
    }

    fun md5(input: String): String {
        try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }

    fun sha256(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun isDownloadCancelled(): Boolean {
        return DownloadState.cancelRequested
    }

    /**
     * Escáner iterativo de etiquetas <S ...> de manifiestos MPD.
     *
     * El regex /<S\s+[^>]*d="(\d+)"(?:\s+r="(-?\d+)")?[^>]*\/?>/gi que usan las
     * extensiones sobre manifiestos enormes provoca backtracking catastórfico en
     * java.util.regex (que matchea RECURSIVAMENTE los cuantificadores) y revienta
     * con StackOverflowError. Este escáner hace el mismo trabajo con indexOf
     * puro: sin regex y sin recursión, coste lineal garantizado.
     *
     * @return JSON array de [{full, end, d, r}] donde "end" es la posición
     *         siguiente al tag (para simular RegExp.lastIndex).
     */
    fun mpdScan(text: String?): String {
        if (text.isNullOrEmpty()) return "[]"
        return try {
            val out = org.json.JSONArray()
            var i = 0
            val n = text.length
            while (i < n - 2) {
                val start = text.indexOf("<S", i)
                if (start < 0 || start + 2 > n) break
                val afterTag = text.getOrNull(start + 2)
                // Debe ser <S seguido de espacio/atributo (no <Segment... etc.)
                if (afterTag == null || !(afterTag.isWhitespace() || afterTag == '/')) {
                    i = start + 2
                    continue
                }
                val tagEnd = text.indexOf('>', start)
                if (tagEnd < 0) break
                val tag = text.substring(start, tagEnd + 1)

                // Extraer atributos d="N" y r="N" escaneando por índices
                var dVal = ""
                var rVal = ""
                var j = 2
                while (j < tag.length - 1) {
                    val eq = tag.indexOf("=\"", j)
                    if (eq < 0) break
                    val name = tag.substring(j, eq).trim()
                    val close = tag.indexOf('"', eq + 2)
                    if (close < 0) break
                    val value = tag.substring(eq + 2, close)
                    when {
                        name == "d" && dVal.isEmpty() -> dVal = value
                        name == "r" && rVal.isEmpty() -> rVal = value
                    }
                    j = close + 1
                }

                // El regex original exige d="\d+" (uno o más DÍGITOS): un tag sin d
                // o con valor no numérico NO matchearía. Omitirlo para semántica idéntica.
                val dNum = dVal.toLongOrNull()
                if (dNum == null || dNum < 0) {
                    i = tagEnd + 1
                    continue
                }

                val obj = org.json.JSONObject()
                    .put("full", tag)
                    .put("end", (tagEnd + 1).toLong())
                    .put("d", dNum)
                    .put("r", rVal.toLongOrNull() ?: 0L)
                out.put(obj)

                i = tagEnd + 1
            }
            out.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    fun hmacSHA1(key: ByteArray, data: ByteArray): ByteArray {
        try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA1")
            val secretKey = javax.crypto.spec.SecretKeySpec(key, "HmacSHA1")
            mac.init(secretKey)
            return mac.doFinal(data)
        } catch (e: Exception) {
            Log.e("UtilsBridge", "hmacSHA1 failed", e)
            return ByteArray(20)
        }
    }

    fun base64Decode(input: String): String {
        return try {
            String(android.util.Base64.decode(input, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun base64Encode(input: String): String {
        return try {
            android.util.Base64.encodeToString(input.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun normalizeString(input: String): String {
        return input.lowercase().trim()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun compareStrings(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        if (longer.isEmpty()) return 1.0
        val matches = longer.windowed(shorter.length).count { it == shorter }
        return matches.toDouble() / longer.length.toDouble()
    }
}

class MatchingBridge {
    fun normalizeString(input: String): String {
        return input.lowercase().trim()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun compareStrings(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        if (longer.isEmpty()) return 1.0
        var matches = 0
        for (i in shorter.indices) {
            if (i < longer.length && shorter[i] == longer[i]) matches++
        }
        return matches.toDouble() / longer.length.toDouble()
    }
}

class StorageBridge(private val context: Context, private val extensionId: String) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
    }

    operator fun get(key: String): String? {
        return prefs.getString(key, null)
    }

    operator fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** Estado de cancelación compartido entre el worker y las extensiones. */
object DownloadState {
    @Volatile var cancelRequested: Boolean = false
}

/** Bridge de progreso JS -> Kotlin (para reportes hacia el worker/UI). */
class ProgressBridge(private val listener: (Int) -> Unit) {
    fun report(value: Any?) {
        val raw = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: return
            else -> return
        }
        // Algunas extensiones reportan porcentaje (0-100) y otras ratio (0.0-1.0)
        val percent = if (raw in 0.0..1.0 && raw <= 1.0) (raw * 100).toInt() else raw.toInt()
        listener(percent.coerceIn(0, 100))
    }
}

class FileBridge {
    /**
     * Reporter de progreso POR BYTES en Kotlin puro (sin re-entrar a JS).
     * Antes se invocaba un callback JS por cada bloque de 64KB leídos:
     * esa re-entrada Java→JS dentro del bucle de streaming era el camino
     * directo al StackOverflowError del hilo de Rhino.
     */
    var byteProgressReporter: ((Int) -> Unit)? = null

    private fun jsResult(vararg pairs: Pair<String, Any?>): org.mozilla.javascript.NativeObject {
        val result = org.mozilla.javascript.NativeObject()
        for ((k, v) in pairs) result.put(k, result, v)
        return result
    }

    fun exists(path: String): Boolean {
        return try { java.io.File(path).exists() } catch (e: Exception) { false }
    }

    fun delete(path: String): Boolean {
        return try { java.io.File(path).delete() } catch (e: Exception) { false }
    }

    fun getSize(path: String): Any? {
        return try {
            val size = java.io.File(path).length()
            jsResult("success" to true, "size" to size)
        } catch (e: Exception) {
            jsResult("success" to false, "error" to (e.message ?: "getSize failed"))
        }
    }

    /**
     * Contrato SpotiFLAC: readBytes(path, {encoding:"base64"}) -> {success, data(base64)|error}
     */
    fun readBytes(path: String, options: Any?): Any? {
        return try {
            val bytes = java.io.File(path).readBytes()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            jsResult("success" to true, "data" to b64)
        } catch (e: Exception) {
            jsResult("success" to false, "error" to (e.message ?: "readBytes failed"))
        }
    }

    /**
     * Contrato SpotiFLAC: writeBytes(path, dataBase64, {encoding:"base64", truncate:bool, append:bool})
     * truncate=true -> sobrescribe; append=true -> añade al final.
     */
    fun writeBytes(path: String, data: String, options: Any?): Any? {
        return try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            var append = false
            if (options is Scriptable) {
                val appendVal = options.get("append", options)
                if (appendVal is Boolean) append = appendVal
                val truncateVal = options.get("truncate", options)
                if (truncateVal is Boolean && truncateVal) append = false
            }
            val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
            if (append) {
                java.io.FileOutputStream(file, true).use { it.write(bytes) }
            } else {
                file.writeBytes(bytes)
            }
            jsResult("success" to true)
        } catch (e: Exception) {
            jsResult("success" to false, "error" to (e.message ?: "writeBytes failed"))
        }
    }

    /**
     * Contrato SpotiFLAC: download(url, path, {headers:{}, onProgress:function(written,total)})
     * Descarga REAL del archivo a disco.
     */
    fun download(url: String, path: String, options: Any?): Any? {
        var connection: HttpURLConnection? = null
        return try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()

            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000

            if (options is Scriptable) {
                val headersObj = options.get("headers", options)
                if (headersObj is Scriptable) {
                    for (id in headersObj.ids) {
                        val key = id.toString()
                        val value = headersObj.get(key, headersObj)?.toString()
                        if (value != null) connection.setRequestProperty(key, value)
                    }
                }
                // NOTA: el callback JS onProgress de options se IGNORA a propósito.
                // El progreso real sale por byteProgressReporter (Kotlin puro).
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return jsResult("success" to false, "error" to "HTTP $code")
            }

            val total = connection.contentLengthLong
            val input = connection.inputStream
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            var read: Int
            var lastPct = -1

            file.outputStream().use { out ->
                while (input.read(buffer).also { read = it } != -1) {
                    if (DownloadState.cancelRequested) {
                        return jsResult("success" to false, "error" to "download cancelled")
                    }
                    out.write(buffer, 0, read)
                    written += read

                    // Progreso por bytes SIN re-entrar a JavaScript (0-100%).
                    // Solo notificamos cuando cambia el porcentaje entero.
                    if (total > 0) {
                        val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            byteProgressReporter?.let { rep -> runCatching { rep(pct) } }
                        }
                    }
                }
            }
            jsResult("success" to true, "path" to path, "size" to written)
        } catch (e: Exception) {
            runCatching { java.io.File(path).delete() }
            jsResult("success" to false, "error" to (e.message ?: "download failed"))
        } finally {
            connection?.disconnect()
        }
    }
}
