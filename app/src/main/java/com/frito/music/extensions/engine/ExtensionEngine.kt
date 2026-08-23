package com.frito.music.extensions.engine

import android.content.Context
import com.frito.music.extensions.session.SignedSessionManager
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.zip.ZipFile

class ExtensionEngine(private val context: Context, private val extensionName: String) {
    companion object {
        /** Timeout de seguridad para cada evaluación JS (evita bloqueos eternos). */
        private const val EVAL_TIMEOUT_MS = 300_000L
    }

    private var rhinoContext: org.mozilla.javascript.Context? = null
    private var scope: ScriptableObject? = null
    private val fileBridge = FileBridge()
    private var progressBridge: ProgressBridge? = null

    /**
     * Log COMPLETO del último error del motor (clase, mensaje, hasta 120 frames
     * y causas anidadas). El Gestor de Descargas lo muestra en un modal con
     * botón de copiar cuando una descarga falla por error del motor.
     */
    var lastEngineErrorLog: String? = null
        private set

    /**
     * HILO PERSISTENTE de Rhino (uno solo por motor, pila de 16MB).
     *
     * Antes CADA evaluación creaba un hilo nuevo de 8MB: en una descarga se
     * encadenaban varios (hasDownloadCapability + downloadTrack + búsquedas),
     * y con descargas simultáneas eso agotaba la memoria del proceso
     * ("pthread_create ... stack ... failed" / StackOverflowError) además de
     * violar el confinamiento de hilos que exige Rhino.
     * Ahora TODO corre secuencialmente en este mismo hilo.
     */
    private val rhinoExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            // 32MB de pila: las extensiones escritas para goja pueden consumir
            // mucha más pila Java por frame JS en modo intérprete de Rhino.
            // (La reserva es memoria virtual; lo físico solo se toca al usarla.)
            Thread(null, r, "RhinoEngine-$extensionName", 32L * 1024 * 1024)
        }

    /** Ejecuta [block] en el hilo de Rhino con timeout; propaga el error real. */
    private fun <T> evalWithTimeout(timeoutMs: Long, block: () -> T): T {
        val future = rhinoExecutor.submit(block)
        return try {
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("La extensión tardó demasiado en responder (timeout ${timeoutMs / 1000}s)")
        } catch (e: java.util.concurrent.ExecutionException) {
            throw (e.cause ?: e)
        }
    }

    /** Manifest.json de la extensión (si el paquete lo incluye). */
    var manifestJson: JSONObject? = null
        private set

    /** Gestor de sesión firmada (solo si el manifest declara "signedSession"). */
    var signedSessionManager: SignedSessionManager? = null
        private set

    init {
        // Construir el motor DENTRO del hilo del executor: así Context.enter(),
        // los bridges y la evaluación de index.js quedan confinados a ese hilo,
        // igual que todas las evaluaciones posteriores.
        try {
            evalWithTimeout(60_000L) { initEngineInternal() }
        } catch (e: Throwable) {
            android.util.Log.e("ExtensionEngine", "Fallo inicializando extensión $extensionName", e)
            throw e
        }
    }

    private fun initEngineInternal() {
        val extFile = File(context.filesDir, "extensions/$extensionName.spotiflac-ext")
        if (!extFile.exists()) throw Exception("Extension file not found")

        var jsCode = ""
        ZipFile(extFile).use { zip ->
            val entry = zip.getEntry("index.js") ?: throw Exception("index.js not found in extension")
            jsCode = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val manifestEntry = zip.getEntry("manifest.json")
            if (manifestEntry != null) {
                runCatching {
                    manifestJson = JSONObject(zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() })
                }
            }
        }

        jsCode = preprocessES6(jsCode)

        rhinoContext = org.mozilla.javascript.Context.enter()
        rhinoContext?.optimizationLevel = -1
        rhinoContext?.languageVersion = org.mozilla.javascript.Context.VERSION_ES6

        scope = rhinoContext?.initStandardObjects()

        ScriptableObject.putProperty(scope, "http", org.mozilla.javascript.Context.javaToJS(HttpBridge(), scope))
        ScriptableObject.putProperty(scope, "log", org.mozilla.javascript.Context.javaToJS(LogBridge(), scope))
        ScriptableObject.putProperty(scope, "gobackend", org.mozilla.javascript.Context.javaToJS(GoBackendBridge(), scope))
        ScriptableObject.putProperty(scope, "utils", org.mozilla.javascript.Context.javaToJS(UtilsBridge(), scope))
        ScriptableObject.putProperty(scope, "matching", org.mozilla.javascript.Context.javaToJS(MatchingBridge(), scope))
        ScriptableObject.putProperty(scope, "storage", org.mozilla.javascript.Context.javaToJS(StorageBridge(context, extensionName), scope))
        ScriptableObject.putProperty(scope, "file", org.mozilla.javascript.Context.javaToJS(fileBridge, scope))

        // Sesión firmada (signedSession@1): solo si el manifest la declara
        if (manifestJson?.optJSONObject("signedSession") != null) {
            val manager = SignedSessionManager(context, extensionName)
            if (manager.isConfigured()) {
                signedSessionManager = manager
                ScriptableObject.putProperty(scope, "__session_native", org.mozilla.javascript.Context.javaToJS(manager, scope))
            }
        }

        // Construir el objeto settings con los valores por defecto del manifest
        val settingsJson = buildManifestSettingsJson()

        val registerCode = """
            var __extension = null;
            var __settings = $settingsJson;
            function registerExtension(ext) {
                __extension = ext;
                if (typeof ext.initialize === 'function') {
                    try { ext.initialize(__settings); } catch(e) { log.error("Init error: " + e); }
                }
            }
        """.trimIndent()
        rhinoContext?.evaluateString(scope, registerCode, "registerExtension", 1, null)

        // Shim JS del objeto `session` (firmado HMAC) sobre el bridge nativo
        if (signedSessionManager != null) {
            val sessionShim = """
                var session = {
                    status: function() { return JSON.parse(__session_native.status()); },
                    clear: function() { return JSON.parse(__session_native.clear()); },
                    completeGrant: function(grant) { return JSON.parse(__session_native.completeGrant(grant || null)); },
                    signedFetch: function(method, path, body, headers) {
                        var bodyStr = null;
                        if (body !== null && body !== undefined) {
                            bodyStr = (typeof body === 'string') ? body : JSON.stringify(body);
                        }
                        var headersStr = (headers !== null && headers !== undefined) ? JSON.stringify(headers) : null;
                        return JSON.parse(__session_native.signedFetch(String(method), String(path), bodyStr, headersStr));
                    }
                };
            """.trimIndent()
            rhinoContext?.evaluateString(scope, sessionShim, "sessionShim", 1, null)
        }

        // ─── Shim anti-catastrofic-backtracking para manifiestos MPD ───
        // El regex /<S\s+[^>]*d="(\d+)"(?:\s+r="(-?\d+)")?[^>]*\/?>/gi que las
        // extensiones ejecutan sobre manifiestos enormes hace que java.util.regex
        // (recursivo por diseño) reviente con StackOverflowError. Este shim
        // enruta SOLO ese patrón al escáner iterativo nativo (utils.mpdScan),
        // preservando la semántica de exec(): match[1], match[2], lastIndex y
        // null al terminar. Todos los demás regex pasan intactos.
        val mpdShim = """
            (function() {
                if (typeof utils === 'undefined' || typeof utils.mpdScan !== 'function') return;
                var origExec = RegExp.prototype.exec;
                RegExp.prototype.exec = function(str) {
                    try {
                        var src = this && this.source ? String(this.source) : '';
                        var isKiller = src.length > 10 &&
                            src.charAt(0) === '<' && src.charAt(1) === 'S' &&
                            src.indexOf('[^>]') !== -1 &&
                            src.indexOf('d=') !== -1;
                        if (isKiller && str !== null && str !== undefined && String(str).indexOf('<S') !== -1) {
                            var s = String(str);
                            var all;
                            if (this.__mpdKey === s) {
                                all = this.__mpdAll;
                            } else {
                                all = JSON.parse(utils.mpdScan(s));
                            }
                            // SOLO tomamos el control si el escáner encontró tags.
                            // Si devolvió vacío, dejamos pasar al original para no
                            // ocultar matches que nuestro parser pudiera perder.
                            if (all && all.length > 0) {
                                if (this.__mpdKey !== s) {
                                    this.__mpdKey = s;
                                    this.__mpdIdx = 0;
                                }
                                this.__mpdAll = all;
                                if (this.__mpdIdx >= all.length) {
                                    this.lastIndex = 0;
                                    this.__mpdKey = null;
                                    return null;
                                }
                                var m = all[this.__mpdIdx++];
                                this.lastIndex = m.end;
                                var arr = [m.full, String(m.d), String(m.r)];
                                arr.index = m.end;
                                return arr;
                            }
                        }
                    } catch (e) { /* cualquier problema: caer al exec original */ }
                    return origExec.call(this, str);
                };
            })();
        """.trimIndent()
        rhinoContext?.evaluateString(scope, mpdShim, "mpdSafeShim", 1, null)

        rhinoContext?.evaluateString(scope, jsCode, "index.js", 1, null)
    }

    /** Evaluate JS and convert result to String safely on the persistent Rhino thread */
    private fun evalStr(js: String): String? = evalWithTimeout(EVAL_TIMEOUT_MS) {
        var resultStr: String? = null
        try {
            // Ya estamos en el hilo del executor: enter/exit anidan correctamente
            val cx = org.mozilla.javascript.Context.enter()
            cx.optimizationLevel = -1
            cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
            val result = cx.evaluateString(scope, js, "eval", 1, null)
            resultStr = result?.toString()
            android.util.Log.d("ExtensionEngine", "evalStr result (${resultStr?.length ?: -1} chars): ${resultStr?.take(200)}")
        } catch (e: Throwable) {
            android.util.Log.e("ExtensionEngine", "evalStr failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            runCatching { org.mozilla.javascript.Context.exit() }
        }
        resultStr
    }

    /** Evaluate JS and convert result to Boolean safely on the persistent Rhino thread */
    private fun evalBool(js: String): Boolean = try {
        evalWithTimeout(EVAL_TIMEOUT_MS) {
            var resultBool = false
            try {
                val cx = org.mozilla.javascript.Context.enter()
                cx.optimizationLevel = -1
                cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
                val result = cx.evaluateString(scope, js, "eval", 1, null)
                resultBool = when (result) {
                    is Boolean -> result
                    is Number -> result.toDouble() != 0.0
                    else -> result?.toString()?.lowercase()?.let { it == "true" || it != "null" && it.isNotEmpty() } ?: false
                }
            } catch (e: Throwable) {
                android.util.Log.e("ExtensionEngine", "evalBool failed: ${e.javaClass.simpleName}: ${e.message}", e)
            } finally {
                runCatching { org.mozilla.javascript.Context.exit() }
            }
            resultBool
        }
    } catch (e: Throwable) {
        android.util.Log.e("ExtensionEngine", "evalBool timeout/error: ${e.message}")
        false
    }

    fun performSearch(query: String): SearchResult {
        val hasSearch = evalBool("typeof __extension !== 'undefined' && __extension !== null && (typeof __extension.customSearch === 'function' || typeof __extension.search === 'function' || typeof __extension.searchTracks === 'function')")
        if (!hasSearch) {
            android.util.Log.w("ExtensionEngine", "Extension $extensionName has no search methods")
            return SearchResult(emptyList(), emptyList(), emptyList())
        }

        val escapedQuery = query.replace("\\", "\\\\").replace("'", "\\'")
        var result: String? = null

        val hasCustomSearch = evalBool("typeof __extension.customSearch === 'function'")
        if (hasCustomSearch) {
            val jsCode = """
                (function() {
                    try {
                        var res = __extension.customSearch('$escapedQuery', {types: ['track', 'album', 'artist'], limit: 20});
                        if (res && typeof res.then === 'function') {
                            var resolvedVal = null;
                            var done = false;
                            res.then(function(v) { resolvedVal = v; done = true; })['catch'](function() { done = true; });
                            var start = Date.now();
                            while (!done && (Date.now() - start < 30000)) {
                                java.lang.Thread.sleep(50);
                            }
                            return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                        } else {
                            return (typeof res === 'string') ? res : JSON.stringify(res);
                        }
                    } catch(e) { return null; }
                })()
            """.trimIndent()
            result = evalStr(jsCode)
        } else {
            val hasNormalSearch = evalBool("typeof __extension.search === 'function'")
            if (hasNormalSearch) {
                val jsCode = """
                    (function() {
                        try {
                            var res = __extension.search('$escapedQuery', 'track,album,artist', 20);
                            if (res && typeof res.then === 'function') {
                                var resolvedVal = null;
                                var done = false;
                                res.then(function(v) { resolvedVal = v; done = true; })['catch'](function() { done = true; });
                                var start = Date.now();
                                while (!done && (Date.now() - start < 30000)) {
                                    java.lang.Thread.sleep(50);
                                }
                                return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                            } else {
                                return (typeof res === 'string') ? res : JSON.stringify(res);
                            }
                        } catch(e) { return null; }
                    })()
                """.trimIndent()
                result = evalStr(jsCode)
            } else {
                val hasSearchTracks = evalBool("typeof __extension.searchTracks === 'function'")
                if (hasSearchTracks) {
                    val jsCode = """
                        (function() {
                            try {
                                var res = __extension.searchTracks('$escapedQuery', 20);
                                if (res && typeof res.then === 'function') {
                                    var resolvedVal = null;
                                    var done = false;
                                    res.then(function(v) { resolvedVal = v; done = true; })['catch'](function() { done = true; });
                                    var start = Date.now();
                                    while (!done && (Date.now() - start < 30000)) {
                                        java.lang.Thread.sleep(50);
                                    }
                                    return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                                } else {
                                    return (typeof res === 'string') ? res : JSON.stringify(res);
                                }
                            } catch(e) { return null; }
                        })()
                    """.trimIndent()
                    val tracksResult = evalStr(jsCode)
                    if (!tracksResult.isNullOrEmpty() && tracksResult != "null" && tracksResult != "undefined") {
                        result = "{\"tracks\": $tracksResult, \"albums\": [], \"artists\": []}"
                    }
                }
            }
        }

        if (result.isNullOrEmpty() || result == "null" || result == "undefined") {
            return SearchResult(emptyList(), emptyList(), emptyList())
        }

        return parseSearchResults(result)
    }

    private fun parseSearchResults(result: String): SearchResult {
        val tracks = mutableListOf<TrackResult>()
        val albums = mutableListOf<AlbumResult>()
        val artists = mutableListOf<ArtistResult>()

        try {
            if (result.trim().startsWith("[")) {
                val arr = JSONArray(result)
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val type = item.optString("item_type")
                        .ifEmpty { item.optString("type") }
                        .lowercase()
                    when {
                        type.contains("artist") -> artists.add(adaptArtist(item))
                        type.contains("album") || type.contains("playlist") -> albums.add(adaptAlbum(item))
                        else -> tracks.add(adaptTrack(item))
                    }
                }
            } else {
                val obj = JSONObject(result)
                val tracksArr = obj.optJSONArray("tracks") ?: obj.optJSONArray("songs") ?: obj.optJSONArray("results")
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) tracks.add(adaptTrack(tracksArr.getJSONObject(i)))
                }
                val albumsArr = obj.optJSONArray("albums")
                if (albumsArr != null) {
                    for (i in 0 until albumsArr.length()) albums.add(adaptAlbum(albumsArr.getJSONObject(i)))
                }
                val artistsArr = obj.optJSONArray("artists")
                if (artistsArr != null) {
                    for (i in 0 until artistsArr.length()) artists.add(adaptArtist(artistsArr.getJSONObject(i)))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExtensionEngine", "Failed to parse search results: ${e.message}", e)
        }

        return SearchResult(tracks, albums, artists)
    }

    private fun adaptTrack(t: JSONObject): TrackResult {
        val id = t.optString("id").ifEmpty { t.optString("videoId") }.ifEmpty { t.optString("trackId") }.ifEmpty { t.optString("track_id") }
        val name = t.optString("title").ifEmpty { t.optString("name") }
        val artistsStr = parseArtistsString(t)
        
        val albumObj = t.optJSONObject("album")
        val albumName = albumObj?.optString("title")?.ifEmpty { albumObj.optString("name") } ?: t.optString("album")
        
        var durationMs = t.optLong("duration_ms", 0L)
        if (durationMs == 0L) durationMs = t.optLong("durationMs", 0L)
        if (durationMs == 0L) durationMs = t.optLong("duration", 0L) * 1000L

        val imageUrl = t.optString("imageUrl")
            .ifEmpty { t.optString("thumbnailUrl") }
            .ifEmpty { t.optString("thumbnail") }
            .ifEmpty { t.optString("cover_url") }
            .ifEmpty { t.optString("image_url") }
            .ifEmpty { t.optString("images") }
            .ifEmpty { albumObj?.optString("cover_url") ?: "" }
            .ifEmpty { albumObj?.optString("cover_xl") ?: "" }

        val externalUrl = t.optString("external_url").ifEmpty { t.optString("url") }.ifEmpty { t.optString("externalUrl") }.ifEmpty { t.optString("link") }

        return TrackResult(id, name, artistsStr, albumName, durationMs, imageUrl, externalUrl, extensionName)
    }

    private fun adaptAlbum(a: JSONObject): AlbumResult {
        val id = a.optString("id").ifEmpty { a.optString("albumId") }.ifEmpty { a.optString("album_id") }
        val name = a.optString("title").ifEmpty { a.optString("name") }
        val artistsStr = parseArtistsString(a)
        val imageUrl = a.optString("imageUrl").ifEmpty { a.optString("thumbnailUrl") }.ifEmpty { a.optString("thumbnail") }.ifEmpty { a.optString("cover_url") }.ifEmpty { a.optString("coverUrl") }.ifEmpty { a.optString("images") }.ifEmpty { a.optString("image_url") }
        return AlbumResult(id, name, artistsStr, imageUrl, extensionName)
    }

    private fun adaptArtist(a: JSONObject): ArtistResult {
        val id = a.optString("id").ifEmpty { a.optString("artistId") }.ifEmpty { a.optString("artist_id") }
        val name = a.optString("name").ifEmpty { a.optString("title") }
        val imageUrl = a.optString("imageUrl").ifEmpty { a.optString("thumbnailUrl") }.ifEmpty { a.optString("thumbnail") }.ifEmpty { a.optString("avatarUrl") }.ifEmpty { a.optString("picture_xl") }.ifEmpty { a.optString("images") }.ifEmpty { a.optString("image_url") }
        return ArtistResult(id, name, imageUrl, extensionName)
    }

    private fun parseArtistsString(obj: JSONObject): String {
        val artistsArray = obj.optJSONArray("artists")
        if (artistsArray != null) {
            val names = mutableListOf<String>()
            for (i in 0 until artistsArray.length()) {
                val item = artistsArray.opt(i)
                if (item is JSONObject) {
                    names.add(item.optString("name"))
                } else if (item is String) {
                    names.add(item)
                }
            }
            if (names.isNotEmpty()) return names.joinToString(", ")
        }
        return obj.optString("artists").ifEmpty { obj.optString("artist") }
    }

    fun fetchArtist(artistId: String): String {
        val hasMethod = evalBool("typeof __extension !== 'undefined' && __extension !== null && (typeof __extension.getArtist === 'function' || typeof __extension.getArtistDetails === 'function')")
        if (!hasMethod) {
            return ""
        }
        val escapedId = artistId.replace("\\", "\\\\").replace("'", "\\'")
        val jsCode = """
            (function() {
                try {
                    var fn = __extension.getArtist || __extension.getArtistDetails;
                    if (typeof fn !== 'function') return "";
                    var res = fn.call(__extension, '$escapedId');
                    if (res && typeof res.then === 'function') {
                        var resolvedVal = null;
                        var done = false;
                        res.then(function(v) { resolvedVal = v; done = true; })['catch'](function() { done = true; });
                        var start = Date.now();
                        while (!done && (Date.now() - start < 30000)) {
                            java.lang.Thread.sleep(50);
                        }
                        return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                    } else {
                        return (typeof res === 'string') ? res : JSON.stringify(res);
                    }
                } catch(e) {
                    return "";
                }
            })()
        """.trimIndent()
        val result = evalStr(jsCode) ?: ""
        return if (result == "null" || result == "undefined") "" else result
    }

    fun fetchAlbum(albumId: String): String {
        val hasMethod = evalBool("typeof __extension !== 'undefined' && __extension !== null && (typeof __extension.getAlbum === 'function' || typeof __extension.getAlbumDetails === 'function' || typeof __extension.getAlbumTracks === 'function')")
        if (!hasMethod) {
            return ""
        }
        val escapedId = albumId.replace("\\", "\\\\").replace("'", "\\'")
        val jsCode = """
            (function() {
                try {
                    var fn = __extension.getAlbum || __extension.getAlbumDetails || __extension.getAlbumTracks;
                    if (typeof fn !== 'function') return "";
                    var res = fn.call(__extension, '$escapedId');
                    if (res && typeof res.then === 'function') {
                        var resolvedVal = null;
                        var done = false;
                        res.then(function(v) { resolvedVal = v; done = true; })['catch'](function() { done = true; });
                        var start = Date.now();
                        while (!done && (Date.now() - start < 30000)) {
                            java.lang.Thread.sleep(50);
                        }
                        return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                    } else {
                        return (typeof res === 'string') ? res : JSON.stringify(res);
                    }
                } catch(e) {
                    return "";
                }
            })()
        """.trimIndent()
        val result = evalStr(jsCode) ?: ""
        return if (result == "null" || result == "undefined") "" else result
    }

    /** true si la extensión registra una función download() (download_provider). */
    fun hasDownloadCapability(): Boolean {
        return evalBool("typeof __extension !== 'undefined' && __extension !== null && typeof __extension.download === 'function'")
    }

    /**
     * Ejecuta la descarga completa dentro de la extensión (arquitectura SpotiFLAC):
     * la propia extensión resuelve URLs, descarga segmentos y escribe el archivo final
     * a través del FileBridge nativo.
     *
     * @return JSON con {success, file_path, error_message, actual_extension, title, artist, album, ...}
     */
    fun downloadTrack(
        trackId: String,
        quality: String,
        outputPath: String,
        onProgress: ((Int) -> Unit)? = null
    ): String? {
        val escapedId = trackId.replace("\\", "\\\\").replace("'", "\\'")
        val escapedQuality = quality.replace("\\", "\\\\").replace("'", "\\'")
        val escapedPath = outputPath.replace("\\", "\\\\").replace("'", "\\'")

        // El bridge se crea aquí, pero se INYECTA dentro del hilo de Rhino
        // (antes se inyectaba en el hilo llamador: violación de confinamiento).
        progressBridge = onProgress?.let { ProgressBridge(it) }
        lastEngineErrorLog = null

        val jsCode = """
            (function() {
                try {
                    if (typeof __extension === 'undefined' || __extension === null || typeof __extension.download !== 'function') {
                        return JSON.stringify({success: false, error_message: 'La extensión no soporta descargas', error_type: 'unsupported'});
                    }
                    var cb = (typeof progress !== 'undefined' && progress) ? function(p) { progress.report(p); } : null;
                    var res = __extension.download('$escapedId', '$escapedQuality', '$escapedPath', cb);
                    
                    if (res && typeof res.then === 'function') {
                        var resolvedVal = null;
                        var resolvedErr = null;
                        var done = false;
                        
                        res.then(function(val) {
                            resolvedVal = val;
                            done = true;
                        })['catch'](function(err) {
                            resolvedErr = err;
                            done = true;
                        });
                        
                        var start = Date.now();
                        while (!done && (Date.now() - start < 300000)) {
                            java.lang.Thread.sleep(50);
                        }
                        if (!done) {
                            return JSON.stringify({success: false, error_message: 'Tiempo de espera agotado (Timeout) en descarga', error_type: 'timeout'});
                        }
                        if (resolvedErr) {
                            return JSON.stringify({success: false, error_message: String(resolvedErr.message || resolvedErr), error_type: 'runtime_error'});
                        }
                        return (typeof resolvedVal === 'string') ? resolvedVal : JSON.stringify(resolvedVal);
                    } else {
                        return (typeof res === 'string') ? res : JSON.stringify(res);
                    }
                } catch (e) {
                    return JSON.stringify({success: false, error_message: String(e && e.message ? e.message : e), error_type: 'runtime_error'});
                }
            })()
        """.trimIndent()

        // OJO: no usar evalStr aquí (anidaríamos dos timeouts sobre el MISMO
        // executor y nos daría deadlock). Este bloque corre directamente en el
        // hilo de Rhino: inyecta el bridge, evalúa, y si el motor revienta
        // (StackOverflowError, OOM...) devuelve el error REAL con su clase para
        // que el Gestor de Descargas muestre la causa exacta.
        return try {
            evalWithTimeout(EVAL_TIMEOUT_MS) {
                var resultStr: String? = null
                try {
                    val pb = progressBridge
                    if (pb != null && scope != null) {
                        ScriptableObject.putProperty(
                            scope,
                            "progress",
                            org.mozilla.javascript.Context.javaToJS(pb, scope)
                        )
                    }
                    // Progreso POR BYTES real (0-100) desde FileBridge, sin re-entrar a JS
                    fileBridge.byteProgressReporter = onProgress

                    val cx = org.mozilla.javascript.Context.enter()
                    cx.optimizationLevel = -1
                    cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
                    val result = cx.evaluateString(scope, jsCode, "downloadTrack", 1, null)
                    resultStr = result?.toString()
                } catch (e: Throwable) {
                    android.util.Log.e(
                        "ExtensionEngine",
                        "downloadTrack failed: ${e.javaClass.simpleName}: ${e.message}", e
                    )
                    // Guardar la traza COMPLETA para el modal del Gestor
                    lastEngineErrorLog = buildErrorLog(e)
                    resultStr = JSONObject()
                        .put("success", false)
                        .put("error_message", "${e.javaClass.simpleName}: ${e.message ?: "error desconocido del motor"}")
                        .put("error_type", "engine_error")
                        .toString()
                } finally {
                    runCatching { org.mozilla.javascript.Context.exit() }
                    fileBridge.byteProgressReporter = null
                }
                resultStr
            }
        } catch (e: Throwable) {
            // Timeout del executor u otro fallo externo
            android.util.Log.e("ExtensionEngine", "downloadTrack engine timeout/error", e)
            lastEngineErrorLog = buildErrorLog(e)
            JSONObject()
                .put("success", false)
                .put("error_message", "${e.javaClass.simpleName}: ${e.message ?: "error desconocido"}")
                .put("error_type", "engine_error")
                .toString()
        }
    }

    /** Traza completa (hasta 150 frames + hasta 5 causas anidadas). */
    private fun buildErrorLog(e: Throwable): String = buildString {
        appendLine("${e.javaClass.name}: ${e.message ?: "(sin mensaje)"}")
        appendLine("  (hilo: ${Thread.currentThread().name})")
        e.stackTrace.take(150).forEach { st ->
            appendLine("  at ${st.className}.${st.methodName}(${st.fileName}:${st.lineNumber})")
        }
        if (e.stackTrace.size > 150) appendLine("  ... (${e.stackTrace.size - 150} frames más)")
        var cause = e.cause
        var depth = 0
        while (cause != null && depth < 5) {
            appendLine()
            appendLine("Caused by: ${cause.javaClass.name}: ${cause.message ?: "(sin mensaje)"}")
            cause.stackTrace.take(60).forEach { st ->
                appendLine("  at ${st.className}.${st.methodName}(${st.fileName}:${st.lineNumber})")
            }
            cause = cause.cause
            depth++
        }
    }

    fun destroy() {
        // Apagar el hilo persistente (interrumpe busy-waits dormidos)
        runCatching { rhinoExecutor.shutdownNow() }
        rhinoContext = null
        scope = null
    }

    /**
     * Lee los "settings" del manifest.json de la extensión y devuelve un string
     * JSON con un objeto {key: defaultValue, ...} para pasar a initialize().
     * Ej: {publicToken:"49YxDN9a2aFV6RTG", downloadApiUrl:"https://api.zarz.moe/v1/dl/tid2", ...}
     */
    private fun buildManifestSettingsJson(): String {
        val manifest = manifestJson ?: return "{}"
        val settingsArray = manifest.optJSONArray("settings") ?: return "{}"
        val obj = JSONObject()
        for (i in 0 until settingsArray.length()) {
            val setting = settingsArray.optJSONObject(i) ?: continue
            val key = setting.optString("key").ifEmpty { continue }
            val defaultVal = setting.opt("default")
            if (defaultVal != null && defaultVal != JSONObject.NULL) {
                obj.put(key, defaultVal)
            }
        }
        return obj.toString()
    }

    private fun preprocessES6(code: String): String {
        // Rhino with VERSION_ES6 already supports ES6 natively
        // No preprocessing needed - it was causing more problems than it solved
        return code
    }
}
