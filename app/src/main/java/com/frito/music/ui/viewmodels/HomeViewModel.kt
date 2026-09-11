package com.frito.music.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.FolderNode
import com.frito.music.data.repository.MediaScanner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = MediaScanner(application)

    private val _rootNode = MutableStateFlow<FolderNode?>(null)

    private val _currentNode = MutableStateFlow<FolderNode?>(null)
    val currentNode: StateFlow<FolderNode?> = _currentNode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Lista plana reactiva de TODOS los audios (para Biblioteca, Buscar, Favoritos,
    // PlaylistDetail). Antes cada pantalla la calculaba una vez con remember() y se
    // congelaba: las canciones descargadas no aparecían hasta reiniciar la app.
    private val _allAudios = MutableStateFlow<List<AudioFile>>(emptyList())
    val allAudios: StateFlow<List<AudioFile>> = _allAudios.asStateFlow()

    // ── Estado reactivo de selección múltiple (para sincronizar HomeScreen y MainActivity) ──
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderPaths: StateFlow<Set<String>> = _selectedFolderPaths.asStateFlow()

    private val _selectedAudioPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedAudioPaths: StateFlow<Set<String>> = _selectedAudioPaths.asStateFlow()

    private val _requestDeleteMultipleEvent = MutableStateFlow(false)
    val requestDeleteMultipleEvent: StateFlow<Boolean> = _requestDeleteMultipleEvent.asStateFlow()

    fun enterSelectionMode(folderPath: String? = null, audioPath: String? = null) {
        _selectedFolderPaths.value = if (folderPath != null) setOf(folderPath) else emptySet()
        _selectedAudioPaths.value = if (audioPath != null) setOf(audioPath) else emptySet()
        _isSelectionMode.value = true
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedFolderPaths.value = emptySet()
        _selectedAudioPaths.value = emptySet()
        _requestDeleteMultipleEvent.value = false
    }

    fun toggleFolder(path: String) {
        val current = _selectedFolderPaths.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _selectedFolderPaths.value = current
    }

    fun toggleAudio(path: String) {
        val current = _selectedAudioPaths.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _selectedAudioPaths.value = current
    }

    fun selectAll(folderPaths: List<String>, audioPaths: List<String>) {
        _selectedFolderPaths.value = folderPaths.toSet()
        _selectedAudioPaths.value = audioPaths.toSet()
    }

    fun deselectAll() {
        _selectedFolderPaths.value = emptySet()
        _selectedAudioPaths.value = emptySet()
    }

    fun requestDeleteMultiple() {
        _requestDeleteMultipleEvent.value = true
    }

    fun resetDeleteMultipleEvent() {
        _requestDeleteMultipleEvent.value = false
    }

    fun hideSelected() {
        val folders = _selectedFolderPaths.value
        val audios = _selectedAudioPaths.value
        val node = _currentNode.value

        val foldersToHide = node?.subfolders?.values
            ?.filter { folders.contains(it.path) }
            ?.map { Triple(it.path, it.name, it.realPath) } ?: emptyList()

        val audiosToHide = node?.audios
            ?.filter { audios.contains(it.path) }
            ?.map { Pair(it.path, it.title) } ?: emptyList()

        com.frito.music.data.repository.HiddenItemsManager.hideMultiple(foldersToHide, audiosToHide)
        exitSelectionMode()
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // Iniciar escaneo en background tan pronto como se crea el ViewModel en MainActivity
        scanMusic()
    }

    fun scanMusic() {
        if (_rootNode.value != null && _allAudios.value.isNotEmpty()) return // Ya está escaneado con canciones
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            scanInternal()
        }
    }

    /**
     * Re-escaneo forzado: reconstruye el árbol y la lista plana de forma fluida.
     * Se llama cuando una descarga termina para que la nueva canción aparezca sin parpadear la pantalla.
     */
    fun rescan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanInternal()
        }
    }

    private suspend fun scanInternal() {
        // Solo mostrar spinner en la carga en frío inicial si no hay contenido previo
        if (_rootNode.value == null) {
            _isLoading.value = true
        }
        try {
            val root = kotlinx.coroutines.withContext(Dispatchers.IO) {
                scanner.scanLocalAudio()
            }
            _rootNode.value = root
            val current = _currentNode.value
            if (current == null || current.path == "/" || current.path.isEmpty()) {
                _currentNode.value = root
            } else {
                _currentNode.value = findNodeByPath(root, current.path) ?: root
            }
            _allAudios.value = flattenAudios(root)
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Error al escanear música: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    fun navigateToFolder(folderName: String) {
        val current = _currentNode.value ?: return
        val target = current.subfolders[folderName]
        if (target != null) {
            _currentNode.value = target
        }
    }

    fun navigateUp() {
        val current = _currentNode.value
        val root = _rootNode.value
        if (current == null || root == null || current.path == root.path) {
            return
        }

        val targetPath = File(current.path).parent ?: "/"
        _currentNode.value = findNodeByPath(root, targetPath) ?: root
    }

    private fun findNodeByPath(node: FolderNode, path: String): FolderNode? {
        if (node.path == path) return node
        for (sub in node.subfolders.values) {
            val found = findNodeByPath(sub, path)
            if (found != null) return found
        }
        return null
    }

    private fun flattenAudios(node: FolderNode?): List<AudioFile> {
        if (node == null) return emptyList()
        val list = mutableListOf<AudioFile>()
        fun walk(n: FolderNode) {
            list.addAll(n.audios)
            for (sub in n.subfolders.values) walk(sub)
        }
        walk(node)
        return list
    }

    /** Compatibilidad con llamadas existentes; preferir [allAudios] (reactivo). */
    fun getAllAudios(node: FolderNode? = _rootNode.value): List<AudioFile> {
        if (node == null) return emptyList()
        val list = mutableListOf<AudioFile>()
        list.addAll(node.audios)
        for (sub in node.subfolders.values) {
            list.addAll(getAllAudios(sub))
        }
        return list
    }
}
