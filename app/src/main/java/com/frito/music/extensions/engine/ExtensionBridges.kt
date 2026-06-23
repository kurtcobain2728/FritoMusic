package com.frito.music.extensions.engine

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

class StorageBridge {
    private val store = mutableMapOf<String, String>()

    fun get(key: String): String? {
        return store[key]
    }

    fun set(key: String, value: String?) {
        if (value == null) {
            store.remove(key)
        } else {
            store[key] = value
        }
    }
}

class FileBridge {
    var interceptedUrl: String? = null

    fun exists(path: String): Boolean {
        return try { java.io.File(path).exists() } catch (e: Exception) { false }
    }

    fun delete(path: String): Boolean {
        return try { java.io.File(path).delete() } catch (e: Exception) { false }
    }

    fun getSize(path: String): Any? {
        return try {
            val size = java.io.File(path).length()
            val result = org.mozilla.javascript.NativeObject()
            result.put("success", result, true)
            result.put("size", result, size)
            result
        } catch (e: Exception) { null }
    }

    fun readBytes(path: String, options: Any?): Any? {
        return null
    }

    fun writeBytes(path: String, data: String, options: Any?): Any? {
        return null
    }

    fun download(url: String, path: String, options: Any?): Any? {
        if (interceptedUrl == null) {
            interceptedUrl = url
        }
        val result = org.mozilla.javascript.NativeObject()
        result.put("success", result, true)
        return result
    }
}
