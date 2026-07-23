package com.frito.music.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.frito.music.extensions.session.SignedSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pantalla de verificación de sesión firmada (signedSession@1) dentro de la app.
 * Carga el challenge (Cloudflare Turnstile) en un WebView e intercepta el redirect
 * spotiflac://session-grant?...&state={extId}&grant={grant} antes de que salga al
 * sistema, capturando el grant de forma determinística (sin Chrome ni selector de apps).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionVerificationScreen(
    extensionId: String,
    authUrl: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Completa la verificación en la página…") }
    var finishing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verificar sesión", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = status,
                color = if (finishing) Color(0xFFA5D6A7) else Color(0xFFFFD54F),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val uri = request.url
                                if (uri.scheme == "spotiflac" && uri.host == "session-grant") {
                                    val extId = uri.getQueryParameter("state") ?: extensionId
                                    val grant = uri.getQueryParameter("grant")
                                    if (!grant.isNullOrEmpty() && !finishing) {
                                        finishing = true
                                        status = "Verificación recibida, activando sesión…"
                                        SignedSessionManager.setPendingGrant(ctx, extId, grant)
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    JSONObject(
                                                        SignedSessionManager(ctx, extId).completeGrant(grant)
                                                    ).optBoolean("success")
                                                }.getOrDefault(false)
                                            }
                                            if (ok) {
                                                status = "¡Sesión verificada!"
                                                delay(900)
                                                onCompleted()
                                            } else {
                                                status = "No se pudo activar la sesión. Inténtalo de nuevo."
                                                finishing = false
                                            }
                                        }
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(authUrl)
                    }
                }
            )
        }
    }
}
