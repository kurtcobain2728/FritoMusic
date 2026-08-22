package com.frito.music.extensions.engine

import android.content.Context
import com.frito.music.extensions.session.SignedSessionManager
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.zip.ZipFile

class ExtensionEngine(private val context: Context, private val extensionName: String) {
    private var rhinoContext: org.mozilla.javascript.Context? = null
    private var scope: ScriptableObject? = null
    private val fileBridge = FileBridge()
    private var progressBridge: ProgressBridge? = null

    /** Manifest.json de la extensión (si el paquete lo incluye). */
    var manifestJson: JSONObject? = null
        private set

    /** Gestor de sesión firmada (solo si el manifest declara "signedSession"). */
    var signedSessionManager: SignedSessionManager? = null
        private set

    init {
        initEngine()
    }

    private fun initEngine() {
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

        fileBridge.attach(rhinoContext, scope)

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

        rhinoContext?.evaluateString(scope, jsCode, "index.js", 1, null)
    }

    /** Evaluate JS and convert result to String safely on an 8MB stack thread */
    private fun evalStr(js: String): String? {
        var resultStr: String? = null
        var exception: Throwable? = null
        val thread = Thread(null, {
            try {
                val cx = org.mozilla.javascript.Context.enter()
                cx.optimizationLevel = -1
                cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
                val result = cx.evaluateString(scope, js, "eval", 1, null)
                resultStr = result?.toString()
                android.util.Log.d("ExtensionEngine", "evalStr result (${resultStr?.length ?: -1} chars): ${resultStr?.take(200)}")
            } catch (e: Throwable) {
                exception = e
                android.util.Log.e("ExtensionEngine", "evalStr failed: ${e.message}", e)
            } finally {
                runCatching { org.mozilla.javascript.Context.exit() }
            }
        }, "Rhino8MBThread", 8 * 1024 * 1024L)
        thread.start()
        thread.join()
        return resultStr
    }

    /** Evaluate JS and convert result to Boolean safely on an 8MB stack thread */
    private fun evalBool(js: String): Boolean {
        var resultBool = false
        val thread = Thread(null, {
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
                android.util.Log.e("ExtensionEngine", "evalBool failed: ${e.message}", e)
            } finally {
                runCatching { org.mozilla.javascript.Context.exit() }
            }
        }, "Rhino8MBThread", 8 * 1024 * 1024L)
        thread.start()
        thread.join()
        return resultBool
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

    fun getDownloadUrl(trackId: String, trackUrl: String? = null, quality: String = "320kbps"): String? {
        val escapedId = trackId.replace("\\", "\\\\").replace("'", "\\'")
        val escapedUrl = trackUrl?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
        val escapedQuality = quality.replace("\\", "\\\\").replace("'", "\'")

        // Intentar getDownloadUrl primero (SpotiFLAC o similar directo)
        val hasGetDownloadUrl = evalBool("typeof __extension.getDownloadUrl === 'function'")
        if (hasGetDownloadUrl) {
            val jsCode = "JSON.stringify(__extension.getDownloadUrl('$escapedId', '$escapedUrl', '$escapedQuality'))"
            val result = evalStr(jsCode)
            if (!result.isNullOrEmpty() && result != "null" && result != "undefined") {
                return try {
                    val obj = JSONObject(result)
                    obj.optString("url").takeIf { it.isNotEmpty() }
                } catch (e: Exception) {
                    // Si el resultado es directamente un string plano
                    if (result.startsWith("\"")) result.removeSurrounding("\"") else result
                }
            }
        }

        // Si no hay getDownloadUrl, intentar con la función 'download' con flag urlOnly: true
        val hasDownload = evalBool("typeof __extension.download === 'function'")
        if (hasDownload) {
            // First, clear the intercepted URL
            fileBridge.interceptedUrl = null

            // Try to force the extension to run its download logic with a dummy path, 
            // so we can intercept the URL when it calls file.download
            val interceptCode = """
                try {
                    __extension.download('$escapedId', '$escapedQuality', '/tmp/dummy.m4a', null);
                } catch(e) { log.error("Intercept error: " + e); }
            """.trimIndent()
            evalStr(interceptCode)

            val intercepted = fileBridge.interceptedUrl
            if (!intercepted.isNullOrEmpty()) {
                return intercepted
            }

            // Fallback for extensions that DO return an object
            val jsCode = "JSON.stringify(__extension.download('$escapedId', '$escapedQuality', {urlOnly: true, fetchUrlOnly: true}))"
            val result = evalStr(jsCode)
            if (!result.isNullOrEmpty() && result != "null" && result != "undefined") {
                return try {
                    val obj = JSONObject(result)
                    obj.optString("url").takeIf { it.isNotEmpty() } ?: obj.optString("file_path").takeIf { it.isNotEmpty() }
                } catch (e: Exception) {
                    null
                }
            }
        }

        return null
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

        if (onProgress != null) {
            progressBridge = ProgressBridge(onProgress)
            ScriptableObject.putProperty(scope, "progress", org.mozilla.javascript.Context.javaToJS(progressBridge, scope))
        }

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
        return evalStr(jsCode)
    }

    fun destroy() {
        runCatching { org.mozilla.javascript.Context.exit() }
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
