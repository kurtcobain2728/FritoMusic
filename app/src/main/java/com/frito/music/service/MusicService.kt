package com.frito.music.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val defaultDataSource = DefaultDataSource.Factory(this)
        
        return cache?.let { cacheInstance ->
            CacheDataSource.Factory()
                .setCache(cacheInstance)
                .setUpstreamDataSourceFactory(defaultDataSource)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } ?: defaultDataSource
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize cache
        val cacheDir = File(cacheDir, "stream-cache")
        val evictor = LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L) // 500MB
        cache = SimpleCache(cacheDir, evictor)
        
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,  // minBufferMs
                30_000,  // maxBufferMs
                700,     // bufferForPlaybackMs (default 2500ms -> arranque más rápido)
                1_500    // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(createDataSourceFactory())
            )
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
        cache?.release()
        cache = null
        super.onDestroy()
    }
}
