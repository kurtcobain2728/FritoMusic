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
     *
     * @param extension extensión real del archivo ("flac", "mp3", "m4a", "opus", "ogg").
     */
    fun createAudioFileStream(
        context: Context,
        artistName: String,
        albumName: String,
        trackName: String,
        extension: String = "mp3"
    ): Pair<Uri, OutputStream>? {
        val safeArtist = sanitizeFilename(artistName).ifEmpty { "Desconocido" }
        val safeAlbum = sanitizeFilename(albumName)
        val safeTrack = sanitizeFilename(trackName).ifEmpty { "Pista Desconocida" }

        val cleanExtension = extension.removePrefix(".").lowercase().ifEmpty { "mp3" }
        val mimeType = when (cleanExtension) {
            "flac" -> "audio/flac"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
        val fileName = "$safeTrack.$cleanExtension"

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

            val outStream = context.contentResolver.openOutputStream(uri, "w")
            if (outStream == null) {
                // No dejar filas IS_PENDING huérfanas en MediaStore
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }
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

    /**
     * Crea un archivo físico directamente en /storage/emulated/0/FritoMusic/{safeArtist}/{safeTrack}.{ext}
     * Si no existe la carpeta FritoMusic en la raíz, la crea.
     * Si no existe la carpeta del Artista, la crea.
     */
    fun createDirectAudioFile(
        artistName: String,
        trackName: String,
        extension: String = "mp3"
    ): File {
        val safeArtist = sanitizeFilename(artistName).ifEmpty { "Desconocido" }
        val safeTrack = sanitizeFilename(trackName).ifEmpty { "Pista Desconocida" }
        val cleanExtension = extension.removePrefix(".").lowercase().ifEmpty { "mp3" }
        val fileName = "$safeTrack.$cleanExtension"

        @Suppress("DEPRECATION")
        val rootDir = Environment.getExternalStorageDirectory()
        val fritoMusicDir = File(rootDir, "FritoMusic")
        val canUseRoot = runCatching {
            (fritoMusicDir.exists() || fritoMusicDir.mkdirs()) && fritoMusicDir.canWrite()
        }.getOrDefault(false)

        return if (canUseRoot) {
            val artistDir = File(fritoMusicDir, safeArtist)
            if (!artistDir.exists()) artistDir.mkdirs()
            File(artistDir, fileName)
        } else {
            // Fallback a Music/FritoMusic si el sistema no permite escribir directamente en la raíz
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val fallbackFrito = File(publicDir, "FritoMusic")
            if (!fallbackFrito.exists()) fallbackFrito.mkdirs()
            val artistDir = File(fallbackFrito, safeArtist)
            if (!artistDir.exists()) artistDir.mkdirs()
            File(artistDir, fileName)
        }
    }

    /**
     * Notifica al MediaScanner de Android para que indexe inmediatamente el archivo descargado
     * y aparezca de inmediato en "Inicio" de FritoMusic y en todo el sistema.
     */
    fun scanAudioFile(
        context: Context,
        file: File,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        onComplete: ((Uri?) -> Unit)? = null
    ) {
        val cleanExtension = file.extension.lowercase()
        val mimeType = when (cleanExtension) {
            "flac" -> "audio/flac"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "audio/mpeg"
        }
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { _, uri ->
            if (uri != null && (title != null || artist != null || album != null)) {
                runCatching {
                    val values = ContentValues().apply {
                        if (!title.isNullOrBlank()) put(MediaStore.Audio.Media.TITLE, title)
                        if (!artist.isNullOrBlank()) put(MediaStore.Audio.Media.ARTIST, artist)
                        if (!album.isNullOrBlank()) put(MediaStore.Audio.Media.ALBUM, album)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }.onFailure {
                    android.util.Log.w("StorageUtils", "No se pudo actualizar metadatos MediaStore: ${it.message}")
                }
            }
            onComplete?.invoke(uri)
        }
    }
}

