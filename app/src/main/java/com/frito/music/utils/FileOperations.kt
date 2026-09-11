package com.frito.music.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Log
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.FolderNode
import com.frito.music.data.repository.CustomNamesManager
import com.frito.music.downloader.StorageUtils
import java.io.File

/**
 * Operaciones seguras sobre el sistema de archivos y MediaStore para renombrar y eliminar
 * archivos de música y carpetas locales sin pérdida de datos.
 */
object FileOperations {

    fun renameAudio(context: Context, audio: AudioFile, newTitle: String): Boolean {
        val safeTitle = StorageUtils.sanitizeFilename(newTitle).trim()
        if (safeTitle.isBlank()) return false

        val oldFile = File(audio.path)
        val ext = oldFile.extension.ifEmpty { "mp3" }
        val newFile = File(oldFile.parentFile, "$safeTitle.$ext")

        var finalPath = audio.path

        // 1. Intentar renombrar el archivo físico en el almacenamiento
        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
            try {
                val renamed = oldFile.renameTo(newFile)
                if (renamed && newFile.exists()) {
                    finalPath = newFile.absolutePath
                }
            } catch (e: Exception) {
                Log.w("FileOperations", "No se pudo renombrar en disco: ${e.message}")
            }
        }

        // 2. Persistir el nuevo título personalizado en CustomNamesManager
        CustomNamesManager.setCustomAudioTitle(audio.path, finalPath, safeTitle)

        // 3. Actualizar la fila en MediaStore DIRECTAMENTE (¡NUNCA borrar la fila!)
        try {
            val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.id)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, safeTitle)
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle.$ext")
                if (finalPath != audio.path) {
                    @Suppress("DEPRECATION")
                    put(MediaStore.Audio.Media.DATA, finalPath)
                }
            }
            context.contentResolver.update(audioUri, values, null, null)
        } catch (e: Exception) {
            Log.w("FileOperations", "Error al actualizar fila de MediaStore: ${e.message}")
        }

        // 4. Notificar al MediaScanner si cambió la ruta en disco
        if (finalPath != audio.path) {
            val targetFile = File(finalPath)
            if (targetFile.exists()) {
                StorageUtils.scanAudioFile(context, targetFile, title = safeTitle)
            }
        }

        return true
    }

    fun renameFolder(context: Context, folder: FolderNode, newName: String): Boolean {
        val safeName = StorageUtils.sanitizeFilename(newName).trim()
        if (safeName.isBlank()) return false

        val oldDir = resolveFolderDirectory(folder)
        var finalDirPath = folder.realPath

        if (oldDir != null && oldDir.exists() && oldDir.isDirectory) {
            val newDir = File(oldDir.parentFile, safeName)
            if (oldDir.absolutePath != newDir.absolutePath) {
                try {
                    val renamed = oldDir.renameTo(newDir)
                    if (renamed && newDir.exists()) {
                        finalDirPath = newDir.absolutePath

                        // Actualizar rutas en MediaStore para audios contenidos
                        val oldPathPrefix = oldDir.absolutePath
                        val newPathPrefix = newDir.absolutePath
                        try {
                            val cursor = context.contentResolver.query(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
                                "${MediaStore.Audio.Media.DATA} LIKE ?",
                                arrayOf("$oldPathPrefix%"),
                                null
                            )
                            cursor?.use { c ->
                                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                while (c.moveToNext()) {
                                    val id = c.getLong(idCol)
                                    val currentData = c.getString(dataCol)
                                    if (currentData != null && currentData.startsWith(oldPathPrefix)) {
                                        val updatedData = currentData.replaceFirst(oldPathPrefix, newPathPrefix)
                                        val itemUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                        val cv = ContentValues().apply {
                                            @Suppress("DEPRECATION")
                                            put(MediaStore.Audio.Media.DATA, updatedData)
                                        }
                                        context.contentResolver.update(itemUri, cv, null, null)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("FileOperations", "Error al actualizar registros de carpeta en MediaStore: ${e.message}")
                        }

                        scanDirectoryRecursively(context, newDir)
                    }
                } catch (e: Exception) {
                    Log.w("FileOperations", "No se pudo renombrar carpeta en disco: ${e.message}")
                }
            }
        }

        // Persistir nombre personalizado de carpeta
        CustomNamesManager.setCustomFolderName(folder.path, folder.path, safeName)
        if (finalDirPath.isNotBlank()) {
            CustomNamesManager.setCustomFolderName(folder.realPath, finalDirPath, safeName)
        }

        return true
    }

    fun deleteAudio(context: Context, audio: AudioFile): Boolean {
        return try {
            val file = File(audio.path)
            if (file.exists()) {
                file.delete()
            }
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(audio.id.toString())
            )
            true
        } catch (e: Exception) {
            Log.e("FileOperations", "Error al eliminar audio: ${e.message}")
            false
        }
    }

    fun deleteFolder(context: Context, folder: FolderNode): Boolean {
        return try {
            val dir = resolveFolderDirectory(folder)
            val dirPath = dir?.absolutePath ?: folder.realPath
            if (dir != null && dir.exists()) {
                dir.deleteRecursively()
            }
            if (dirPath.isNotBlank()) {
                context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DATA} LIKE ?",
                    arrayOf("$dirPath%")
                )
            }
            true
        } catch (e: Exception) {
            Log.e("FileOperations", "Error al eliminar carpeta: ${e.message}")
            false
        }
    }

    fun resolveFolderDirectory(folder: FolderNode): File? {
        if (folder.realPath.isNotBlank()) {
            val f = File(folder.realPath)
            if (f.exists() && f.isDirectory) return f
        }
        val firstAudioPath = folder.audios.firstOrNull()?.path
        if (!firstAudioPath.isNullOrBlank()) {
            val parent = File(firstAudioPath).parentFile
            if (parent != null && parent.exists()) return parent
        }
        return null
    }

    private fun scanDirectoryRecursively(context: Context, dir: File) {
        val files = dir.listFiles() ?: return
        val audioPaths = mutableListOf<String>()
        val mimeTypes = mutableListOf<String>()

        fun walk(f: File) {
            if (f.isDirectory) {
                f.listFiles()?.forEach { walk(it) }
            } else {
                val ext = f.extension.lowercase()
                if (ext in listOf("mp3", "m4a", "flac", "opus", "ogg", "wav", "aac")) {
                    audioPaths.add(f.absolutePath)
                    mimeTypes.add(
                        when (ext) {
                            "flac" -> "audio/flac"
                            "m4a", "aac" -> "audio/mp4"
                            "opus" -> "audio/opus"
                            "ogg" -> "audio/ogg"
                            "wav" -> "audio/wav"
                            else -> "audio/mpeg"
                        }
                    )
                }
            }
        }
        walk(dir)

        if (audioPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(
                context,
                audioPaths.toTypedArray(),
                mimeTypes.toTypedArray(),
                null
            )
        }
    }
}
