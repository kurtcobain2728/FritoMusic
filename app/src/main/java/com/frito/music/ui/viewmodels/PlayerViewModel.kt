package com.frito.music.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frito.music.data.models.AudioFile
import com.frito.music.data.repository.FavoritesRepository
import com.frito.music.data.repository.PlaylistRepository
import com.frito.music.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var mediaController: MediaController? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentAudio = MutableStateFlow<AudioFile?>(null)
    val currentAudio = _currentAudio.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()
    
    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val audioFilesMap = mutableMapOf<String, AudioFile>()

    private val favoritesRepository = FavoritesRepository(application)
    val favorites = favoritesRepository.favorites
    
    private val playlistRepository = PlaylistRepository(application)
    val playlists = playlistRepository.playlists
    
    val equalizerManager = com.frito.music.audio.AudioEffectManagerProvider.getManager(application)
    
    val isCurrentFavorite = combine(currentAudio, favoritesRepository.favorites) { audio, favs ->
        audio?.path?.let { favs.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        initializeController()
        startProgressUpdater()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )
        val controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.let { id ->
                        _currentAudio.value = audioFilesMap[id]
                    }
                    _durationMs.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _durationMs.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }
            })
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    mediaController?.let { controller ->
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val dur = controller.duration.coerceAtLeast(1L)
                        _positionMs.value = pos
                        _progress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                }
                delay(100L)
            }
        }
    }

    fun playAudios(audios: List<AudioFile>, startIndex: Int) {
        val controller = mediaController ?: return
        
        audioFilesMap.clear()
        val mediaItems = audios.map { audio ->
            audioFilesMap[audio.id.toString()] = audio
            
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(audio.title)
                .setArtist(audio.artist)
                
            if (audio.albumUri != null) {
                metadataBuilder.setArtworkUri(Uri.parse(audio.albumUri))
            }

            MediaItem.Builder()
                .setMediaId(audio.id.toString())
                .setUri(Uri.fromFile(File(audio.path)))
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()
    }

    fun playPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(progress: Float) {
        mediaController?.let {
            val dur = it.duration.coerceAtLeast(1L)
            val pos = (dur * progress).toLong()
            it.seekTo(pos)
            _positionMs.value = pos
            _progress.value = progress
        }
    }

    fun toggleShuffle() {
        mediaController?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeat() {
        mediaController?.let {
            val nextMode = when(it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
        }
    }

    fun toggleFavorite() {
        _currentAudio.value?.path?.let {
            favoritesRepository.toggleFavorite(it)
        }
    }

    fun createPlaylist(name: String): com.frito.music.data.models.Playlist {
        return playlistRepository.createPlaylist(name)
    }

    fun addCurrentAudioToPlaylist(playlistId: String) {
        _currentAudio.value?.path?.let {
            playlistRepository.addToPlaylist(playlistId, it)
        }
    }
}
