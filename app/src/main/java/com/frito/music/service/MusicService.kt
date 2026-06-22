package com.frito.music.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.media3.common.util.UnstableApi
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Maneja el foco de audio (pausa en llamadas, etc)
            )
            .setHandleAudioBecomingNoisy(true) // Pausa al desconectar auriculares
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
        super.onDestroy()
    }
}
