package com.frito.music.utils.potoken

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) return null

        return try {
            getWebClientPoToken(videoId, sessionId, forceRecreate = false)
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
        val context = appContext
            ?: throw PoTokenException("PoTokenGenerator not initialized. Call init(context) first.")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                        webPoTokenGenerator!!.isDead ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                        webPoTokenGenerator = null
                    }

                    val newGenerator: PoTokenWebView
                    val newStreamingPot: String
                    try {
                        newGenerator = PoTokenWebView.getNewPoTokenGenerator(context)
                        try {
                            newStreamingPot = newGenerator.generatePoToken(sessionId)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { runCatching { newGenerator.close() } }
                            throw e
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
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

    companion object {
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}
