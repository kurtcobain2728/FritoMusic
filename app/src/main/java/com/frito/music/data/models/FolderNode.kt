package com.frito.music.data.models

data class FolderNode(
    val name: String,
    val path: String,
    val subfolders: MutableMap<String, FolderNode> = mutableMapOf(),
    val audios: MutableList<AudioFile> = mutableListOf()
) {
    // Cuenta cuántos audios hay en esta carpeta y sus subcarpetas
    fun getTotalAudioCount(): Int {
        var count = audios.size
        for (subfolder in subfolders.values) {
            count += subfolder.getTotalAudioCount()
        }
        return count
    }
}
