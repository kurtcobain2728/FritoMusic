package com.frito.music.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.frito.music.data.repository.YouTubeLoginManager
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(
    emailHint: String? = null,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var hasCompletedLogin by remember { mutableStateOf(false) }
    var loginStatus by remember { mutableStateOf(if (!emailHint.isNullOrEmpty()) "Conectando con $emailHint..." else "Cargando...") }
    var retrievedVisitorData by remember { mutableStateOf("") }
    var retrievedDataSyncId by remember { mutableStateOf("") }

    var webView: WebView? = null

    // Destruir el WebView al salir de la pantalla (antes quedaba vivo en memoria)
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                runCatching { stopLoading() }
                runCatching { loadUrl("about:blank") }
                runCatching { destroy() }
            }
            webView = null
        }
    }

    val targetUrl = remember(emailHint) {
        if (!emailHint.isNullOrEmpty()) {
            val encodedEmail = java.net.URLEncoder.encode(emailHint, "UTF-8")
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&login_hint=$encodedEmail&Email=$encodedEmail&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Des%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
        } else {
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Des%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (!emailHint.isNullOrEmpty()) "Acceder como $emailHint" else "Iniciar sesión", 
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        fun checkAndProcessLogin(currentUrl: String?) {
                            if (hasCompletedLogin) return
                            if (currentUrl != null && (currentUrl.startsWith("https://music.youtube.com") || currentUrl.startsWith("http://music.youtube.com"))) {
                                cookieManager.flush()
                                val cookie = cookieManager.getCookie("https://music.youtube.com") ?: cookieManager.getCookie(currentUrl)
                                if (cookie != null && cookie.contains("SAPISID")) {
                                    hasCompletedLogin = true
                                    loginStatus = "¡Cuenta detectada! Conectando..."

                                    // Extraer VISITOR_DATA y DATASYNC_ID desde el config de YouTube Music
                                    loadUrl("""
                                        javascript:(function() {
                                            try {
                                                var v = (window.yt && window.yt.config_) ? (window.yt.config_.VISITOR_DATA || '') : '';
                                                var d = (window.yt && window.yt.config_) ? (window.yt.config_.DATASYNC_ID || '') : '';
                                                Android.onRetrieveConfig(v, d);
                                            } catch(e){}
                                        })();
                                    """.trimIndent())

                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        delay(800)
                                        cookieManager.flush()
                                        val freshCookie = cookieManager.getCookie("https://music.youtube.com") ?: cookie

                                        val vData = retrievedVisitorData.ifBlank { YouTubeLoginManager.getVisitorData() }
                                        val dSyncId = retrievedDataSyncId.ifBlank { YouTubeLoginManager.getDataSyncId() }

                                        YouTubeLoginManager.saveLogin(
                                            cookie = freshCookie,
                                            visitorData = vData,
                                            dataSyncId = dSyncId
                                        )

                                        YouTube.cookie = freshCookie
                                        YouTube.dataSyncId = dSyncId.ifBlank { null }
                                        YouTube.visitorData = vData.ifBlank { null }

                                        // Validar llamada real a InnerTube y obtener foto de perfil y nombre
                                        var accountResult = YouTube.accountInfo()
                                        if (accountResult.isFailure) {
                                            delay(1200)
                                            cookieManager.flush()
                                            val retryCookie = cookieManager.getCookie("https://music.youtube.com") ?: freshCookie
                                            YouTube.cookie = retryCookie
                                            accountResult = YouTube.accountInfo()
                                        }

                                        accountResult.onSuccess { accountInfo ->
                                            YouTubeLoginManager.saveAccountInfo(
                                                name = accountInfo.name,
                                                email = accountInfo.email ?: "",
                                                handle = accountInfo.channelHandle ?: "",
                                                avatarUrl = accountInfo.thumbnailUrl ?: ""
                                            )
                                            loginStatus = "¡Bienvenido ${accountInfo.name}!"
                                            delay(600)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                onLoginSuccess()
                                            }
                                        }.onFailure {
                                            loginStatus = "Sesión iniciada"
                                            delay(600)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                onLoginSuccess()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null && (url.startsWith("https://music.youtube.com") || url.startsWith("http://music.youtube.com"))) {
                                    loadUrl("""
                                        javascript:(function() {
                                            try {
                                                var v = (window.yt && window.yt.config_) ? (window.yt.config_.VISITOR_DATA || '') : '';
                                                var d = (window.yt && window.yt.config_) ? (window.yt.config_.DATASYNC_ID || '') : '';
                                                Android.onRetrieveConfig(v, d);
                                            } catch(e){}
                                        })();
                                    """.trimIndent())
                                    checkAndProcessLogin(url)
                                }
                            }
                        }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onRetrieveConfig(newVisitorData: String?, newDataSyncId: String?) {
                                if (!newVisitorData.isNullOrBlank()) {
                                    retrievedVisitorData = newVisitorData
                                    YouTubeLoginManager.saveVisitorData(newVisitorData)
                                }
                                if (!newDataSyncId.isNullOrBlank()) {
                                    val cleanId = newDataSyncId.substringBefore("||")
                                    retrievedDataSyncId = cleanId
                                    YouTubeLoginManager.saveDataSyncId(cleanId)
                                }
                            }

                            @JavascriptInterface
                            fun onRetrieveVisitorData(newVisitorData: String?) {
                                if (!newVisitorData.isNullOrBlank()) {
                                    retrievedVisitorData = newVisitorData
                                    YouTubeLoginManager.saveVisitorData(newVisitorData)
                                }
                            }

                            @JavascriptInterface
                            fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                if (!newDataSyncId.isNullOrBlank()) {
                                    val cleanId = newDataSyncId.substringBefore("||")
                                    retrievedDataSyncId = cleanId
                                    YouTubeLoginManager.saveDataSyncId(cleanId)
                                }
                            }
                        }, "Android")

                        webView = this
                        loadUrl(targetUrl)
                    }
                }
            )

            // Overlay elegante mientras se procesa la conexión
            if (hasCompletedLogin) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = loginStatus,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
