package com.frito.music.extensions.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.zip.ZipFile

class ExtensionEngine(private val context: Context, private val extensionName: String) {
    private var rhinoContext: org.mozilla.javascript.Context? = null
    private var scope: ScriptableObject? = null
    private val fileBridge = FileBridge()

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
        ScriptableObject.putProperty(scope, "storage", org.mozilla.javascript.Context.javaToJS(StorageBridge(), scope))
        ScriptableObject.putProperty(scope, "file", org.mozilla.javascript.Context.javaToJS(fileBridge, scope))

        val registerCode = """
            var __extension = null;
            function registerExtension(ext) {
                __extension = ext;
                if (typeof ext.initialize === 'function') {
                    try { ext.initialize({}); } catch(e) { log.error("Init error: " + e); }
                }
            }
        """.trimIndent()
        rhinoContext?.evaluateString(scope, registerCode, "registerExtension", 1, null)

        rhinoContext?.evaluateString(scope, jsCode, "index.js", 1, null)
    }

    /** Evaluate JS and convert result to String safely (Rhino returns NativeObject, not Java String) */
    private fun evalStr(js: String): String? {
        return try {
            val result = rhinoContext?.evaluateString(scope, js, "eval", 1, null)
            val str = result?.toString()
            android.util.Log.d("ExtensionEngine", "evalStr result (${str?.length ?: -1} chars): ${str?.take(200)}")
            str
        } catch (e: Exception) {
            android.util.Log.e("ExtensionEngine", "evalStr failed: ${e.message}", e)
            null
        }
    }

    /** Evaluate JS and convert result to Boolean safely */
    private fun evalBool(js: String): Boolean {
        return try {
            val result = rhinoContext?.evaluateString(scope, js, "eval", 1, null)
            when (result) {
                is Boolean -> result
                is Number -> result.toDouble() != 0.0
                else -> result?.toString()?.lowercase()?.let { it == "true" || it != "null" && it.isNotEmpty() } ?: false
            }
        } catch (e: Exception) {
            android.util.Log.e("ExtensionEngine", "evalBool failed: ${e.message}", e)
            false
        }
    }

    // --- Eliminated fetchHomeFeed and fetchBrowse logic as per RN architecture ---

    fun performSearch(query: String): SearchResult {
        val hasSearch = evalBool("typeof __extension !== 'undefined' && __extension !== null && (typeof __extension.customSearch === 'function' || typeof __extension.search === 'function' || typeof __extension.searchTracks === 'function')")
        if (!hasSearch) {
            android.util.Log.w("ExtensionEngine", "Extension $extensionName has no search methods")
            return SearchResult(emptyList(), emptyList(), emptyList())
        }

        val escapedQuery = query.replace("\\", "\\\\").replace("'", "\\'")
        var result: String? = null

        // Intenta customSearch (SpotiFLAC), search, y luego searchTracks (como en RN musicSearch.ts)
        val hasCustomSearch = evalBool("typeof __extension.customSearch === 'function'")
        if (hasCustomSearch) {
            val jsCode = "JSON.stringify(__extension.customSearch('$escapedQuery', {types: ['track', 'album', 'artist'], limit: 20}))"
            result = evalStr(jsCode)
        } else {
            val hasNormalSearch = evalBool("typeof __extension.search === 'function'")
            if (hasNormalSearch) {
                val jsCode = "JSON.stringify(__extension.search('$escapedQuery', 'track,album,artist', 20))"
                result = evalStr(jsCode)
            } else {
                val hasSearchTracks = evalBool("typeof __extension.searchTracks === 'function'")
                if (hasSearchTracks) {
                    val jsCode = "JSON.stringify(__extension.searchTracks('$escapedQuery', 20))"
                    val tracksResult = evalStr(jsCode)
                    if (!tracksResult.isNullOrEmpty() && tracksResult != "null" && tracksResult != "undefined") {
                        // Envolver en objeto para parseSearchResults
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
            // En React Native, el customSearch de SpotiFLAC puede devolver un Array (solo tracks) o un Objeto { tracks, albums, artists }
            if (result.trim().startsWith("[")) {
                val arr = JSONArray(result)
                for (i in 0 until arr.length()) {
                    tracks.add(adaptTrack(arr.getJSONObject(i)))
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
            .ifEmpty { albumObj?.optString("cover_url") ?: "" }
            .ifEmpty { albumObj?.optString("cover_xl") ?: "" }

        val externalUrl = t.optString("external_url").ifEmpty { t.optString("url") }.ifEmpty { t.optString("externalUrl") }.ifEmpty { t.optString("link") }

        return TrackResult(id, name, artistsStr, albumName, durationMs, imageUrl, externalUrl, extensionName)
    }

    private fun adaptAlbum(a: JSONObject): AlbumResult {
        val id = a.optString("id").ifEmpty { a.optString("albumId") }.ifEmpty { a.optString("album_id") }
        val name = a.optString("title").ifEmpty { a.optString("name") }
        val artistsStr = parseArtistsString(a)
        val imageUrl = a.optString("imageUrl").ifEmpty { a.optString("thumbnailUrl") }.ifEmpty { a.optString("thumbnail") }.ifEmpty { a.optString("cover_url") }.ifEmpty { a.optString("coverUrl") }
        return AlbumResult(id, name, artistsStr, imageUrl, extensionName)
    }

    private fun adaptArtist(a: JSONObject): ArtistResult {
        val id = a.optString("id").ifEmpty { a.optString("artistId") }.ifEmpty { a.optString("artist_id") }
        val name = a.optString("name").ifEmpty { a.optString("title") }
        val imageUrl = a.optString("imageUrl").ifEmpty { a.optString("thumbnailUrl") }.ifEmpty { a.optString("thumbnail") }.ifEmpty { a.optString("avatarUrl") }.ifEmpty { a.optString("picture_xl") }
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
        val hasMethod = evalBool("typeof __extension.getArtist === 'function'")
        if (!hasMethod) {
            return ""
        }
        val result = evalStr("JSON.stringify(__extension.getArtist('$artistId'))")
            ?: throw Exception("getArtist returned null")

        return result
    }

    fun fetchAlbum(albumId: String): String {
        val hasMethod = evalBool("typeof __extension.getAlbum === 'function'")
        if (!hasMethod) {
            return ""
        }
        val result = evalStr("JSON.stringify(__extension.getAlbum('$albumId'))")
            ?: throw Exception("getAlbum returned null")

        return result
    }

    fun getDownloadUrl(trackId: String, trackUrl: String? = null): String? {
        val escapedId = trackId.replace("\\", "\\\\").replace("'", "\\'")
        val escapedUrl = trackUrl?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""

        // Intentar getDownloadUrl primero (SpotiFLAC o similar directo)
        val hasGetDownloadUrl = evalBool("typeof __extension.getDownloadUrl === 'function'")
        if (hasGetDownloadUrl) {
            val jsCode = "JSON.stringify(__extension.getDownloadUrl('$escapedId', '$escapedUrl'))"
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
                    __extension.download('$escapedId', 'LOSSLESS', '/tmp/dummy.m4a', null);
                } catch(e) { log.error("Intercept error: " + e); }
            """.trimIndent()
            evalStr(interceptCode)

            val intercepted = fileBridge.interceptedUrl
            if (!intercepted.isNullOrEmpty()) {
                return intercepted
            }

            // Fallback for extensions that DO return an object
            val jsCode = "JSON.stringify(__extension.download('$escapedId', '', {urlOnly: true, fetchUrlOnly: true}))"
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

    fun destroy() {
        runCatching { org.mozilla.javascript.Context.exit() }
        rhinoContext = null
        scope = null
    }

    private fun preprocessES6(code: String): String {
        return try {
            var js = code
            js = js.replace(Regex("""for\s*\(\s*const\s+"""), "for (let ")
            js = js.replace(Regex("""\bconst\s+"""), "let ")
            if (js.contains(".padStart(")) {
                val polyfill = """
                    |if (!String.prototype.padStart) {
                    |    String.prototype.padStart = function(targetLength, padString) {
                    |        targetLength = targetLength >> 0;
                    |        padString = String(padString || ' ');
                    |        if (this.length >= targetLength) return String(this);
                    |        targetLength = targetLength - this.length;
                    |        if (targetLength > padString.length) {
                    |            padString = padString.repeat(Math.ceil(targetLength / padString.length));
                    |        }
                    |        return padString.slice(0, targetLength) + String(this);
                    |    };
                    |}
                """.trimMargin()
                js = polyfill + "\n" + js
            }
            js = js.replace(
                Regex("""([a-zA-Z0-9_]+)\.push\(\.\.\.([a-zA-Z0-9_]+)\)"""),
                "Array.prototype.push.apply($1, $2)"
            )
            android.util.Log.d("ExtensionEngine", "ES6 preprocessor applied to $extensionName (${js.length} chars)")
            js
        } catch (e: Exception) {
            android.util.Log.e("ExtensionEngine", "ES6 preprocessor error for $extensionName, using original", e)
            code
        }
    }
}
