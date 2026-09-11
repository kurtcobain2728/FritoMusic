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
        
        // Consultar archivos musicales y audios válidos, excluyendo directorios de sistema
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR ${MediaStore.Audio.Media.DATA} LIKE '%.opus' OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg') AND (${MediaStore.Audio.Media.DATA} NOT LIKE '%/Android/%')"
        val folderCoversCache = HashMap<String, String?>()

        try {
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
                    val path = cursor.getString(dataColumn) ?: continue
                    if (path.contains("/Android/", ignoreCase = true) ||
                        path.contains("/Alarms/", ignoreCase = true) ||
                        path.contains("/Notifications/", ignoreCase = true) ||
                        path.contains("/Ringtones/", ignoreCase = true)
                    ) continue

                    val rawTitle = cursor.getString(titleColumn)
                    val rawArtist = cursor.getString(artistColumn)
                    val rawAlbum = cursor.getString(albumColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val defaultAlbumUri = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else null

                    val isFritoMusic = path.contains("/FritoMusic/", ignoreCase = true)
                    val file = File(path)
                    val parentDir = file.parentFile

                    val finalArtist: String
                    val finalTitle: String
                    val finalAlbum: String
                    val resolvedAlbumUri: String?

                    if (isFritoMusic) {
                        val isArtistUnknown = rawArtist.isNullOrBlank() ||
                            rawArtist.equals("<unknown>", ignoreCase = true) ||
                            rawArtist.equals("Artista Desconocido", ignoreCase = true) ||
                            rawArtist.equals("Unknown Artist", ignoreCase = true) ||
                            rawArtist.equals("Unknown", ignoreCase = true)

                        val grandParent = parentDir?.parentFile
                        val hasAlbumSubfolder = grandParent != null &&
                            !grandParent.name.equals("FritoMusic", ignoreCase = true) &&
                            !grandParent.name.equals("Music", ignoreCase = true) &&
                            grandParent.name.isNotBlank()

                        finalArtist = if (!isArtistUnknown && !rawArtist.isNullOrBlank()) {
                            rawArtist
                        } else if (hasAlbumSubfolder) {
                            grandParent!!.name
                        } else if (parentDir != null && !parentDir.name.equals("FritoMusic", ignoreCase = true)) {
                            parentDir.name
                        } else if (file.name.contains(" - ")) {
                            file.nameWithoutExtension.replace("""^\d{1,3}\s*[-.]\s*""".toRegex(), "").substringBefore(" - ").trim()
                        } else {
                            "Artista Desconocido"
                        }

                        val isTitleUnknown = rawTitle.isNullOrBlank() ||
                            rawTitle.equals("<unknown>", ignoreCase = true) ||
                            rawTitle.equals("Desconocido", ignoreCase = true)

                        val titleCandidate = if (!isTitleUnknown && !rawTitle.isNullOrBlank()) {
                            rawTitle
                        } else if (file.name.contains(" - ")) {
                            file.nameWithoutExtension.substringAfter(" - ").trim()
                        } else {
                            file.nameWithoutExtension
                        }
                        finalTitle = titleCandidate.replace("""^\d{1,3}\s*[-.]\s*""".toRegex(), "").trim().ifEmpty { file.nameWithoutExtension }

                        val isAlbumUnknown = rawAlbum.isNullOrBlank() ||
                            rawAlbum.equals("<unknown>", ignoreCase = true) ||
                            rawAlbum.equals("Álbum Desconocido", ignoreCase = true)

                        finalAlbum = if (!isAlbumUnknown && !rawAlbum.isNullOrBlank()) {
                            rawAlbum
                        } else if (hasAlbumSubfolder) {
                            parentDir!!.name
                        } else {
                            "Álbum Desconocido"
                        }

                        val songCover = if (parentDir != null) File(parentDir, "${file.nameWithoutExtension}.jpg") else null
                        val parentPath = parentDir?.absolutePath
                        val folderCoverUri = if (parentPath != null) {
                            folderCoversCache.getOrPut(parentPath) {
                                val fc = File(parentDir, "cover.jpg")
                                if (fc.exists()) Uri.fromFile(fc).toString() else null
                            }
                        } else null

                        resolvedAlbumUri = when {
                            songCover?.exists() == true -> Uri.fromFile(songCover).toString()
                            folderCoverUri != null -> folderCoverUri
                            else -> defaultAlbumUri
                        }
                    } else {
                        finalArtist = rawArtist ?: "Artista Desconocido"
                        finalTitle = rawTitle ?: "Desconocido"
                        finalAlbum = rawAlbum ?: "Álbum Desconocido"
                        resolvedAlbumUri = defaultAlbumUri
                    }

                    val customTitle = CustomNamesManager.getCustomAudioTitle(path)
                    val resolvedTitle = customTitle ?: finalTitle

                    val audioFile = AudioFile(
                        id = cursor.getLong(idColumn),
                        title = resolvedTitle,
                        artist = finalArtist,
                        path = path,
                        durationMs = cursor.getLong(durationColumn),
                        sizeBytes = cursor.getLong(sizeColumn),
                        albumUri = resolvedAlbumUri,
                        album = finalAlbum,
                        dateAdded = cursor.getLong(dateAddedColumn)
                    )

                    if (parentDir != null) {
                        insertIntoTree(rootNode, parentDir, audioFile)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaScanner", "Error al escanear MediaStore: ${e.message}")
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
        val ancestors = mutableListOf<File>()
        var temp: File? = dir
        while (temp != null && temp.parentFile != null &&
            temp.absolutePath != "/" &&
            temp.absolutePath != "/storage/emulated/0" &&
            !temp.absolutePath.matches(Regex("""^/storage/[^/]+$"""))
        ) {
            ancestors.add(0, temp)
            temp = temp.parentFile
        }

        var currentNode = root
        var currentPath = ""
        
        for (i in parts.indices) {
            val part = parts[i]
            currentPath += "/$part"
            val nodeRealPath = if (i < ancestors.size) ancestors[i].absolutePath else ""
            val customFolderName = CustomNamesManager.getCustomFolderName(currentPath)
                ?: if (nodeRealPath.isNotBlank()) CustomNamesManager.getCustomFolderName(nodeRealPath) else null
            val displayName = customFolderName ?: part

            if (!currentNode.subfolders.containsKey(part)) {
                currentNode.subfolders[part] = FolderNode(name = displayName, path = currentPath, realPath = nodeRealPath)
            } else if (currentNode.subfolders[part]?.realPath.isNullOrBlank() && nodeRealPath.isNotBlank()) {
                currentNode.subfolders[part]?.realPath = nodeRealPath
            }
            currentNode = currentNode.subfolders[part]!!
        }
        if (currentNode.realPath.isBlank()) {
            currentNode.realPath = dir.absolutePath
        }
        currentNode.audios.add(audioFile)
    }
}
