package com.frito.music.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.FolderNode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(private val context: Context) {

    suspend fun scanLocalAudio(): FolderNode = withContext(Dispatchers.IO) {
        val rootNode = FolderNode(name = "Dispositivo", path = "/")

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_ADDED
        )
        
        // Excluimos tonos de llamada, alarmas, etc si es posible, y excluimos la carpeta Android
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%/Android/%'"

        context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                if (path == null) continue

                val albumId = cursor.getLong(albumIdColumn)
                val albumUri = Uri.parse("content://media/external/audio/albumart/$albumId").toString()

                val audioFile = AudioFile(
                    id = cursor.getLong(idColumn),
                    title = cursor.getString(titleColumn) ?: "Desconocido",
                    artist = cursor.getString(artistColumn) ?: "Artista Desconocido",
                    path = path,
                    durationMs = cursor.getLong(durationColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    albumUri = albumUri,
                    album = cursor.getString(albumColumn) ?: "Álbum Desconocido",
                    dateAdded = cursor.getLong(dateAddedColumn)
                )
                
                val file = File(path)
                val parentDir = file.parentFile
                if (parentDir != null) {
                    insertIntoTree(rootNode, parentDir, audioFile)
                }
            }
        }
        
        return@withContext rootNode
    }

    private fun insertIntoTree(root: FolderNode, dir: File, audioFile: AudioFile) {
        var relativePath = dir.absolutePath
        
        // Simplificar ruta de almacenamiento interno
        if (relativePath.startsWith("/storage/emulated/0")) {
            relativePath = relativePath.removePrefix("/storage/emulated/0")
        } else if (relativePath.startsWith("/storage/")) {
            // Simplificar ruta de tarjeta SD
            val parts = relativePath.split("/")
            if (parts.size >= 3) {
                val sdCardId = parts[2]
                relativePath = relativePath.replaceFirst("/storage/$sdCardId", "/Tarjeta SD")
            }
        }

        val parts = relativePath.split("/").filter { it.isNotEmpty() }
        var currentNode = root
        var currentPath = ""
        
        for (part in parts) {
            currentPath += "/$part"
            if (!currentNode.subfolders.containsKey(part)) {
                currentNode.subfolders[part] = FolderNode(name = part, path = currentPath)
            }
            currentNode = currentNode.subfolders[part]!!
        }
        currentNode.audios.add(audioFile)
    }
}
