package com.frito.music.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frito.music.extensions.ExtensionInfo
import com.frito.music.extensions.ExtensionManager
import com.frito.music.extensions.ExtensionState
import com.frito.music.extensions.ExtensionUIModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private val extensionManager = ExtensionManager(application)

    private val _extensions = MutableStateFlow<List<ExtensionUIModel>>(emptyList())
    val extensions: StateFlow<List<ExtensionUIModel>> = _extensions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadRegistry(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val registry = extensionManager.fetchRegistry(url)
            if (registry != null) {
                updateUIModels(registry.extensions)
            } else {
                _errorMessage.value = "Error al cargar el registro"
            }
            _isLoading.value = false
        }
    }

    private fun updateUIModels(infos: List<ExtensionInfo>) {
        val uiModels = infos.map { info ->
            val installedVersion = extensionManager.getInstalledVersion(info.id)
            val state = when {
                installedVersion == null -> ExtensionState.NOT_INSTALLED
                isNewerVersion(info.version, installedVersion) -> ExtensionState.UPDATE_AVAILABLE
                else -> ExtensionState.INSTALLED
            }
            ExtensionUIModel(info, state, 0f)
        }
        _extensions.value = uiModels
    }

    fun downloadExtension(extensionId: String) {
        val currentList = _extensions.value
        val extIndex = currentList.indexOfFirst { it.info.id == extensionId }
        if (extIndex == -1) return

        val uiModel = currentList[extIndex]
        
        updateExtensionState(extensionId, ExtensionState.DOWNLOADING, 0f)

        viewModelScope.launch {
            val success = extensionManager.downloadExtension(uiModel.info) { progress ->
                updateExtensionState(extensionId, ExtensionState.DOWNLOADING, progress)
            }
            
            if (success) {
                updateExtensionState(extensionId, ExtensionState.INSTALLED, 1f)
            } else {
                updateExtensionState(extensionId, ExtensionState.ERROR, 0f)
            }
        }
    }

    fun deleteExtension(extensionId: String, extensionName: String) {
        extensionManager.deleteExtension(extensionId, extensionName)
        updateExtensionState(extensionId, ExtensionState.NOT_INSTALLED, 0f)
    }

    private fun updateExtensionState(extensionId: String, newState: ExtensionState, newProgress: Float) {
        _extensions.value = _extensions.value.map {
            if (it.info.id == extensionId) {
                it.copy(state = newState, progress = newProgress)
            } else {
                it
            }
        }
    }

    private fun isNewerVersion(registryVersion: String, installedVersion: String): Boolean {
        val rParts = registryVersion.split(".").mapNotNull { it.toIntOrNull() }
        val iParts = installedVersion.split(".").mapNotNull { it.toIntOrNull() }

        val size = maxOf(rParts.size, iParts.size)
        for (i in 0 until size) {
            val rVal = rParts.getOrNull(i) ?: 0
            val iVal = iParts.getOrNull(i) ?: 0
            if (rVal > iVal) return true
            if (rVal < iVal) return false
        }
        return false
    }
}
