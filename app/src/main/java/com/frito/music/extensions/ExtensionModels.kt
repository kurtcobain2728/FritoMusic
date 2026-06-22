package com.frito.music.extensions

data class ExtensionRegistry(
    val version: Int,
    val updatedAt: String,
    val extensions: List<ExtensionInfo>
)

data class ExtensionInfo(
    val id: String,
    val name: String,
    val displayName: String,
    val version: String,
    val description: String,
    val downloadUrl: String,
    val category: String,
    val tags: List<String>,
    val downloads: Int,
    val updatedAt: String,
    val minAppVersion: String,
    val iconUrl: String? = null
)

enum class ExtensionState {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    ERROR
}

data class ExtensionUIModel(
    val info: ExtensionInfo,
    val state: ExtensionState,
    val progress: Float = 0f // Para mostrar barra de progreso de descarga
)
