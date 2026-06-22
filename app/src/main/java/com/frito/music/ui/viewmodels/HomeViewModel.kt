package com.frito.music.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.data.models.FolderNode
import com.frito.music.data.repository.MediaScanner
import java.io.File
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

    fun scanMusic() {
        if (_rootNode.value != null) return // Ya está escaneado
        viewModelScope.launch {
            _isLoading.value = true
            val root = scanner.scanLocalAudio()
            
            // Si la raíz no tiene audios directamente pero solo una subcarpeta "storage" por ejemplo, podríamos simplificar el árbol aquí, pero lo mantendremos puro por ahora.
            _rootNode.value = root
            _currentNode.value = root
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

    fun getAllAudios(node: FolderNode? = _rootNode.value): List<com.frito.music.data.models.AudioFile> {
        if (node == null) return emptyList()
        val list = mutableListOf<com.frito.music.data.models.AudioFile>()
        list.addAll(node.audios)
        for (sub in node.subfolders.values) {
            list.addAll(getAllAudios(sub))
        }
        return list
    }
}
