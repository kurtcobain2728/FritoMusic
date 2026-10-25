package com.frito.music.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.LyricsUiState
import com.frito.music.data.network.yt.StreamClientUtils
import com.frito.music.data.network.yt.YouTubeRepository
import com.frito.music.data.repository.FavoritesRepository
import com.frito.music.data.repository.LyricsRepository
import com.frito.music.data.repository.PlaylistRepository
import com.frito.music.data.repository.YouTubeLoginManager
import com.frito.music.downloader.OnlineQuality
import com.frito.music.downloader.OnlineQualityResolver
import com.frito.music.service.MusicService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val _currentQueue = MutableStateFlow<List<AudioFile>>(emptyList())
    val currentQueue: StateFlow<List<AudioFile>> = _currentQueue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val lyricsRepository = LyricsRepository(application)
    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null

    fun setPreparingAudio(audio: AudioFile, videoId: String? = null) {
        _currentAudio.value = audio
        _currentVideoId.value = videoId
        loadLyricsForCurrentAudio()
        updateRealAudioFormat()
    }

    /** Limpia el estado "preparando" cuando la resolución de la URL falla,
     * para que no quede un mini-player fantasma colgado. Si había una cola
     * sonando, restaura el track que realmente está sonando. */
    fun clearPreparingAudio() {
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0 && controller.currentMediaItem != null) {
            _currentAudio.value = audioFilesMap[controller.currentMediaItem!!.mediaId]
                ?: _currentAudio.value
            _currentVideoId.value = videoIdsByMediaId[controller.currentMediaItem!!.mediaId]
                ?: _currentVideoId.value
        } else {
            _currentAudio.value = null
            _currentVideoId.value = null
        }
        loadLyricsForCurrentAudio()
        updateRealAudioFormat()
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

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val audioFilesMap = mutableMapOf<String, AudioFile>()

    /**
     * videoId REAL de YouTube de cada mediaId de la cola de streaming.
     * El AudioFile solo guarda la URL resuelta (googlevideo), que NO contiene
     * el videoId: sin este map no se puede dar like ni añadir a playlist.
     */
    private val videoIdsByMediaId = mutableMapOf<String, String>()

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    private val _realAudioFormat = MutableStateFlow<RealAudioFormatInfo?>(null)
    val realAudioFormat: StateFlow<RealAudioFormatInfo?> = _realAudioFormat.asStateFlow()

    // --- Cola de streaming con resolución perezosa de URLs ---
    // Los items pendientes se encolan con uri vacía y se resuelven cuando
    // ExoPlayer llega a ellos. Clave = mediaId del item -> videoId de YouTube.
    private val pendingStreamVideoIds = LinkedHashMap<String, String>()
    private var streamResolver: (suspend (String) -> String?)? = null
    private var resolvingIndex = -1
    // Generación de cola: invalida resoluciones en curso cuando se cambia de cola
    private var queueGeneration = 0L
    // Intentos de recuperación por canción en esta cola: 1er fallo → reintento
    // por YouTube (NewPipe primero), 2º fallo → fuentes alternativas
    // (JioSaavn/Qobuz), 3er fallo → saltar. Evita bucles si las URLs nuevas
    // vuelven a fallar; se limpia al construir una cola nueva.
    private val streamRecoveryAttempts = mutableMapOf<String, Int>()
    // Canciones que ya demostraron que YouTube no las sirve (p. ej. restricción
    // de edad): en reproducciones posteriores van directo a las alternativas.
    private val alternativeOnlyVideoIds = mutableSetOf<String>()
    // Fallos consecutivos de stream sin ninguna reproducción exitosa: al
    // superar el límite se detiene y se avisa (en vez de saltar toda la cola)
    private var consecutiveStreamFailures = 0
    private val maxConsecutiveStreamFailures = 2

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
            val controller = mediaController ?: return@addListener

            // 1. Sincronizar estado base si MusicService ya está activo en segundo plano
            _isPlaying.value = controller.isPlaying
            _shuffleModeEnabled.value = controller.shuffleModeEnabled

            val savedRepeat = getApplication<Application>()
                .getSharedPreferences("frito_player_prefs", Context.MODE_PRIVATE)
                .getInt("saved_repeat_mode", Player.REPEAT_MODE_OFF)
            if (savedRepeat != Player.REPEAT_MODE_OFF && controller.repeatMode == Player.REPEAT_MODE_OFF) {
                controller.repeatMode = savedRepeat
                _repeatMode.value = savedRepeat
            } else {
                _repeatMode.value = controller.repeatMode
            }

            _durationMs.value = controller.duration.coerceAtLeast(0L)
            _positionMs.value = controller.currentPosition.coerceAtLeast(0L)
            _playbackSpeed.value = controller.playbackParameters.speed
            _playbackPitch.value = controller.playbackParameters.pitch

            // 2. Recuperar la pista en reproducción actual
            val initialItem = controller.currentMediaItem
            if (initialItem != null) {
                val id = initialItem.mediaId
                val meta = initialItem.mediaMetadata
                val metaExtras = meta.extras
                val videoId = metaExtras?.getString("videoId")
                    ?: initialItem.localConfiguration?.customCacheKey
                    ?: videoIdsByMediaId[id]

                val recoveredAudio = audioFilesMap[id] ?: AudioFile(
                    id = id.hashCode().toLong(),
                    title = meta.title?.toString() ?: "Desconocido",
                    artist = meta.artist?.toString() ?: "Artista Desconocido",
                    path = metaExtras?.getString("path") ?: initialItem.localConfiguration?.uri?.toString() ?: "",
                    durationMs = metaExtras?.getLong("durationMs", 0L)?.takeIf { it > 0 } ?: controller.duration.coerceAtLeast(0L),
                    sizeBytes = 0L,
                    albumUri = meta.artworkUri?.toString(),
                    album = meta.albumTitle?.toString() ?: metaExtras?.getString("album") ?: "Álbum Desconocido",
                    dateAdded = 0L
                )
                audioFilesMap[id] = recoveredAudio
                _currentAudio.value = recoveredAudio
                if (!videoId.isNullOrBlank()) {
                    videoIdsByMediaId[id] = videoId
                    _currentVideoId.value = videoId
                }
                loadLyricsForCurrentAudio()
            }

            _currentQueueIndex.value = controller.currentMediaItemIndex
            updateRealAudioFormat()
            if (_currentQueue.value.isEmpty() && controller.mediaItemCount > 0) {
                val list = mutableListOf<AudioFile>()
                for (i in 0 until controller.mediaItemCount) {
                    val item = controller.getMediaItemAt(i)
                    val id = item.mediaId
                    val meta = item.mediaMetadata
                    val metaExtras = meta.extras
                    val recovered = audioFilesMap[id] ?: AudioFile(
                        id = id.hashCode().toLong(),
                        title = meta.title?.toString() ?: "Desconocido",
                        artist = meta.artist?.toString() ?: "Artista Desconocido",
                        path = metaExtras?.getString("path") ?: item.localConfiguration?.uri?.toString() ?: "",
                        durationMs = metaExtras?.getLong("durationMs", 0L)?.takeIf { it > 0 } ?: 0L,
                        sizeBytes = 0L,
                        albumUri = meta.artworkUri?.toString(),
                        album = meta.albumTitle?.toString() ?: metaExtras?.getString("album") ?: "Álbum Desconocido",
                        dateAdded = 0L
                    )
                    list.add(recovered)
                }
                _currentQueue.value = list
            }

            controller.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    updateRealAudioFormat()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    // Si algo está sonando, la racha de fallos se rompe y la
                    // canción actual recupera intentos para futuros fallos
                    if (isPlaying) {
                        consecutiveStreamFailures = 0
                        controller.currentMediaItem?.mediaId?.let { mediaId ->
                            videoIdsByMediaId[mediaId]?.let { videoId ->
                                streamRecoveryAttempts.remove(videoId)
                            }
                        }
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItem == null) {
                        _currentAudio.value = null
                        _currentVideoId.value = null
                        updateRealAudioFormat()
                        return
                    }
                    val id = mediaItem.mediaId
                    val meta = mediaItem.mediaMetadata
                    val metaExtras = meta.extras
                    val videoId = metaExtras?.getString("videoId")
                        ?: mediaItem.localConfiguration?.customCacheKey
                        ?: videoIdsByMediaId[id]

                    val audio = audioFilesMap[id] ?: AudioFile(
                        id = id.hashCode().toLong(),
                        title = meta.title?.toString() ?: "Desconocido",
                        artist = meta.artist?.toString() ?: "Artista Desconocido",
                        path = metaExtras?.getString("path") ?: mediaItem.localConfiguration?.uri?.toString() ?: "",
                        durationMs = metaExtras?.getLong("durationMs", 0L)?.takeIf { it > 0 } ?: controller.duration.coerceAtLeast(0L),
                        sizeBytes = 0L,
                        albumUri = meta.artworkUri?.toString(),
                        album = meta.albumTitle?.toString() ?: metaExtras?.getString("album") ?: "Álbum Desconocido",
                        dateAdded = 0L
                    )
                    audioFilesMap[id] = audio
                    _currentAudio.value = audio
                    com.frito.music.data.repository.RecapManager.recordPlayback(
                        title = audio.title,
                        artist = audio.artist,
                        album = audio.album,
                        albumUri = audio.albumUri,
                        durationMs = audio.durationMs
                    )
                    if (!videoId.isNullOrBlank()) {
                        videoIdsByMediaId[id] = videoId
                        _currentVideoId.value = videoId
                        notifyPlaybackRegistered(videoId)
                    }
                    _currentQueueIndex.value = controller.currentMediaItemIndex
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                    resolveCurrentIfPending()
                    loadLyricsForCurrentAudio()
                    updateRealAudioFormat()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                    if (playbackState == Player.STATE_READY) {
                        updateRealAudioFormat()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e(
                        "PlayerViewModel",
                        "playerError code=${error.errorCode} name=${error.errorCodeName} http=${findHttpStatusCode(error)} msg=${error.message} causas=[${describeError(error)}]"
                    )
                    handleStreamPlaybackFailure(error)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                    _playbackSpeed.value = playbackParameters.speed
                    _playbackPitch.value = playbackParameters.pitch
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                    getApplication<Application>()
                        .getSharedPreferences("frito_player_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("saved_repeat_mode", repeatMode)
                        .apply()
                }
            })
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val s = speed.coerceIn(0.25f, 2.5f)
        val p = pitch.coerceIn(0.25f, 2.0f)
        _playbackSpeed.value = s
        _playbackPitch.value = p
        mediaController?.playbackParameters = androidx.media3.common.PlaybackParameters(s, p)
    }

    fun applySpeedPitchPreset(preset: String) {
        when (preset.lowercase()) {
            "slowed" -> setPlaybackParameters(0.97f, 0.85f)
            "normal" -> setPlaybackParameters(1.00f, 1.00f)
            "nightcore" -> setPlaybackParameters(1.05f, 1.30f)
        }
    }

    fun updateRealAudioFormat() {
        val controller = mediaController
        val current = _currentAudio.value
        val videoId = _currentVideoId.value

        val isLocal = current?.path?.let { !it.startsWith("http://") && !it.startsWith("https://") } ?: false
        val resolved = videoId?.let { YouTubeRepository.getResolvedFormatDetails(it) }

        val audioTrack = controller?.currentTracks?.groups
            ?.firstOrNull { it.isSelected && it.type == C.TRACK_TYPE_AUDIO }
            ?.let { group ->
                (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
                    ?: if (group.length > 0) group.getTrackFormat(0) else null
            }

        if (resolved != null) {
            val trackBitrate = (audioTrack?.bitrate ?: 0).takeIf { it > 0 } ?: (resolved.bitrate)
            val trackSampleRate = (audioTrack?.sampleRate ?: 0).takeIf { it > 0 } ?: resolved.sampleRate
            val channels = (audioTrack?.channelCount ?: 2).coerceAtLeast(1)
            _realAudioFormat.value = RealAudioFormatInfo(
                codec = resolved.codec,
                container = resolved.container,
                bitrateKbps = (trackBitrate / 1000).coerceAtLeast(1),
                sampleRateHz = trackSampleRate,
                channelCount = channels,
                itag = resolved.itag,
                isLocal = false,
                sourceDescription = "YouTube Audio (itag ${resolved.itag})"
            )
        } else if (audioTrack != null) {
            val mime = audioTrack.sampleMimeType.orEmpty().lowercase()
            val codecs = audioTrack.codecs.orEmpty().lowercase()
            val cleanCodec = when {
                mime.contains("opus") || codecs.contains("opus") -> "OPUS"
                mime.contains("mp4a") || mime.contains("aac") || codecs.contains("mp4a") || codecs.contains("aac") -> "AAC"
                mime.contains("flac") -> "FLAC"
                mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
                mime.contains("vorbis") -> "VORBIS"
                else -> if (isLocal) "LOCAL" else "STREAM"
            }
            val container = when {
                mime.contains("webm") -> "WebM"
                mime.contains("mp4") -> "M4A"
                mime.contains("ogg") -> "OGG"
                isLocal -> "Local"
                else -> "Audio"
            }
            val bitrateKbps = when {
                audioTrack.bitrate > 0 -> audioTrack.bitrate / 1000
                audioTrack.averageBitrate > 0 -> audioTrack.averageBitrate / 1000
                else -> 0
            }
            _realAudioFormat.value = RealAudioFormatInfo(
                codec = cleanCodec,
                container = container,
                bitrateKbps = bitrateKbps,
                sampleRateHz = audioTrack.sampleRate.coerceAtLeast(0),
                channelCount = audioTrack.channelCount.coerceAtLeast(1),
                itag = null,
                isLocal = isLocal,
                sourceDescription = if (isLocal) "Archivo Local" else "Stream de Audio"
            )
        } else if (current != null) {
            if (isLocal) {
                val ext = current.path.substringAfterLast(".", "AUDIO").uppercase()
                _realAudioFormat.value = RealAudioFormatInfo(
                    codec = ext,
                    container = "Local",
                    bitrateKbps = 0,
                    sampleRateHz = 44100,
                    channelCount = 2,
                    itag = null,
                    isLocal = true,
                    sourceDescription = "Archivo en el dispositivo"
                )
            } else {
                val prefQuality = com.frito.music.data.preferences.StreamQualityPreferences.getQuality(getApplication())
                val prefCodec = com.frito.music.data.preferences.StreamQualityPreferences.getCodecPreference(getApplication())
                val estimatedBitrate = when (prefQuality) {
                    com.frito.music.data.preferences.StreamQuality.HIGH -> if (prefCodec == com.frito.music.data.preferences.AudioCodecPreference.AAC) 256 else 160
                    com.frito.music.data.preferences.StreamQuality.NORMAL -> 128
                    com.frito.music.data.preferences.StreamQuality.LOW -> if (prefCodec == com.frito.music.data.preferences.AudioCodecPreference.AAC) 48 else 64
                }
                val codecName = when (prefCodec) {
                    com.frito.music.data.preferences.AudioCodecPreference.AAC -> "AAC"
                    com.frito.music.data.preferences.AudioCodecPreference.OPUS -> "OPUS"
                    com.frito.music.data.preferences.AudioCodecPreference.AUTO -> "AUTO"
                }
                val container = if (prefCodec == com.frito.music.data.preferences.AudioCodecPreference.AAC) "M4A" else "WebM"
                _realAudioFormat.value = RealAudioFormatInfo(
                    codec = codecName,
                    container = container,
                    bitrateKbps = estimatedBitrate,
                    sampleRateHz = 48000,
                    channelCount = 2,
                    itag = null,
                    isLocal = false,
                    sourceDescription = "Conectando stream..."
                )
            }
        } else {
            _realAudioFormat.value = null
        }
    }

    fun reloadCurrentStreamQuality() {
        val controller = mediaController ?: return
        val current = _currentAudio.value ?: return
        val videoId = _currentVideoId.value ?: return
        viewModelScope.launch {
            YouTubeRepository.clearStreamUrlCache()
            val currentPos = controller.currentPosition
            val isCurrentlyPlaying = controller.isPlaying
            val newUrl = resolveStreamWithFallback(videoId, current.title, current.artist, preferNewPipe = false)
            if (!newUrl.isNullOrBlank()) {
                val updatedAudio = current.copy(path = newUrl)
                _currentAudio.value = updatedAudio
                val key = controller.currentMediaItem?.mediaId
                if (key != null) {
                    audioFilesMap[key] = updatedAudio
                }

                val currentIdx = controller.currentMediaItemIndex
                if (currentIdx in 0 until controller.mediaItemCount) {
                    val currentItem = controller.getMediaItemAt(currentIdx)
                    controller.replaceMediaItem(
                        currentIdx,
                        currentItem.buildUpon()
                            .setUri(Uri.parse(newUrl))
                            .setCustomCacheKey(videoId)
                            .build()
                    )
                    controller.seekTo(currentPos)
                    if (isCurrentlyPlaying) controller.play()
                }
                updateRealAudioFormat()
            }
        }
    }

    fun updateCurrentAudioMetadata(newTitle: String, newArtist: String, newAlbum: String) {
        val current = _currentAudio.value ?: return
        val finalTitle = newTitle.trim().ifEmpty { current.title }
        val finalArtist = newArtist.trim().ifEmpty { current.artist }
        val finalAlbum = newAlbum.trim().ifEmpty { current.album }

        val updated = current.copy(
            title = finalTitle,
            artist = finalArtist,
            album = finalAlbum
        )
        _currentAudio.value = updated
        val key = mediaController?.currentMediaItem?.mediaId
        if (!key.isNullOrBlank()) {
            audioFilesMap[key] = updated
        }

        com.frito.music.data.repository.CustomNamesManager.setCustomAudioMetadata(
            path = current.path,
            title = finalTitle,
            artist = finalArtist,
            album = finalAlbum
        )
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
                    delay(250L)
                } else {
                    delay(500L)
                }
            }
        }
    }

    /**
     * Construye y encola los MediaItems. Genera un mediaId ÚNICO por posición
     * ("q{generación}-i{índice}") en lugar de usar audio.id (hash del videoId),
     * lo que elimina colisiones entre pistas distintas.
     * Devuelve las claves mediaId generadas, alineadas con [audios].
     */
    private fun buildAndSetQueue(
        audios: List<AudioFile>,
        startIndex: Int,
        videoIds: List<String>? = null
    ): List<String> {
        val controller = mediaController ?: return emptyList()
        queueGeneration++

        audioFilesMap.clear()
        pendingStreamVideoIds.clear()
        videoIdsByMediaId.clear()
        streamRecoveryAttempts.clear()
        alternativeOnlyVideoIds.clear()
        consecutiveStreamFailures = 0
        _currentVideoId.value = null
        _currentQueue.value = audios
        _currentQueueIndex.value = startIndex

        val keys = mutableListOf<String>()
        val mediaItems = audios.mapIndexed { index, audio ->
            val key = "q${queueGeneration}-i$index"
            keys.add(key)
            audioFilesMap[key] = audio

            val vid = videoIds?.getOrNull(index)
            val extras = android.os.Bundle().apply {
                putString("path", audio.path)
                putString("album", audio.album)
                if (!vid.isNullOrEmpty()) {
                    putString("videoId", vid)
                }
                putLong("durationMs", audio.durationMs)
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(audio.title)
                .setArtist(audio.artist)
                .setAlbumTitle(audio.album)
                .setExtras(extras)

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

            val builder = MediaItem.Builder()
                .setMediaId(key)
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())

            if (!vid.isNullOrEmpty()) {
                builder.setCustomCacheKey(vid)
            }

            builder.build()
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
        val keys = buildAndSetQueue(audios, startIndex, videoIds)
        if (keys.size != videoIds.size) return
        streamResolver = resolver
        keys.forEachIndexed { index, key ->
            videoIdsByMediaId[key] = videoIds[index]
            // El ítem inicial ya viene resuelto: no marcarlo como pendiente
            if (index != startIndex) {
                pendingStreamVideoIds[key] = videoIds[index]
            }
        }
        // Exponer el videoId real del ítem que empieza a sonar
        _currentVideoId.value = videoIds[startIndex]

        // Pre-resolución eficiente en segundo plano: resuelve solo los siguientes 1-2 ítems
        // para no sobrecalentar la CPU con decenas de descifrados JS simultáneos de Rhino
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val generation = queueGeneration
            val endPreResolve = (startIndex + 2).coerceAtMost(keys.size - 1)
            for (idx in (startIndex + 1)..endPreResolve) {
                if (generation != queueGeneration) return@launch
                val key = keys.getOrNull(idx) ?: continue
                if (!pendingStreamVideoIds.containsKey(key)) continue
                val url = runCatching { resolver(videoIds[idx]) }.getOrNull()
                if (url.isNullOrEmpty()) continue
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (generation != queueGeneration) return@withContext
                    if (pendingStreamVideoIds.remove(key) != null) {
                        val c = mediaController ?: return@withContext
                        if (idx < c.mediaItemCount) {
                            val item = c.getMediaItemAt(idx)
                            c.replaceMediaItem(
                                idx,
                                item.buildUpon()
                                    .setUri(Uri.parse(url))
                                    .setCustomCacheKey(videoIds[idx])
                                    .build()
                            )
                        }
                    }
                }
            }
        }
    }

    /** Resuelve la URL del ítem actual si aún está pendiente (uri vacía) y pre-resuelve el siguiente. */
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
                    c.replaceMediaItem(
                        index,
                        current.buildUpon()
                            .setUri(Uri.parse(url))
                            .setCustomCacheKey(videoId)
                            .build()
                    )
                    // Pre-resolver solo la siguiente canción si aún está pendiente
                    val nextIdx = index + 1
                    if (nextIdx < c.mediaItemCount) {
                        val nextItem = c.getMediaItemAt(nextIdx)
                        val nextVideoId = pendingStreamVideoIds[nextItem.mediaId]
                        if (nextVideoId != null) {
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val nextUrl = runCatching { resolver(nextVideoId) }.getOrNull()
                                if (!nextUrl.isNullOrEmpty()) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (generationAtStart == queueGeneration && pendingStreamVideoIds.remove(nextItem.mediaId) != null) {
                                            c.replaceMediaItem(
                                                nextIdx,
                                                nextItem.buildUpon()
                                                    .setUri(Uri.parse(nextUrl))
                                                    .setCustomCacheKey(nextVideoId)
                                                    .build()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun notifyPlaybackRegistered(videoId: String) {
        if (!YouTubeLoginManager.isLoggedIn.value || videoId.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                YouTubeRepository.registerPlayback(videoId)
                android.util.Log.d("PlayerViewModel", "Playback registrado en YouTube: $videoId")
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "Aviso registrando playback en YouTube: ${e.message}")
            }
        }
    }

    /**
     * Resolución de stream con cadena de fallback completa (para reproducir):
     *
     *  1. YouTube por la vía preferida (clientes o NewPipe) — URL VALIDADA con
     *     una petición Range mínima antes de devolverla.
     *  2. YouTube por la vía opuesta si la anterior falla o da URL muerta.
     *  3. Proveedores alternativos (JioSaavn 320 kbps con match estricto /
     *     Qobuz lossless) para canciones bloqueadas por bot-detection.
     *
     * Devuelve la primera URL reproducible, o null si ninguna fuente sirve.
     */
    suspend fun resolveStreamWithFallback(
        videoId: String,
        title: String,
        artist: String,
        preferNewPipe: Boolean = false,
        skipYouTube: Boolean = false
    ): String? {
        val youtubeBlocked = skipYouTube || videoId in alternativeOnlyVideoIds
        android.util.Log.i(
            "FritoFallback",
            "resolve videoId=$videoId preferNewPipe=$preferNewPipe skipYouTube=$youtubeBlocked titulo='$title' artista='$artist'"
        )

        suspend fun tryYouTube(viaNewPipe: Boolean): String? {
            val url = runCatching {
                if (viaNewPipe) YouTubeRepository.getStreamUrlNewPipeFirst(videoId).getOrNull()
                else YouTubeRepository.getStreamUrl(videoId).getOrNull()
            }.getOrNull()
            if (url == null) {
                android.util.Log.d("FritoFallback", "  YouTube(${if (viaNewPipe) "NewPipe" else "clientes"}) -> sin URL")
                return null
            }
            android.util.Log.i("FritoFallback", "  YouTube(${if (viaNewPipe) "NewPipe" else "clientes"}) -> OK (validada)")
            return url
        }

        suspend fun tryAlternatives(): String? {
            android.util.Log.i("FritoFallback", "  probando fuentes alternativas (JioSaavn/Qobuz)")
            val altUrl = runCatching {
                OnlineQualityResolver.resolve(
                    getApplication<Application>(),
                    videoId,
                    title,
                    artist,
                    OnlineQuality.MEDIUM
                ).getOrNull()?.streamUrl
            }.getOrNull()
            if (!altUrl.isNullOrEmpty()) {
                val playable = YouTubeRepository.isStreamUrlPlayable(altUrl)
                android.util.Log.i("FritoFallback", "  Alternativa -> url=${altUrl.take(80)}… playable=$playable")
                if (playable) {
                    // Recordar que esta canción se sirve por alternativas: las
                    // próximas reproducciones van directo (sin reintentar YouTube)
                    alternativeOnlyVideoIds.add(videoId)
                    return altUrl
                }
                return null
            }
            android.util.Log.w("FritoFallback", "  Alternativa -> ninguna fuente encontró la canción")
            return null
        }

        if (!youtubeBlocked) {
            tryYouTube(preferNewPipe)?.let { return it }
            tryYouTube(!preferNewPipe)?.let { return it }
            tryAlternatives()?.let { return it }
            return null
        }

        // YouTube ya falló dos veces para esta canción (p. ej. restricción de
        // edad en el CDN): alternativas primero y YouTube como último recurso.
        tryAlternatives()?.let { return it }
        tryYouTube(true)?.let { return it }
        tryYouTube(false)?.let { return it }
        return null
    }

    /**
     * Rescate en paralelo tras el primer fallo de playback. Lanza a la vez el
     * reintento de YouTube (NewPipe primero, luego clientes) y las fuentes
     * alternativas (JioSaavn/Qobuz), con espera acotada en cada uno.
     *
     * · preferAlternatives=true (403/416: bloqueo a nivel CDN) → gana Saavn
     *   cuando llega (320 kbps, sin previews trampa); YouTube queda de respaldo.
     * · preferAlternatives=false (fallo transitorio) → gana YouTube si responde
     *   rápido; si no, las alternativas.
     */
    private suspend fun resolveStreamInParallel(
        videoId: String,
        title: String,
        artist: String,
        preferAlternatives: Boolean,
    ): String? = kotlinx.coroutines.coroutineScope {
        val ytJob = async(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                runCatching {
                    YouTubeRepository.getStreamUrlNewPipeFirst(videoId).getOrNull()
                        ?: YouTubeRepository.getStreamUrl(videoId).getOrNull()
                }.getOrNull()
                    ?.takeIf { YouTubeRepository.isStreamUrlPlayable(it) }
            }
        }
        val altJob = async(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(4_000) {
                runCatching {
                    OnlineQualityResolver.resolve(
                        getApplication<Application>(),
                        videoId,
                        title,
                        artist,
                        OnlineQuality.MEDIUM
                    ).getOrNull()?.streamUrl
                }.getOrNull()
                    ?.takeIf { YouTubeRepository.isStreamUrlPlayable(it) }
            }
        }

        if (preferAlternatives) {
            val altUrl = altJob.await()
            if (!altUrl.isNullOrEmpty()) {
                alternativeOnlyVideoIds.add(videoId)
                android.util.Log.i("FritoFallback", "  Paralelo -> ganó alternativa (Saavn/Qobuz) para $videoId")
                return@coroutineScope altUrl
            }
            val ytUrl = ytJob.await()
            if (!ytUrl.isNullOrEmpty()) {
                android.util.Log.i("FritoFallback", "  Paralelo -> ganó YouTube (respaldo) para $videoId")
                return@coroutineScope ytUrl
            }
            android.util.Log.w("FritoFallback", "  Paralelo -> sin URL para $videoId")
            return@coroutineScope null
        }

        val ytUrl = ytJob.await()
        if (!ytUrl.isNullOrEmpty()) {
            altJob.cancel()
            return@coroutineScope ytUrl
        }
        val altUrl = altJob.await()
        if (!altUrl.isNullOrEmpty()) {
            alternativeOnlyVideoIds.add(videoId)
            android.util.Log.i("FritoFallback", "  Paralelo -> ganó alternativa para $videoId")
            return@coroutineScope altUrl
        }
        android.util.Log.w("FritoFallback", "  Paralelo -> sin URL para $videoId")
        null
    }

    /**
     * Ante un error de playback en un ítem de STREAMING (resuelto o pendiente):
     *
     * 1) Re-resuelve la URL UNA vez probando NewPipe primero (otra vía de
     *    extracción) y luego los clientes de Innertube, sin caché previa.
     * 2) Si llega una URL nueva, reemplaza el ítem y reintenta la reproducción.
     * 3) Si falla de nuevo, salta al siguiente ítem — pero si ya van 2 canciones
     *    falladas seguidas sin reproducir ninguna, DETIENE y avisa al usuario
     *    (antes saltaba toda la cola sin control).
     *
     * Ítems locales no se tocan (su error es real del archivo).
     */
    private fun handleStreamPlaybackFailure(error: PlaybackException) {
        val controller = mediaController ?: return
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        val videoId = videoIdsByMediaId[currentItem.mediaId]
            ?: return // ítem local u otro problema: no intervenir

        // Diagnóstico estilo FridaMusic: código HTTP y cliente que emitió la
        // URL que falló. Un 403 marca ese cliente en backoff (10 min) para no
        // volver a intentarlo en esta canción.
        val httpCode = findHttpStatusCode(error)
        val failingUrl = currentItem.localConfiguration?.uri?.toString()
        val failingClient = failingUrl
            ?.takeIf { it.startsWith("http") }
            ?.let { StreamClientUtils.resolveRequestProfile(it).requestedClientName }
        if (httpCode == 403) {
            android.util.Log.w(
                "PlayerViewModel",
                "403 del cliente=$failingClient para $videoId; backoff + re-resolución"
            )
        }
        YouTubeRepository.markStreamClientFailed(videoId, failingClient, httpCode)
        YouTubeRepository.invalidateStreamUrl(videoId)
        // Limpiar entradas corruptas/parciales de la caché de Media3 para esta
        // canción: una entrada dañada hacía fallar el playback una y otra vez
        // (ERROR_CODE_IO_UNSPECIFIED con http=null, sin pedir red).
        com.frito.music.service.PlayerCacheHolder.removeForVideo(videoId)

        // Demasiados fallos seguidos: parar y avisar en vez de saltar sin fin
        if (consecutiveStreamFailures >= maxConsecutiveStreamFailures) {
            runCatching { controller.stop() }
            android.widget.Toast.makeText(
                getApplication(),
                "Varias canciones no se pudieron reproducir. Prueba de nuevo o inicia sesión.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        // Hasta dos reintentos por canción en esta cola:
        //  · 1er fallo → rescate en paralelo (YouTube + alternativas a la vez)
        //  · 2º fallo  → fuentes alternativas (JioSaavn/Qobuz)
        //  · 3er fallo → saltar (protege contra bucles infinitos)
        val attempt = (streamRecoveryAttempts[videoId] ?: 0) + 1
        streamRecoveryAttempts[videoId] = attempt
        if (attempt > 2) {
            consecutiveStreamFailures++
            skipPending(controller, index)
            return
        }
        val useAlternatives = attempt >= 2

        val generation = queueGeneration
        val audio = audioFilesMap[currentItem.mediaId]
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val url = if (useAlternatives) {
                // 2º fallo: alternativas primero, YouTube como último recurso
                resolveStreamWithFallback(
                    videoId = videoId,
                    title = audio?.title ?: "",
                    artist = audio?.artist ?: "",
                    preferNewPipe = false,
                    skipYouTube = true
                )
            } else {
                // 1er fallo: rescate en paralelo (YouTube y Saavn a la vez)
                resolveStreamInParallel(
                    videoId = videoId,
                    title = audio?.title ?: "",
                    artist = audio?.artist ?: "",
                    preferAlternatives = httpCode == 403 || httpCode == 416
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val c = mediaController ?: return@withContext
                if (generation != queueGeneration) return@withContext
                val idx = c.currentMediaItemIndex
                val stillCurrent = idx == index && c.currentMediaItem?.mediaId == currentItem.mediaId
                // Si el usuario ya cambió de canción manualmente, no tocar nada
                if (!stillCurrent) return@withContext
                if (!url.isNullOrEmpty()) {
                    pendingStreamVideoIds.remove(currentItem.mediaId)
                    c.replaceMediaItem(
                        index,
                        currentItem.buildUpon()
                            .setUri(Uri.parse(url))
                            .setCustomCacheKey(videoId)
                            .build()
                    )
                    c.prepare()
                    c.play()
                } else {
                    consecutiveStreamFailures++
                    skipPending(c, index)
                }
            }
        }
    }

    /** Busca el código HTTP dentro de la cadena de causas del error de Media3. */
    private fun findHttpStatusCode(error: PlaybackException): Int? {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 6) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
            depth++
        }
        return null
    }

    /** Describe la cadena completa de causas de un error (para diagnóstico). */
    private fun describeError(error: Throwable): String {
        val sb = StringBuilder()
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 8) {
            if (depth > 0) sb.append(" <- ")
            sb.append("${cause.javaClass.simpleName}: ${cause.message}")
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    private fun skipPending(controller: Player, index: Int) {
        if (index < controller.mediaItemCount - 1) {
            // seekToDefaultPosition(index) salta al ítem y reinicia desde el inicio;
            // seekTo(ms) espera Long (posición en ms), por eso no servía aquí
            controller.seekToDefaultPosition(index + 1)
            controller.prepare()
            controller.play()
        } else {
            // No hay siguiente: detener en lugar de dejar el reproductor
            // atrapado en buffering/reintentos
            runCatching { controller.stop() }
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
        videoIdsByMediaId.clear()
        resolvingIndex = -1
        audioFilesMap.clear()
        _currentAudio.value = null
        _currentVideoId.value = null
        _currentQueue.value = emptyList()
        _currentQueueIndex.value = 0
        _isPlaying.value = false
        _progress.value = 0f
        _positionMs.value = 0L
        _durationMs.value = 0L
        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState.Idle
    }

    fun loadLyricsForCurrentAudio(forceRefresh: Boolean = false) {
        val audio = _currentAudio.value
        if (audio == null) {
            _lyricsState.value = LyricsUiState.Idle
            return
        }
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            val durationSec = (mediaController?.duration?.takeIf { it > 0 } ?: audio.durationMs.takeIf { it > 0 })?.let { it / 1000 }
            val lyrics = lyricsRepository.getLyrics(
                title = audio.title,
                artist = audio.artist,
                durationSeconds = durationSec,
                localFilePath = if (audio.path.startsWith("http://") || audio.path.startsWith("https://") || audio.path.isEmpty()) null else audio.path,
                videoId = _currentVideoId.value,
                forceRefresh = forceRefresh
            )
            if (lyrics != null && (lyrics.lines.isNotEmpty() || !lyrics.plainLyrics.isNullOrBlank())) {
                _lyricsState.value = LyricsUiState.Success(lyrics)
            } else {
                _lyricsState.value = LyricsUiState.Empty
            }
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.prepare()
            controller.play()
        }
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

    fun seekToMs(positionMs: Long) {
        mediaController?.let {
            val dur = it.duration.coerceAtLeast(1L)
            val clampedPos = positionMs.coerceIn(0L, dur)
            it.seekTo(clampedPos)
            _positionMs.value = clampedPos
            _progress.value = (clampedPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
    }

    fun seekTo(positionMs: Long) = seekToMs(positionMs)

    fun refreshLyrics() = loadLyricsForCurrentAudio(forceRefresh = true)

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
            _repeatMode.value = nextMode
            getApplication<Application>()
                .getSharedPreferences("frito_player_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("saved_repeat_mode", nextMode)
                .apply()
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

    fun deletePlaylist(playlistId: String) {
        playlistRepository.deletePlaylist(playlistId)
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

data class RealAudioFormatInfo(
    val codec: String,
    val container: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channelCount: Int = 2,
    val itag: Int? = null,
    val isLocal: Boolean = false,
    val sourceDescription: String = "YouTube Audio"
)

