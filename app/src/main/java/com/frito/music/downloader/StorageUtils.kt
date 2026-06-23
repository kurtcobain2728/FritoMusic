package com.frito.music.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object StorageUtils {
    
    /**
     * Sanitiza el nombre del archivo para que sea válido en todos los sistemas de archivos.
     */
    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    /**
     * Devuelve un par que contiene el Uri final donde escribir, y un OutputStream.
     * En Android 10+ (Q) usa MediaStore y el atributo RELATIVE_PATH.
     * En Android 9 e inferior usa File directo en Environment.getExternalStoragePublicDirectory.
     */
    fun createAudioFileStream(
        context: Context,
        artistName: String,
        albumName: String,
        trackName: String
    ): Pair<Uri, OutputStream>? {
        val safeArtist = sanitizeFilename(artistName).ifEmpty { "Desconocido" }
        val safeAlbum = sanitizeFilename(albumName)
        val safeTrack = sanitizeFilename(trackName).ifEmpty { "Pista Desconocida" }
        
        val fileName = "$safeTrack.mp3" // Defaulting to mp3, the downloader can detect MIME type later if needed
        val mimeType = "audio/mpeg"

        // La estructura será: Music/FritoM/{Artist}/{Album}/
        val relativePath = if (safeAlbum.isNotEmpty()) {
            "${Environment.DIRECTORY_MUSIC}/FritoM/$safeArtist/$safeAlbum"
        } else {
            "${Environment.DIRECTORY_MUSIC}/FritoM/$safeArtist"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, contentValues) ?: return null

            val outStream = context.contentResolver.openOutputStream(uri, "w") ?: return null
            return Pair(uri, outStream)
        } else {
            // Legacy storage (requiere WRITE_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            
            val fritoDir = File(publicDir, "FritoM")
            val artistDir = File(fritoDir, safeArtist)
            val targetDir = if (safeAlbum.isNotEmpty()) File(artistDir, safeAlbum) else artistDir
            
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            val targetFile = File(targetDir, fileName)
            val uri = Uri.fromFile(targetFile)
            return Pair(uri, targetFile.outputStream())
        }
    }

    /**
     * Marca el archivo como terminado (solo relevante para MediaStore en Android 10+).
     */
    fun commitAudioFile(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            try {
                context.contentResolver.update(uri, contentValues, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Elimina el archivo parcial si falla la descarga (MediaStore).
     */
    fun deleteAudioFile(context: Context, uri: Uri) {
        try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
