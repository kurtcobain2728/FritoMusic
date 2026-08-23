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

    fun scanMusic() {
        if (_rootNode.value != null) return // Ya está escaneado
        viewModelScope.launch {
            scanInternal()
        }
    }

    /**
     * Re-escaneo forzado: reconstruye el árbol y la lista plana.
     * Se llama cuando una descarga termina para que la nueva canción aparezca.
     */
    fun rescan() {
        viewModelScope.launch {
            scanInternal()
        }
    }

    private suspend fun scanInternal() {
        _isLoading.value = true
        try {
            val root = kotlinx.coroutines.withContext(Dispatchers.IO) {
                scanner.scanLocalAudio()
            }
            _rootNode.value = root
            _currentNode.value = root
            _allAudios.value = flattenAudios(root)
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
