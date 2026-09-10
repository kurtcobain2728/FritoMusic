package com.frito.music.data.network.yt

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Cliente OkHttp ÚNICO del reproductor y de las descargas de YouTube.
 *
 * El INTERCEPTOR aplica a cada petición a un host de YouTube (googlevideo,
 * etc.) el User-Agent/Origin/Referer EXACTOS del cliente que emitió la URL
 * (leídos de sus parámetros c=/cver=).
 *
 * Compartir la MISMA instancia entre el reproductor (OkHttpDataSource) y el
 * worker de descargas garantiza requests idénticos por construcción: si el
 * reproductor puede pedir la URL, la descarga la pide exactamente igual.
 * (Un OkHttp "a mano" en el worker daba 403 aunque los headers parecieran
 * iguales.)
 */
object PlaybackHttpClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (!StreamClientUtils.isYouTubeMediaHost(host)) {
                    return@addInterceptor chain.proceed(request)
                }
                val profile = StreamClientUtils.resolveRequestProfile(request.url.toString())
                android.util.Log.i(
                    "RemotePlayback",
                    "open host=$host client=${profile.requestedClientName}@${profile.requestedClientVersion} range=${request.header("Range") ?: "none"}"
                )
                val profiled = request.newBuilder().apply {
                    header("User-Agent", profile.userAgent)
                    profile.origin?.let { header("Origin", it) } ?: removeHeader("Origin")
                    profile.referer?.let { header("Referer", it) } ?: removeHeader("Referer")
                }.build()
                val response = chain.proceed(profiled)
                if (!response.isSuccessful) {
                    // Diagnóstico: googlevideo explica el motivo en el body del error
                    val snippet = runCatching { response.peekBody(1024).string() }.getOrNull()
                    android.util.Log.w(
                        "RemotePlayback",
                        "HTTP ${response.code} host=$host clen=${profiled.url.queryParameter("clen")} dur=${profiled.url.queryParameter("dur")} urlRange=${profiled.url.queryParameter("range")} rn=${profiled.url.queryParameter("rn")} pot=${profiled.url.queryParameter("pot") != null} range=${profiled.header("Range") ?: "none"} body=${snippet?.replace('\n', ' ')?.take(300)}"
                    )
                }
                response
            }
            .build()
    }
}