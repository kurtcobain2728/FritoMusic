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
import androidx.media3.common.PlaybackException
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

    fun setPreparingAudio(audio: AudioFile) {
        _currentAudio.value = audio
    }

    /** Limpia el estado "preparando" cuando la resolución de la URL falla,
     * para que no quede un mini-player fantasma colgado. Si había una cola
     * sonando, restaura el track que realmente está sonando. */
    fun clearPreparingAudio() {
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0 && controller.currentMediaItem != null) {
            _currentAudio.value = audioFilesMap[controller.currentMediaItem!!.mediaId]
                ?: _currentAudio.value
        } else {
            _currentAudio.value = null
        }
    }

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

    // --- Cola de streaming con resolución perezosa de URLs ---
    // Los items pendientes se encolan con uri vacía y se resuelven cuando
    // ExoPlayer llega a ellos. Clave = mediaId del item -> videoId de YouTube.
    private val pendingStreamVideoIds = LinkedHashMap<String, String>()
    private var streamResolver: (suspend (String) -> String?)? = null
    private var resolvingIndex = -1
    // Generación de cola: invalida resoluciones en curso cuando se cambia de cola
    private var queueGeneration = 0L

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

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
                    resolveCurrentIfPending()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _durationMs.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePendingPlaybackFailure()
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

    /**
     * Construye y encola los MediaItems. Genera un mediaId ÚNICO por posición
     * ("q{generación}-i{índice}") en lugar de usar audio.id (hash del videoId),
     * lo que elimina colisiones entre pistas distintas.
     * Devuelve las claves mediaId generadas, alineadas con [audios].
     */
    private fun buildAndSetQueue(audios: List<AudioFile>, startIndex: Int): List<String> {
        val controller = mediaController ?: return emptyList()
        queueGeneration++

        audioFilesMap.clear()
        pendingStreamVideoIds.clear()

        val keys = mutableListOf<String>()
        val mediaItems = audios.mapIndexed { index, audio ->
            val key = "q${queueGeneration}-i$index"
            keys.add(key)
            audioFilesMap[key] = audio

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(audio.title)
                .setArtist(audio.artist)

            if (audio.albumUri != null && audio.albumUri.isNotEmpty()) {
                metadataBuilder.setArtworkUri(Uri.parse(audio.albumUri))
            }

            // path vacío = item pendiente de resolver (stream); se encola sin uri real
            val uri = when {
                audio.path.startsWith("http://") || audio.path.startsWith("https://") ->
                    Uri.parse(audio.path)
                audio.path.isEmpty() -> Uri.EMPTY
                else -> Uri.fromFile(File(audio.path))
            }

            MediaItem.Builder()
                .setMediaId(key)
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        controller.prepare()
        controller.play()
        return keys
    }

    fun playAudios(audios: List<AudioFile>, startIndex: Int) {
        streamResolver = null
        buildAndSetQueue(audios, startIndex)
    }

    /**
     * Cola de streaming: solo el ítem [startIndex] trae URL resuelta (arranque
     * rápido); el resto se resuelve perezosamente vía [resolver] cuando ExoPlayer
     * llega a cada uno.
     */
    fun playStreamQueue(
        audios: List<AudioFile>,
        startIndex: Int,
        videoIds: List<String>,
        resolver: suspend (videoId: String) -> String?
    ) {
        val keys = buildAndSetQueue(audios, startIndex)
        if (keys.size != videoIds.size) return
        streamResolver = resolver
        keys.forEachIndexed { index, key ->
            // El ítem inicial ya viene resuelto: no marcarlo como pendiente
            if (index != startIndex) {
                pendingStreamVideoIds[key] = videoIds[index]
            }
        }

        // Pre-resolución secuencial en segundo plano: resuelve todas las URLs
        // pendientes (la caché de 4h las hace baratas) para que cuando ExoPlayer
        // llegue a cada canción ya tenga uri real y no falle.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val generation = queueGeneration
            keys.forEachIndexed { index, key ->
                if (generation != queueGeneration) return@launch // cambió la cola
                if (index == startIndex) return@forEachIndexed
                if (!pendingStreamVideoIds.containsKey(key)) return@forEachIndexed
                val url = runCatching { resolver(videoIds[index]) }.getOrNull()
                if (url.isNullOrEmpty()) return@forEachIndexed
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (generation != queueGeneration) return@withContext
                    if (pendingStreamVideoIds.remove(key) != null) {
                        val c = mediaController ?: return@withContext
                        if (index < c.mediaItemCount) {
                            val item = c.getMediaItemAt(index)
                            c.replaceMediaItem(index, item.buildUpon().setUri(Uri.parse(url)).build())
                        }
                    }
                }
            }
        }
    }

    /** Resuelve la URL del ítem actual si aún está pendiente (uri vacía). */
    private fun resolveCurrentIfPending() {
        val controller = mediaController ?: return
        val resolver = streamResolver ?: return
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        val videoId = pendingStreamVideoIds[currentItem.mediaId] ?: return
        val generationAtStart = queueGeneration
        if (resolvingIndex == index) return // ya hay una resolución en curso para este índice
        resolvingIndex = index

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val url = runCatching { resolver(videoId) }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                resolvingIndex = -1
                // Abortar si cambió la cola o el ítem actual mientras resolvíamos
                val c = mediaController ?: return@withContext
                if (generationAtStart != queueGeneration) return@withContext
                val currentIdx = c.currentMediaItemIndex
                if (currentIdx != index) return@withContext
                val current = c.currentMediaItem ?: return@withContext
                if (current.mediaId != currentItem.mediaId) return@withContext
                if (!url.isNullOrEmpty()) {
                    pendingStreamVideoIds.remove(current.mediaId)
                    c.replaceMediaItem(index, current.buildUpon().setUri(Uri.parse(url)).build())
                }
            }
        }
    }

    /**
     * Ante un error de playback: si el ítem actual es un stream pendiente,
     * intenta resolver su URL una vez más; si falla, salta al siguiente.
     * Ítems locales no se tocan (error real del archivo).
     */
    private fun handlePendingPlaybackFailure() {
        val controller = mediaController ?: return
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        val videoId = pendingStreamVideoIds[currentItem.mediaId]
            ?: return // ítem local u otro problema: no intervenir
        val resolver = streamResolver ?: return skipPending(controller, index)
        val generation = queueGeneration

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val url = runCatching { resolver(videoId) }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val c = mediaController ?: return@withContext
                if (generation != queueGeneration) return@withContext
                if (!url.isNullOrEmpty()) {
                    pendingStreamVideoIds.remove(currentItem.mediaId)
                    c.replaceMediaItem(index, currentItem.buildUpon().setUri(Uri.parse(url)).build())
                    c.prepare()
                    c.play()
                } else {
                    skipPending(c, index)
                }
            }
        }
    }

    private fun skipPending(controller: Player, index: Int) {
        if (index < controller.mediaItemCount - 1) {
            // seekToDefaultPosition(index) salta al ítem y reinicia desde el inicio;
            // seekTo(ms) espera Long (posición en ms), por eso no servía aquí
            controller.seekToDefaultPosition(index + 1)
            controller.prepare()
            controller.play()
        }
    }

    fun playPause() {
        mediaController?.let {
            // Guard: sin cola cargada no hay nada que reanudar (evita reanimar
            // una cola vieja tras un fallo de stream)
            if (it.mediaItemCount == 0) return
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    /**
     * Cierra el mini-player desde su botón X: detiene la reproducción,
     * vacía la cola y limpia el estado para que la barra desaparezca.
     */
    fun stopAndClear() {
        mediaController?.let {
            runCatching { it.pause() }
            runCatching { it.stop() }
            runCatching { it.clearMediaItems() }
        }
        streamResolver = null
        pendingStreamVideoIds.clear()
        resolvingIndex = -1
        audioFilesMap.clear()
        _currentAudio.value = null
        _isPlaying.value = false
        _progress.value = 0f
        _positionMs.value = 0L
        _durationMs.value = 0L
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

    override fun onCleared() {
        // Liberar el MediaController: sin esto queda un binding activo al
        // MediaSessionService después de destruir el ViewModel.
        mediaController?.release()
        mediaController = null
        super.onCleared()
    }
}
