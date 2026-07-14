# PoToken Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans to implement this plan task-by-task.

**Goal:** Implement PoToken system to bypass YouTube bot detection and enable streaming without "Playability: not compatible" errors.

**Architecture:** Create a WebView-based PoToken generator that communicates with YouTube's BotGuard API to generate proof tokens for each streaming request.

**Tech Stack:** Kotlin, Android WebView, OkHttp, Coroutines

## Global Constraints

- Min SDK: 26 (WebView required)
- Package: `com.frito.music`
- PoToken files go in `app/src/main/java/com/frito/music/utils/potoken/`
- WebView must run on Main thread
- All network calls use existing OkHttp client

---

### Task 1: Create PoToken Data Classes

**Files:**
- Create: `app/src/main/java/com/frito/music/utils/potoken/PoTokenResult.kt`
- Create: `app/src/main/java/com/frito/music/utils/potoken/PoTokenException.kt`

**Interfaces:**
- Produces: `PoTokenResult(playerRequestPoToken, streamingDataPoToken)`
- Produces: `PoTokenException`, `BadWebViewException`

- [ ] **Step 1: Create PoTokenResult**

```kotlin
package com.frito.music.utils.potoken

class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)
```

- [ ] **Step 2: Create PoTokenException**

```kotlin
package com.frito.music.utils.potoken

class PoTokenException(message: String) : Exception(message)
class BadWebViewException(message: String) : Exception(message)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/utils/potoken/
git commit -m "feat: add PoToken data classes"
```

---

### Task 2: Create JavaScriptUtil

**Files:**
- Create: `app/src/main/java/com/frito/music/utils/potoken/JavaScriptUtil.kt`

**Interfaces:**
- Produces: `parseChallengeData()`, `parseIntegrityTokenData()`, `stringToU8()`, `u8ToBase64()`

- [ ] **Step 1: Create JavaScriptUtil with conversion functions**

```kotlin
package com.frito.music.utils.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.toByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object JavaScriptUtil {
    
    fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = Json.parseToJsonElement(rawChallengeData).jsonArray
        val challengeData = if (scrambled.size > 1 && scrambled[1].jsonPrimitive.isString) {
            val descrambled = descramble(scrambled[1].jsonPrimitive.content)
            Json.parseToJsonElement(descrambled).jsonArray
        } else {
            scrambled[0].jsonArray
        }
        
        return Json.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
            "messageId" to JsonPrimitive(challengeData[0].jsonPrimitive.content),
            "interpreterJavascript" to JsonObject(mapOf(
                "privateDoNotAccessOrElseSafeScriptWrappedValue" to JsonPrimitive(
                    challengeData[1].jsonObject["privateDoNotAccessOrElseSafeScriptWrappedValue"]?.jsonPrimitive?.content ?: ""
                ),
                "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to JsonPrimitive("")
            )),
            "interpreterHash" to JsonPrimitive(challengeData[3].jsonPrimitive.content),
            "program" to JsonPrimitive(challengeData[4].jsonPrimitive.content),
            "globalName" to JsonPrimitive(challengeData[5].jsonPrimitive.content),
            "clientExperimentsStateBlob" to JsonPrimitive(challengeData[7].jsonPrimitive.content),
        )))
    }
    
    fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
        val data = Json.parseToJsonElement(rawIntegrityTokenData).jsonArray
        return base64ToU8(data[0].jsonPrimitive.content) to data[1].jsonPrimitive.long
    }
    
    fun stringToU8(identifier: String): String {
        return "new Uint8Array([" + identifier.toByteArray().joinToString(",") { it.toUByte().toString() } + "])"
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    fun u8ToBase64(poToken: String): String {
        return poToken.split(",")
            .map { it.toUByte().toByte() }
            .toByteArray()
            .toByteString()
            .base64()
            .replace("+", "-")
            .replace("/", "_")
    }
    
    @OptIn(ExperimentalEncodingApi::class)
    private fun base64ToU8(base64: String): String {
        val bytes = Base64.decode(base64)
        return bytes.joinToString(",") { it.toUByte().toString() }
    }
    
    private fun descramble(scrambledChallenge: String): String {
        val bytes = Base64.decode(scrambledChallenge)
        return bytes.map { (it + 97).toByte() }
            .toByteArray()
            .decodeToString()
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/frito/music/utils/potoken/JavaScriptUtil.kt
git commit -m "feat: add JavaScriptUtil for PoToken conversions"
```

---

### Task 3: Create PoTokenWebView

**Files:**
- Create: `app/src/main/java/com/frito/music/utils/potoken/PoTokenWebView.kt`
- Create: `app/src/main/assets/po_token.html` (from Echo-Music)

**Interfaces:**
- Produces: `PoTokenWebView.getNewPoTokenGenerator(context)`
- Produces: `PoTokenWebView.generatePoToken(identifier)`

- [ ] **Step 1: Copy po_token.html from Echo-Music**

Copy `Echo-Music/app/src/main/assets/po_token.html` to `FritoMusic/app/src/main/assets/po_token.html`

- [ ] **Step 2: Create PoTokenWebView**

```kotlin
package com.frito.music.utils.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ConsoleMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("SetJavaScriptEnabled")
class PoTokenWebView private constructor(
    context: Context,
    private val continuation: Continuation<PoTokenWebView>,
) {
    private val webView = WebView(context)
    private val scope = MainScope()
    private val initResumed = AtomicBoolean(false)
    private val poTokenContinuations = java.util.Collections.synchronizedMap(
        mutableMapOf<Int, Continuation<String>>()
    )
    
    private val httpClient = OkHttpClient()
    
    var isExpired = false
        private set
    var isDead = false
        private set
    private var closed = false
    
    private var expirationInstant: java.time.Instant? = null
    
    companion object {
        private const val TAG = "PoTokenWebView"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val GENERATE_TIMEOUT_MS = 10_000L
        
        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val potWv = PoTokenWebView(context, cont)
                    potWv.loadHtmlAndObtainBotguard()
                }
            }
        }
    }
    
    init {
        val webViewSettings = webView.settings
        webViewSettings.javaScriptEnabled = true
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true
        
        webView.addJavascriptInterface(this, JS_INTERFACE)
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val msg = consoleMessage.message()
                if (msg.contains("Uncaught")) {
                    val exception = BadWebViewException(msg)
                    onInitializationErrorCloseAndCancel(exception)
                    popAllPoTokenContinuations().forEach { (_, cont) ->
                        cont.resumeWithException(exception)
                    }
                }
                return true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                isDead = true
                val exception = PoTokenException("WebView render process gone")
                onInitializationErrorCloseAndCancel(exception)
                popAllPoTokenContinuations().forEach { (_, cont) ->
                    cont.resumeWithException(exception)
                }
                return true
            }
        }
    }
    
    private fun loadHtmlAndObtainBotguard() {
        scope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    webView.context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                }
                
                val data = html.replaceFirst("</script>",
                    "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")
                webView.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }
    
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = JavaScriptUtil.parseChallengeData(responseBody)
            
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }
    
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            val (integrityToken, expirationTimeInSeconds) = JavaScriptUtil.parseIntegrityTokenData(responseBody)
            
            expirationInstant = java.time.Instant.now().plusSeconds(expirationTimeInSeconds)
                .minusSeconds(600) // 10 min margin
            
            webView.evaluateJavascript(
                """try {
                    this.integrityToken = $integrityToken
                    createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                        $JS_INTERFACE.onMinterCreated()
                    }).catch(function(error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ''))
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }
    
    @JavascriptInterface
    fun onMinterCreated() {
        if (initResumed.compareAndSet(false, true)) {
            continuation.resume(this@PoTokenWebView)
        }
    }
    
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        val exception = if (error.contains("SyntaxError")) {
            BadWebViewException(error)
        } else {
            PoTokenException(error)
        }
        onInitializationErrorCloseAndCancel(exception)
    }
    
    private fun onInitializationErrorCloseAndCancel(exception: Exception) {
        if (initResumed.compareAndSet(false, true)) {
            continuation.resumeWithException(exception)
        }
        close()
    }
    
    suspend fun generatePoToken(identifier: String): String {
        if (isDead || closed) throw PoTokenException("PoToken WebView is dead/closed")
        
        isExpired = expirationInstant?.let { java.time.Instant.now().isAfter(it) } ?: false
        if (isExpired) throw PoTokenException("PoToken WebView is expired")
        
        return try {
            withTimeout(GENERATE_TIMEOUT_MS) {
                generatePoTokenInternal(identifier)
            }
        } catch (e: TimeoutCancellationException) {
            isDead = true
            throw PoTokenException("poToken generation timed out")
        }
    }
    
    private suspend fun generatePoTokenInternal(identifier: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val reqId = getNextReqId()
                addPoTokenEmitter(reqId, cont)
                cont.invokeOnCancellation { popPoTokenContinuation(reqId) }
                
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        u8Identifier = ${JavaScriptUtil.stringToU8(identifier)}
                        obtainPoToken(u8Identifier).then(function(poTokenU8) {
                            poTokenU8String = poTokenU8.join(",")
                            $JS_INTERFACE.onObtainPoTokenResult($reqId, identifier, poTokenU8String)
                        }).catch(function(error) {
                            $JS_INTERFACE.onObtainPoTokenError($reqId, identifier, error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError($reqId, identifier, error + "\n" + error.stack)
                    }""",
                    null
                )
            }
        }
    }
    
    @JavascriptInterface
    fun onObtainPoTokenResult(reqId: Int, identifier: String, poTokenU8: String) {
        val poToken = JavaScriptUtil.u8ToBase64(poTokenU8)
        Log.d(TAG, "Generated poToken for $identifier: ${poToken.take(20)}...")
        popPoTokenContinuation(reqId)?.resume(poToken)
    }
    
    @JavascriptInterface
    fun onObtainPoTokenError(reqId: Int, identifier: String, error: String) {
        Log.e(TAG, "Error generating poToken for $identifier: $error")
        popPoTokenContinuation(reqId)?.resumeWithException(PoTokenException(error))
    }
    
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val requestBuilder = Request.Builder()
                    .post(data.toRequestBody())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json+protobuf")
                    .header("x-goog-api-key", GOOGLE_API_KEY)
                    .header("x-user-agent", "grpc-web-javascript/0.1")
                    .url(url)
                
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute()
                }
                
                if (response.isSuccessful) {
                    handleResponseBody(response.body.string())
                } else {
                    onInitializationErrorCloseAndCancel(
                        PoTokenException("BotGuard request failed: ${response.code}")
                    )
                }
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }
    
    private val reqIdCounter = AtomicInteger(0)
    
    private fun getNextReqId(): Int = reqIdCounter.incrementAndGet()
    
    private fun addPoTokenEmitter(reqId: Int, cont: Continuation<String>) {
        poTokenContinuations[reqId] = cont
    }
    
    private fun popPoTokenContinuation(reqId: Int): Continuation<String>? {
        return poTokenContinuations.remove(reqId)
    }
    
    private fun popAllPoTokenContinuations(): Map<Int, Continuation<String>> {
        val copy = poTokenContinuations.toMap()
        poTokenContinuations.clear()
        return copy
    }
    
    fun close() {
        closed = true
        scope.cancel()
        webView.destroy()
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/utils/potoken/PoTokenWebView.kt
git add app/src/main/assets/po_token.html
git commit -m "feat: add PoTokenWebView for token generation"
```

---

### Task 4: Create PoTokenGenerator

**Files:**
- Create: `app/src/main/java/com/frito/music/utils/potoken/PoTokenGenerator.kt`

**Interfaces:**
- Produces: `PoTokenGenerator.getWebClientPoToken(videoId, sessionId)`

- [ ] **Step 1: Create PoTokenGenerator**

```kotlin
package com.frito.music.utils.potoken

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PoTokenGenerator {
    private val webViewSupported by lazy { 
        runCatching { android.webkit.CookieManager.getInstance() }.isSuccess 
    }
    private var webViewBadImpl = false
    
    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null
    
    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) return null
        
        return try {
            runBlocking { getWebClientPoToken(videoId, sessionId, forceRecreate = false) }
        } catch (e: Exception) {
            when (e) {
                is BadWebViewException -> {
                    webViewBadImpl = true
                    null
                }
                else -> throw e
            }
        }
    }
    
    private suspend fun getWebClientPoToken(
        videoId: String, 
        sessionId: String, 
        forceRecreate: Boolean
    ): PoTokenResult {
        val context = com.frito.music.utils.potoken.CipherDeobfuscator.appContext
        
        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                        webPoTokenGenerator!!.isDead ||
                        webPoTokenSessionId != sessionId
                
                if (shouldRecreate) {
                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }
                    
                    val newGenerator = PoTokenWebView.getNewPoTokenGenerator(context)
                    
                    val newStreamingPot = try {
                        newGenerator.generatePoToken(sessionId)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { newGenerator.close() }
                        throw e
                    }
                    
                    webPoTokenSessionId = sessionId
                    webPoTokenGenerator = newGenerator
                    webPoTokenStreamingPot = newStreamingPot
                }
                
                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }
        
        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                throw throwable
            } else {
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }
        
        return PoTokenResult(playerPot, streamingPot)
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/frito/music/utils/potoken/PoTokenGenerator.kt
git commit -m "feat: add PoTokenGenerator orchestrator"
```

---

### Task 5: Integrate PoToken into Streaming

**Files:**
- Modify: `app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt`
- Modify: `innertube/src/main/kotlin/com/music/innertube/models/body/PlayerBody.kt`

**Interfaces:**
- Produces: `YouTubeRepository.getStreamUrl()` uses PoToken
- Produces: `PlayerBody` includes `serviceIntegrityDimensions`

- [ ] **Step 1: Add PoTokenGenerator to YouTubeRepository**

```kotlin
private val poTokenGenerator = PoTokenGenerator()
```

- [ ] **Step 2: Update getStreamUrl to use PoToken**

```kotlin
suspend fun getStreamUrl(videoId: String): Result<String> = runCatching {
    var lastException: Exception? = null
    val isLoggedIn = YouTube.cookie != null
    val clients = if (isLoggedIn) AUTHENTICATED_CLIENTS else ANONYMOUS_CLIENTS
    val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
    
    for (client in clients) {
        try {
            // Generate PoToken if client needs it
            var poToken: PoTokenResult? = null
            if (client.useWebPoTokens && sessionId != null) {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            }
            
            val playerResponse = YouTube.player(
                videoId, null, client,
                poToken = poToken?.playerRequestPoToken
            ).getOrThrow()
            
            // ... rest of existing code ...
            
            // If we got a stream URL and client uses PoTokens, add pot= parameter
            if (streamUrl != null && client.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                val separator = if ("?" in streamUrl) "&" else "?"
                streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
            }
            
            if (streamUrl != null) return@runCatching streamUrl
        } catch (e: Exception) {
            lastException = e
            continue
        }
    }
    
    throw lastException ?: Exception("Could not resolve stream URL")
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/data/network/yt/YouTubeRepository.kt
git commit -m "feat: integrate PoToken into streaming pipeline"
```

---

### Task 6: Final Verification

**Files:**
- Test: Manual testing

- [ ] **Step 1: Build the app**

Run: `./gradlew assembleDebug`

- [ ] **Step 2: Test streaming**

1. Open app
2. Go to Stream
3. Search for a song
4. Play the song
5. Verify no "Playability: not compatible" error

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "feat: complete PoToken integration"
```
