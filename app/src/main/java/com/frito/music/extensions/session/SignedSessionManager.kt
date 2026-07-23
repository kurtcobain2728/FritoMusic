package com.frito.music.extensions.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.frito.music.extensions.ExtensionManager
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Puerto a Kotlin del runtime "signedSession@1" de SpotiFLAC-Mobile
 * (go_backend/extension_signed_session.go).
 *
 * Implementa el protocolo ZARZ-HMAC-V1:
 *  - bootstrap:  GET {baseUrl}/bootstrap?app_version&install_id
 *  - signedFetch: peticiones firmadas con HMAC-SHA256 (rolling key por ventana de tiempo)
 *  - refresh:    POST {baseUrl}/session/refresh (firmado)
 *  - grant:      POST {baseUrl}/session/exchange (sin firmar, tras verificación en navegador)
 *
 * Los métodos públicos devuelven JSON en String para consumo desde el motor JS (Rhino)
 * a través del shim `session` que inyecta ExtensionEngine.
 */
class SignedSessionManager(
    private val context: Context,
    private val extensionId: String
) {
    companion object {
        private const val TAG = "SignedSession"
        private const val PREFS_NAME = "signed_sessions"
        private const val REFRESH_SKEW_MS = 60 * 60 * 1000L // 1 hora (signedSessionRefreshSkew en Go)

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Llamado desde MainActivity cuando llega el deep link spotiflac://session-grant */
        fun setPendingGrant(context: Context, extensionId: String, grant: String) {
            if (extensionId.isBlank() || grant.isBlank()) return
            prefs(context).edit().putString("pending_grant.$extensionId", grant).apply()
            Log.d(TAG, "Grant pendiente guardado para $extensionId")
        }

        /** URL de verificación pendiente para una extensión (si el servidor la pidió). */
        fun getPendingAuthUrl(context: Context, extensionId: String): String? =
            prefs(context).getString("pending_auth_url.$extensionId", null)

        fun clearPendingAuthUrl(context: Context, extensionId: String) {
            prefs(context).edit().remove("pending_auth_url.$extensionId").apply()
        }

        private fun randomHex(bytesLen: Int): String {
            val buf = ByteArray(bytesLen)
            SecureRandom().nextBytes(buf)
            return buf.joinToString("") { "%02x".format(it) }
        }

        private fun sha256Hex(data: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(data).joinToString("") { "%02x".format(it) }
        }

        private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(message)
        }

        private fun base64UrlRaw(data: ByteArray): String =
            android.util.Base64.encodeToString(
                data,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )

        private fun sanitizeNamespace(namespace: String): String {
            val lowered = namespace.trim().lowercase(Locale.US)
            val sb = StringBuilder()
            for (ch in lowered) {
                if (ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.') sb.append(ch)
            }
            return sb.toString().trim('.', '-', '_')
        }
    }

    // ---------- Configuración (desde el manifest.json de la extensión) ----------

    private data class Config(
        val namespace: String,
        val baseUrl: String,
        val appVersion: String,
        val platform: String,
        val callbackUrl: String,
        val schemeLabel: String,
        val headerPrefix: String,
        val timeWindowSeconds: Long,
        val epBootstrap: String,
        val epChallenge: String,
        val epExchange: String,
        val epRefresh: String
    )

    private val config: Config? by lazy { loadConfig() }

    private fun loadConfig(): Config? {
        val raw = ExtensionManager(context).getSignedSessionConfig(extensionId) ?: return null
        val endpoints = raw.optJSONObject("endpoints")
        val namespace = sanitizeNamespace(raw.optString("namespace"))
        val baseUrl = raw.optString("baseUrl").trim()
        if (namespace.isEmpty() || baseUrl.isEmpty()) return null
        return Config(
            namespace = namespace,
            baseUrl = baseUrl.trimEnd('/'),
            appVersion = raw.optString("appVersion").ifEmpty { "ext-1.0" },
            platform = raw.optString("platform").ifEmpty { "extension" },
            callbackUrl = raw.optString("callbackUrl").ifEmpty { "spotiflac://session-grant" },
            schemeLabel = raw.optString("schemeLabel").ifEmpty { "SPOTIFLAC-HMAC-V1" },
            headerPrefix = raw.optString("headerPrefix").ifEmpty { "X-Sig-" },
            timeWindowSeconds = raw.optLong("timeWindowSeconds", 300L).let { if (it > 0) it else 300L },
            epBootstrap = endpoints?.optString("bootstrap")?.ifEmpty { null } ?: "/bootstrap",
            epChallenge = endpoints?.optString("challenge")?.ifEmpty { null } ?: "/challenge",
            epExchange = endpoints?.optString("exchange")?.ifEmpty { null } ?: "/session/exchange",
            epRefresh = endpoints?.optString("refresh")?.ifEmpty { null } ?: "/session/refresh"
        )
    }

    /** true si la extensión declara signedSession en su manifest. */
    fun isConfigured(): Boolean = config != null

    /** true si hay una sesión válida y no expirada. */
    fun isAuthenticated(): Boolean {
        val cfg = config ?: return false
        val record = loadRecord(cfg)
        if (record.sessionId.isEmpty() || record.sessionSecret.isEmpty()) return false
        val exp = parseTime(record.expiresAt) ?: return true
        return System.currentTimeMillis() <= exp
    }

    /** true si hay un grant pendiente llegado por deep link. */
    fun hasPendingGrant(): Boolean =
        prefs(context).contains("pending_grant.$extensionId")

    /**
     * Devuelve la URL de verificación para el usuario (haciendo bootstrap si hace falta),
     * o null si ya hay sesión autenticada.
     */
    fun ensureVerificationUrl(): String? {
        val cfg = config ?: return null
        if (isAuthenticated()) return null
        getPendingAuthUrl(context, extensionId)?.let { return it }
        return startVerification(cfg)
    }

    // ---------- Persistencia del registro de sesión ----------

    private data class Record(
        var installId: String = "",
        var sessionId: String = "",
        var sessionSecret: String = "",
        var expiresAt: String = ""
    )

    private fun scopeKey(cfg: Config): String {
        val scope = listOf(
            cfg.namespace,
            cfg.baseUrl.trim().lowercase(Locale.US),
            cfg.appVersion.trim().lowercase(Locale.US),
            cfg.platform.trim().lowercase(Locale.US)
        ).joinToString("\n")
        return cfg.namespace + "-" + sha256Hex(scope.toByteArray(Charsets.UTF_8)).take(16)
    }

    private fun loadRecord(cfg: Config): Record {
        val key = scopeKey(cfg)
        val p = prefs(context)
        val savedScope = p.getString("$key.scope", null)
        val currentScope = listOf(cfg.namespace, cfg.baseUrl.trim().lowercase(Locale.US),
            cfg.appVersion.trim().lowercase(Locale.US), cfg.platform.trim().lowercase(Locale.US))
            .joinToString("\n")
        val record = Record(
            installId = p.getString("$key.install_id", null) ?: "",
            sessionId = p.getString("$key.session_id", null) ?: "",
            sessionSecret = p.getString("$key.session_secret", null) ?: "",
            expiresAt = p.getString("$key.expires_at", null) ?: ""
        )
        // Si cambió el ámbito (namespace/baseUrl/version/platform), invalidar sesión
        if (savedScope != null && savedScope != currentScope) {
            record.sessionId = ""
            record.sessionSecret = ""
            record.expiresAt = ""
        }
        if (record.installId.isBlank()) {
            record.installId = randomHex(16)
        }
        saveRecord(cfg, record, currentScope)
        return record
    }

    private fun saveRecord(cfg: Config, record: Record, scope: String? = null) {
        val key = scopeKey(cfg)
        val currentScope = scope ?: listOf(cfg.namespace, cfg.baseUrl.trim().lowercase(Locale.US),
            cfg.appVersion.trim().lowercase(Locale.US), cfg.platform.trim().lowercase(Locale.US))
            .joinToString("\n")
        prefs(context).edit()
            .putString("$key.scope", currentScope)
            .putString("$key.install_id", record.installId)
            .putString("$key.session_id", record.sessionId)
            .putString("$key.session_secret", record.sessionSecret)
            .putString("$key.expires_at", record.expiresAt)
            .apply()
    }

    private fun parseTime(value: String): Long? {
        val v = value.trim()
        if (v.isEmpty()) return null
        // Formatos aceptados (Go: RFC3339Nano, RFC3339, "2006-01-02T15:04:05.000Z")
        return try {
            java.time.Instant.parse(v).toEpochMilli()
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(v)?.time
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    sdf.parse(v)?.time
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // ---------- API pública (devuelve JSON String para el shim JS) ----------

    fun status(): String {
        val cfg = config ?: return jsonError("signedSession is not configured", "authenticated" to false)
        val record = loadRecord(cfg)
        var authenticated = record.sessionId.isNotEmpty() && record.sessionSecret.isNotEmpty()
        val exp = parseTime(record.expiresAt)
        if (exp != null && System.currentTimeMillis() > exp) authenticated = false
        return JSONObject()
            .put("authenticated", authenticated)
            .put("expires_at", record.expiresAt)
            .put("install_id", record.installId)
            .put("session_id", record.sessionId)
            .put("app_version", cfg.appVersion)
            .put("platform", cfg.platform)
            .toString()
    }

    fun clear(): String {
        val cfg = config ?: return jsonError("signedSession is not configured", "success" to false)
        val record = loadRecord(cfg)
        record.sessionId = ""
        record.sessionSecret = ""
        record.expiresAt = ""
        saveRecord(cfg, record)
        clearPendingAuthUrl(context, extensionId)
        return JSONObject().put("success", true).toString()
    }

    fun completeGrant(grant: String?): String {
        val cfg = config ?: return jsonError("signedSession is not configured", "success" to false)
        var g = grant?.trim().orEmpty()
        var grantWasPending = false
        if (g.isEmpty()) {
            g = prefs(context).getString("pending_grant.$extensionId", null)?.trim().orEmpty()
            grantWasPending = g.isNotEmpty()
        }
        if (g.isEmpty()) return jsonError("no pending grant", "success" to false)

        return try {
            val record = loadRecord(cfg)
            val endpoint = resolveUrl(cfg, cfg.epExchange)
            val payload = JSONObject()
                .put("grant", g)
                .put("install_id", record.installId)
                .put("app_version", cfg.appVersion)
                .put("platform", cfg.platform)
                .toString()
            val response = rawRequest(
                method = "POST",
                url = endpoint,
                body = payload.toByteArray(Charsets.UTF_8),
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "User-Agent" to "SpotiFLAC-Mobile/${cfg.appVersion}"
                )
            )
            if (response.statusCode !in 200..299) {
                return jsonError("session exchange failed: HTTP ${response.statusCode}", "success" to false)
            }
            val exchanged = JSONObject(response.body)
            val sessionId = exchanged.optString("session_id")
            val sessionSecret = exchanged.optString("session_secret")
            val expiresAt = exchanged.optString("expires_at")
            if (sessionId.isEmpty() || sessionSecret.isEmpty() || expiresAt.isEmpty()) {
                return jsonError("session exchange response missing session fields", "success" to false)
            }
            record.sessionId = sessionId
            record.sessionSecret = sessionSecret
            record.expiresAt = expiresAt
            saveRecord(cfg, record)
            // Solo borrar el grant pendiente tras un intercambio exitoso
            if (grantWasPending) {
                prefs(context).edit().remove("pending_grant.$extensionId").apply()
            }
            clearPendingAuthUrl(context, extensionId)
            Log.d(TAG, "Sesión firmada completada para $extensionId (expira: $expiresAt)")
            JSONObject().put("success", true).toString()
        } catch (e: Exception) {
            Log.e(TAG, "completeGrant failed", e)
            jsonError(e.message ?: "exchange error", "success" to false)
        }
    }

    /**
     * signedFetch(method, path, bodyJson?, headersJson?) -> JSON
     * bodyJson: body ya serializado (el shim JS hace JSON.stringify si es objeto).
     * headersJson: objeto JSON con cabeceras extra (p. ej. X-Zarz-Ticket).
     */
    fun signedFetch(method: String, path: String, bodyJson: String?, headersJson: String?): String {
        val cfg = config ?: return jsonError("signedSession is not configured", "ok" to false)
        val upperMethod = method.trim().uppercase(Locale.US)
        val body = bodyJson?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)

        val extraHeaders = mutableMapOf<String, String>()
        if (!headersJson.isNullOrEmpty() && headersJson != "null") {
            try {
                val obj = JSONObject(headersJson)
                obj.keys().forEach { k -> extraHeaders[k] = obj.optString(k) }
            } catch (_: Exception) { }
        }

        var record: Record
        try {
            record = ensureSession(cfg)
        } catch (e: Exception) {
            val authUrl = startVerification(cfg)
            if (authUrl != null) return verificationRequired(authUrl)
            return jsonError(e.message ?: "signed session is not authenticated", "ok" to false)
        }

        return try {
            val response = signedRequest(cfg, record, upperMethod, path, body, extraHeaders)
            if (response.statusCode == 401 || response.statusCode == 412) {
                // Sesión rechazada: limpiar y pedir verificación
                record.sessionId = ""
                record.sessionSecret = ""
                record.expiresAt = ""
                saveRecord(cfg, record)
                val authUrl = startVerification(cfg)
                if (authUrl != null) return verificationRequired(authUrl)
            }
            val headersObj = JSONObject()
            response.headers.forEach { (k, v) -> headersObj.put(k, v) }
            JSONObject()
                .put("statusCode", response.statusCode)
                .put("status", response.statusCode)
                .put("ok", response.statusCode in 200..299)
                .put("url", response.url)
                .put("body", response.body)
                .put("headers", headersObj)
                .put("retryAfterSeconds", response.retryAfterSeconds)
                .toString()
        } catch (e: Exception) {
            Log.e(TAG, "signedFetch request failed", e)
            jsonError(e.message ?: "signed request failed", "ok" to false)
        }
    }

    // ---------- Núcleo del protocolo ----------

    private fun ensureSession(cfg: Config): Record {
        val record = loadRecord(cfg)
        if (record.sessionId.isEmpty() || record.sessionSecret.isEmpty()) {
            throw Exception("signed session is not authenticated")
        }
        val exp = parseTime(record.expiresAt)
        if (exp != null) {
            val now = System.currentTimeMillis()
            if (now > exp) {
                record.sessionId = ""
                record.sessionSecret = ""
                record.expiresAt = ""
                saveRecord(cfg, record)
                throw Exception("signed session expired")
            }
            if (exp - now <= REFRESH_SKEW_MS && cfg.epRefresh.isNotEmpty()) {
                runCatching { refreshSession(cfg, record) }
            }
        }
        return record
    }

    private fun refreshSession(cfg: Config, record: Record) {
        val body = JSONObject().put("install_id", record.installId).toString()
            .toByteArray(Charsets.UTF_8)
        val response = signedRequest(cfg, record, "POST", cfg.epRefresh, body, emptyMap())
        if (response.statusCode !in 200..299) throw Exception("session refresh failed: HTTP ${response.statusCode}")
        val refreshed = JSONObject(response.body)
        var changed = false
        val sid = refreshed.optString("session_id")
        val sec = refreshed.optString("session_secret")
        val exp = refreshed.optString("expires_at")
        if (sid.isNotEmpty()) { record.sessionId = sid; changed = true }
        if (sec.isNotEmpty()) { record.sessionSecret = sec; changed = true }
        if (exp.isNotEmpty() && exp != record.expiresAt) { record.expiresAt = exp; changed = true }
        if (changed) saveRecord(cfg, record)
    }

    /**
     * GET bootstrap. Si el servidor concede sesión directa, la guarda y devuelve null.
     * Si requiere verificación de usuario, guarda y devuelve la auth URL.
     */
    private fun startVerification(cfg: Config): String? {
        return try {
            val record = loadRecord(cfg)
            val base = resolveUrl(cfg, cfg.epBootstrap)
            val separator = if (base.contains("?")) "&" else "?"
            val url = "$base${separator}app_version=${encode(cfg.appVersion)}&install_id=${encode(record.installId)}"
            val response = rawRequest(
                method = "GET",
                url = url,
                body = null,
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to "SpotiFLAC-Mobile/${cfg.appVersion}"
                )
            )
            if (response.statusCode !in 200..299) return null
            val boot = JSONObject(response.body)
            val sid = boot.optString("session_id")
            val sec = boot.optString("session_secret")
            val exp = boot.optString("expires_at")
            if (sid.isNotEmpty() && sec.isNotEmpty() && exp.isNotEmpty()) {
                record.sessionId = sid
                record.sessionSecret = sec
                record.expiresAt = exp
                saveRecord(cfg, record)
                return null
            }
            var authUrl = boot.optString("auth_url").ifEmpty { boot.optString("challenge_url") }
            if (authUrl.isEmpty()) {
                val challengeId = boot.optString("challenge_id")
                if (challengeId.isNotEmpty()) {
                    authUrl = buildChallengeUrl(cfg, challengeId)
                }
            }
            if (authUrl.isNotEmpty()) {
                prefs(context).edit().putString("pending_auth_url.$extensionId", authUrl).apply()
                Log.d(TAG, "Verificación requerida para $extensionId: $authUrl")
                authUrl
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "startVerification failed", e)
            null
        }
    }

    private fun buildChallengeUrl(cfg: Config, challengeId: String): String {
        val challengeBase = resolveUrl(cfg, cfg.epChallenge)
        val cbSeparator = if (cfg.callbackUrl.contains("?")) "&" else "?"
        val callback = "${cfg.callbackUrl}${cbSeparator}cb_version=v2grant&state=${encode(extensionId)}"
        val separator = if (challengeBase.contains("?")) "&" else "?"
        return "$challengeBase${separator}id=${encode(challengeId)}&cb=${encode(callback)}"
    }

    private fun verificationRequired(authUrl: String): String =
        JSONObject()
            .put("ok", false)
            .put("needsVerification", true)
            .put("error", "VERIFY_REQUIRED")
            .put("open_auth_url", authUrl)
            .put("auth_url", authUrl)
            .toString()

    private data class HttpResult(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, String>,
        val url: String,
        val retryAfterSeconds: Int
    )

    /** Petición firmada ZARZ-HMAC-V1 (equivalente a doSignedSessionRequest en Go). */
    private fun signedRequest(
        cfg: Config,
        record: Record,
        method: String,
        requestPath: String,
        body: ByteArray,
        extraHeaders: Map<String, String>
    ): HttpResult {
        val fullUrl = resolveUrl(cfg, requestPath)
        val path = try {
            URI(fullUrl).rawPath?.ifEmpty { "/" } ?: "/"
        } catch (_: Exception) { "/" }

        val ts = run {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }
        val nonce = randomHex(12)
        val bodyHash = sha256Hex(body)

        // Rolling key: rk = base64url(HMAC_SHA256(secret, "{window}:{sessionId}"))
        val epochSeconds = System.currentTimeMillis() / 1000L
        val window = epochSeconds / cfg.timeWindowSeconds
        val rollingInput = "$window:${record.sessionId}"
        val rk = base64UrlRaw(hmacSha256(record.sessionSecret.toByteArray(Charsets.UTF_8), rollingInput.toByteArray(Charsets.UTF_8)))

        val signingInput = listOf(
            cfg.schemeLabel,
            method,
            path,
            "",
            bodyHash,
            ts,
            nonce,
            record.sessionId,
            cfg.appVersion,
            cfg.platform
        ).joinToString("\n")
        val signature = base64UrlRaw(hmacSha256(rk.toByteArray(Charsets.UTF_8), signingInput.toByteArray(Charsets.UTF_8)))

        val prefix = cfg.headerPrefix
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "User-Agent" to "SpotiFLAC-Mobile/${cfg.appVersion}",
            "${prefix}Session" to record.sessionId,
            "${prefix}Timestamp" to ts,
            "${prefix}Nonce" to nonce,
            "${prefix}Body-SHA256" to bodyHash,
            "${prefix}Signature" to signature,
            "${prefix}App-Version" to cfg.appVersion,
            "${prefix}Platform" to cfg.platform
        )
        if (body.isNotEmpty()) headers["Content-Type"] = "application/json"
        headers.putAll(extraHeaders)

        return rawRequest(method, fullUrl, if (body.isNotEmpty()) body else null, headers)
    }

    private fun rawRequest(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>
    ): HttpResult {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null && body.isNotEmpty()) {
                connection.doOutput = true
                val os: OutputStream = connection.outputStream
                os.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream
            else connection.errorStream ?: connection.inputStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (k, v) ->
                if (k != null && v != null) responseHeaders[k] = v.joinToString("; ")
            }
            val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return HttpResult(code, responseBody, responseHeaders, url, retryAfter)
        } finally {
            connection?.disconnect()
        }
    }

    private fun resolveUrl(cfg: Config, endpoint: String): String {
        val ep = endpoint.trim()
        if (ep.startsWith("https://")) return ep
        return cfg.baseUrl + "/" + ep.trimStart('/')
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun jsonError(message: String, vararg extra: Pair<String, Any>): String {
        val obj = JSONObject().put("error", message)
        extra.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
