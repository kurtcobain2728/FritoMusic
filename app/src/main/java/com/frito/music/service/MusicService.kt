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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var cache: SimpleCache? = null

    /**
     * Cliente OkHttp del reproductor y de las descargas: vive en
     * PlaybackHttpClient (cliente ÚNICO compartido). Su INTERCEPTOR aplica a
     * cada petición de host YouTube (googlevideo, etc.) el User-Agent/Origin/
     * Referer EXACTOS del cliente que emitió la URL (leídos de sus parámetros
     * c=/cver=).
     *
     * IMPORTANTE: se usa OkHttpDataSource y NO DefaultHttpDataSource porque el
     * de media3 1.2.1 IGNORA los headers del DataSpec — esa era la causa real
     * del HTTP 403 al reproducir (la URL validaba 206 con headers, pero
     * ExoPlayer la pedía sin ellos).
     */
    private val mediaOkHttpClient: okhttp3.OkHttpClient
        get() = com.frito.music.data.network.yt.PlaybackHttpClient.client

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
        
        // Initialize cache con límite configurado por el usuario (mín 256MB, default 1GB, máx 8GB)
        cache = try {
            val cacheDir = File(cacheDir, "stream-cache")
            val cacheLimit = com.frito.music.data.preferences.StorageCacheManager.getMusicCacheLimitBytes(this)
            val evictor = LeastRecentlyUsedCacheEvictor(cacheLimit)
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
                if (isPlaying) {
                    startCrossfadeMonitoring(player)
                } else {
                    // Solo cancelamos si la pausa fue por intención del usuario (playWhenReady == false)
                    // y no por un buffering/handover momentáneo
                    if (!player.playWhenReady && !isPerformingHandover) {
                        crossfadeJob?.cancel()
                        cancelCrossfade(player)
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateWidget()
            }
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK && !isPerformingHandover) {
                    cancelCrossfade(player)
                }
            }
        })
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            eqManager.attachAudioSession(player.audioSessionId)
        }
    }

    // ── Motor de Crossfade real con superposición de audio (Dual-Player) ──
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var crossfadeJob: kotlinx.coroutines.Job? = null
    private var fadePlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var isPerformingHandover = false
    private var preloadedMediaId: String? = null

    private fun getOrCreateFadePlayer(): ExoPlayer {
        fadePlayer?.let { return it }
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,
                30_000,
                500,
                1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val fp = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // No roba el foco de audio
            )
            .build()
        fadePlayer = fp
        return fp
    }

    private fun startCrossfadeMonitoring(player: ExoPlayer) {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(200)
                if (!player.isPlaying || isCrossfading || isPerformingHandover) continue

                val isEnabled = com.frito.music.data.preferences.CrossfadePreferences.isEnabled(this@MusicService)
                if (!isEnabled) {
                    if (player.volume < 1f) player.volume = 1f
                    continue
                }

                val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                val isOnline = currentUri.startsWith("http://") || currentUri.startsWith("https://")
                val isApplicable = if (isOnline) {
                    com.frito.music.data.preferences.CrossfadePreferences.isApplyToOnline(this@MusicService)
                } else {
                    com.frito.music.data.preferences.CrossfadePreferences.isApplyToOffline(this@MusicService)
                }

                if (!isApplicable) {
                    if (player.volume < 1f) player.volume = 1f
                    continue
                }

                val duration = player.duration
                val position = player.currentPosition
                if (duration <= 0 || position <= 0) continue

                val fadeSeconds = com.frito.music.data.preferences.CrossfadePreferences.getDurationSeconds(this@MusicService)
                val fadeMs = fadeSeconds * 1000L

                val remainingMs = duration - position
                val nextIndex = player.nextMediaItemIndex
                if (nextIndex == C.INDEX_UNSET) {
                    // No hay siguiente pista: al final de la playlist hacemos fade-out suave
                    if (remainingMs in 100L..fadeMs) {
                        val progress = (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
                        player.volume = progress
                    }
                    continue
                }

                // 1. Pre-buffering anticipado de la siguiente canción (10s antes del corte)
                val preloadThresholdMs = fadeMs + 10_000L
                if (remainingMs <= preloadThresholdMs) {
                    val nextItem = player.getMediaItemAt(nextIndex)
                    if (preloadedMediaId != nextItem.mediaId) {
                        try {
                            val fp = getOrCreateFadePlayer()
                            fp.stop()
                            fp.clearMediaItems()
                            fp.volume = 0f
                            fp.setMediaItem(nextItem)
                            fp.prepare()
                            fp.pause()
                            preloadedMediaId = nextItem.mediaId
                            android.util.Log.d("MusicService", "Crossfade: Pre-bufferizado ${nextItem.mediaMetadata.title}")
                        } catch (e: Exception) {
                            android.util.Log.w("MusicService", "Error al pre-bufferizar pista de crossfade", e)
                        }
                    }
                }

                // 2. Disparo de la mezcla cruzada
                if (remainingMs in 300L..fadeMs) {
                    triggerOverlappingCrossfade(player, remainingMs.coerceAtMost(fadeMs), nextIndex)
                }
            }
        }
    }

    private fun triggerOverlappingCrossfade(player: ExoPlayer, effectiveFadeMs: Long, nextIndex: Int) {
        if (isCrossfading || isPerformingHandover || !player.isPlaying) return
        val nextItem = player.getMediaItemAt(nextIndex)

        val fp = getOrCreateFadePlayer()
        if (preloadedMediaId != nextItem.mediaId) {
            fp.stop()
            fp.clearMediaItems()
            fp.volume = 0f
            fp.setMediaItem(nextItem)
            fp.prepare()
            preloadedMediaId = nextItem.mediaId
        }

        isCrossfading = true
        android.util.Log.i(
            "MusicService",
            "Iniciando Crossfade superpuesto (${effectiveFadeMs}ms): ${player.currentMediaItem?.mediaMetadata?.title} -> ${nextItem.mediaMetadata.title}"
        )

        serviceScope.launch {
            // Si fadePlayer aún está conectando o bufferizando, esperamos hasta 800ms
            var waitBuf = 0
            while (waitBuf < 16 && fp.playbackState != androidx.media3.common.Player.STATE_READY) {
                kotlinx.coroutines.delay(50)
                waitBuf++
            }

            if (!isCrossfading || !player.isPlaying) {
                cancelCrossfade(player)
                return@launch
            }

            // Iniciamos la pista entrante en fadePlayer a volumen 0
            fp.volume = 0f
            fp.play()

            val stepMs = 40L
            val steps = (effectiveFadeMs / stepMs).coerceAtLeast(1)

            // Curva Equal-Power (seno/coseno): la potencia acústica total permanece constante al 100%
            for (i in 1..steps) {
                kotlinx.coroutines.delay(stepMs)
                if (!isCrossfading || !player.isPlaying) break
                val progress = i.toFloat() / steps // 0.0 .. 1.0
                val angle = progress * (Math.PI / 2.0)

                // Track saliente (player): cos(0 -> pi/2) = 1.0 -> 0.0
                val outVol = kotlin.math.cos(angle).toFloat().coerceIn(0f, 1f)
                // Track entrante (fp): sin(0 -> pi/2) = 0.0 -> 1.0
                val inVol = kotlin.math.sin(angle).toFloat().coerceIn(0f, 1f)

                player.volume = outVol
                fp.volume = inVol
            }

            if (!isCrossfading || !player.isPlaying) {
                cancelCrossfade(player)
                return@launch
            }

            // Handover suave: la pista entrante ya está sonando en fp a volumen 1.0
            val handoffPositionMs = fp.currentPosition
            android.util.Log.d("MusicService", "Crossfade: Handover a player en ${handoffPositionMs}ms")

            isPerformingHandover = true
            try {
                // Avanzamos player al siguiente MediaItem silenciosamente en la misma posición
                player.volume = 0f
                player.seekTo(nextIndex, handoffPositionMs)
                player.play()

                // Esperamos que player esté listo antes de apagar fp (para que nunca haya silencio)
                var waitReady = 0
                while (waitReady < 25 && player.playbackState != androidx.media3.common.Player.STATE_READY) {
                    kotlinx.coroutines.delay(50)
                    waitReady++
                }

                // Volumen completo a player y liberación de fadePlayer
                player.volume = 1f
                fp.stop()
                fp.clearMediaItems()
                preloadedMediaId = null
            } finally {
                isPerformingHandover = false
                isCrossfading = false
            }
            android.util.Log.i("MusicService", "Crossfade superpuesto completado exitosamente")
        }
    }

    private fun cancelCrossfade(player: ExoPlayer) {
        if (isCrossfading || preloadedMediaId != null) {
            isCrossfading = false
            preloadedMediaId = null
            fadePlayer?.stop()
            fadePlayer?.clearMediaItems()
            player.volume = 1f
        }
    }

    private fun updateWidget() {
        val player = mediaSession?.player ?: return
        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString()
        val artist = currentItem?.mediaMetadata?.artist?.toString()
        val artworkUri = currentItem?.mediaMetadata?.artworkUri?.toString()
        com.frito.music.widget.FritoMusicWidgetProvider.notifyWidgetUpdate(
            context = this,
            title = title,
            artist = artist,
            isPlaying = player.isPlaying,
            albumUri = artworkUri
        )
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            com.frito.music.widget.FritoMusicWidgetProvider.KEY_PLAY_PAUSE -> {
                mediaSession?.player?.let { p ->
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
            com.frito.music.widget.FritoMusicWidgetProvider.KEY_NEXT -> {
                mediaSession?.player?.seekToNextMediaItem()
            }
            com.frito.music.widget.FritoMusicWidgetProvider.KEY_PREV -> {
                mediaSession?.player?.seekToPreviousMediaItem()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        crossfadeJob?.cancel()
        serviceScope.cancel()
        fadePlayer?.release()
        fadePlayer = null
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
