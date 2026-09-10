package com.frito.music.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

@androidx.media3.common.util.UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var cache: SimpleCache? = null

    /**
     * Cliente OkHttp del reproductor. Un INTERCEPTOR aplica a cada petición de
     * host YouTube (googlevideo, etc.) el User-Agent/Origin/Referer EXACTOS del
     * cliente que emitió la URL (leídos de sus parámetros c=/cver=).
     *
     * IMPORTANTE: se usa OkHttpDataSource y NO DefaultHttpDataSource porque el
     * de media3 1.2.1 IGNORA los headers del DataSpec — esa era la causa real
     * del HTTP 403 al reproducir (la URL validaba 206 con headers, pero
     * ExoPlayer la pedía sin ellos).
     */
    private val mediaOkHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (!com.frito.music.data.network.yt.StreamClientUtils.isYouTubeMediaHost(host)) {
                    return@addInterceptor chain.proceed(request)
                }
                val profile = com.frito.music.data.network.yt.StreamClientUtils
                    .resolveRequestProfile(request.url.toString())
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

    /**
     * Ajusta el DataSpec de los streams de YouTube ANTES de llegar a la red:
     * si la petición no tiene longitud (apertura inicial o seek), la limita al
     * tamaño real del recurso (clen de la URL). Sin esto OkHttpDataSource pide
     * la URL SIN cabecera Range y googlevideo responde 403 (mientras las
     * validaciones con Range: bytes=0-0 sí pasan — el misterio del 403).
     * Con el límite, el request lleva Range: bytes=<pos>-<fin real>, que es lo
     * que hace FridaMusic y lo que YouTube sirve por su camino rápido.
     */
    private fun boundRemoteStreamLength(
        dataSpec: androidx.media3.datasource.DataSpec
    ): androidx.media3.datasource.DataSpec {
        if (dataSpec.length >= 0) return dataSpec
        val host = dataSpec.uri.host ?: return dataSpec
        if (!com.frito.music.data.network.yt.StreamClientUtils.isYouTubeMediaHost(host)) return dataSpec
        val totalLength = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }
            ?: return dataSpec
        val remaining = totalLength - dataSpec.position
        if (remaining <= 0) return dataSpec
        android.util.Log.d(
            "RemotePlayback",
            "bound length=$remaining pos=${dataSpec.position} host=$host"
        )
        return dataSpec.buildUpon().setLength(remaining).build()
    }

    private fun createUpstreamDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val okHttpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaOkHttpClient)
        return DefaultDataSource.Factory(this, okHttpFactory)
    }

    /**
     * Política de errores de carga: un HTTP 401/403/404 es definitivo (URL
     * caducada o bloqueada por bot-detection). Reintentarlo en bucle causaba el
     * parpadeo 0:00 ↔ duración y el buffering infinito. Devolver C.TIME_UNSET
     * corta los reintentos; PlayerViewModel entonces invalida la URL cacheada y
     * re-resuelve con otra estrategia (o salta de canción).
     */
    private fun createLoadErrorPolicy(): androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy =
        object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(
                loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val e = loadErrorInfo.exception
                if (e is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
                    (e.responseCode == 401 || e.responseCode == 403 || e.responseCode == 404)
                ) {
                    return C.TIME_UNSET
                }
                return super.getRetryDelayMsFor(loadErrorInfo)
            }
        }

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val defaultDataSource = createUpstreamDataSourceFactory()

        val cachedDataSource: androidx.media3.datasource.DataSource.Factory = cache?.let { cacheInstance ->
            CacheDataSource.Factory()
                .setCache(cacheInstance)
                .setUpstreamDataSourceFactory(defaultDataSource)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // Clave de caché estable: el videoId en vez de la URL completa
                // (las URLs de YouTube expiran; con videoId la caché sirve entre sesiones)
                .setCacheKeyFactory { dataSpec ->
                    val customKey = dataSpec.key
                    when {
                        !customKey.isNullOrEmpty() -> {
                            if (customKey.startsWith("video_")) customKey else "video_$customKey"
                        }
                        else -> {
                            val url = dataSpec.uri.toString()
                            val vParam = Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
                            if (!vParam.isNullOrEmpty()) "video_$vParam" else url
                        }
                    }
                }
        } ?: defaultDataSource

        // El ajuste de longitud debe ocurrir ANTES de la caché y de la red:
        // CacheDataSource/OkHttpDataSource calcularán el Range real a partir del
        // DataSpec ya acotado (mismo patrón que FridaMusic).
        return androidx.media3.datasource.ResolvingDataSource.Factory(cachedDataSource) { dataSpec ->
            boundRemoteStreamLength(dataSpec)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize cache (con protección: si el directorio está bloqueado por
        // otra instancia, no crashear el servicio)
        cache = try {
            val cacheDir = File(cacheDir, "stream-cache")
            val evictor = LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L) // 500MB
            val databaseProvider = StandaloneDatabaseProvider(this)
            SimpleCache(cacheDir, evictor, databaseProvider)
        } catch (e: Exception) {
            android.util.Log.w("MusicService", "No se pudo iniciar la caché de streams", e)
            null
        }
        // Exponer la caché para que el manejo de errores pueda limpiar entradas corruptas
        PlayerCacheHolder.cache = cache
        
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,  // minBufferMs (15s antes de pausar buffering)
                50_000,  // maxBufferMs (hasta 50s de buffer)
                500,     // bufferForPlaybackMs (inicio instantáneo: 500ms requeridos)
                1_000    // bufferForPlaybackAfterRebufferMs (1s tras re-buffer)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // La política de errores vive en el MediaSourceFactory (no en ExoPlayer.Builder
        // en media3 1.2.1)
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())
            .setLoadErrorHandlingPolicy(createLoadErrorPolicy())

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Maneja el foco de audio (pausa en llamadas, etc)
            )
            .setHandleAudioBecomingNoisy(true) // Pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT) // Búsqueda exacta para evitar tartamudeo al soltar la barra
            .build()
            
        mediaSession = MediaSession.Builder(this, player).build()

        val eqManager = com.frito.music.audio.AudioEffectManagerProvider.getManager(this)
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                eqManager.attachAudioSession(audioSessionId)
            }
        })
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            eqManager.attachAudioSession(player.audioSessionId)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        PlayerCacheHolder.cache = null
        cache?.release()
        cache = null
        super.onDestroy()
    }
}
