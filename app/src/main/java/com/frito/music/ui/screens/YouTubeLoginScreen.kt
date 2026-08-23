package com.frito.music.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.frito.music.data.repository.YouTubeLoginManager
import com.music.innertube.YouTube
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var hasCompletedLogin by remember { mutableStateOf(false) }
    var loginStatus by remember { mutableStateOf("Cargando...") }

    var webView: WebView? = null

    // Destruir el WebView al salir de la pantalla (antes quedaba vivo en memoria)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                runCatching { stopLoading() }
                runCatching { loadUrl("about:blank") }
                runCatching { destroy() }
            }
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iniciar sesión en YouTube", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            // Extract visitorData and dataSyncId
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                            // Check if we're on YouTube Music (login successful)
                            if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                                val cookie = CookieManager.getInstance().getCookie(url)
                                if (cookie != null && cookie.contains("SAPISID")) {
                                    hasCompletedLogin = true
                                    loginStatus = "Login exitoso, guardando..."

                                    coroutineScope.launch {
                                        delay(500)

                                        // Save login data
                                        YouTubeLoginManager.saveLogin(
                                            cookie = cookie,
                                            visitorData = YouTubeLoginManager.getVisitorData(),
                                            dataSyncId = YouTubeLoginManager.getDataSyncId()
                                        )

                                        // Configure YouTube
                                        YouTube.cookie = cookie
                                        YouTube.dataSyncId = YouTubeLoginManager.getDataSyncId()
                                        YouTube.visitorData = YouTubeLoginManager.getVisitorData()

                                        // Validate login
                                        YouTube.accountInfo().onSuccess { accountInfo ->
                                            YouTubeLoginManager.saveAccountInfo(
                                                name = accountInfo.name,
                                                email = accountInfo.email ?: "",
                                                handle = accountInfo.channelHandle ?: ""
                                            )
                                            loginStatus = "¡Bienvenido ${accountInfo.name}!"
                                            onLoginSuccess()
                                        }.onFailure {
                                            loginStatus = "Error validando: ${it.message}"
                                            hasCompletedLogin = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            if (newVisitorData != null) {
                                YouTubeLoginManager.saveVisitorData(newVisitorData)
                            }
                        }

                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncId: String?) {
                            if (newDataSyncId != null) {
                                YouTubeLoginManager.saveDataSyncId(newDataSyncId.substringBefore("||"))
                            }
                        }
                    }, "Android")
                    webView = this
                    loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                }
            }
        )
    }
}
