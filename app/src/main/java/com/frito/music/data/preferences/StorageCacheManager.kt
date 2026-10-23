package com.frito.music.data.preferences

import android.content.Context
import android.content.SharedPreferences
import coil.Coil
import com.frito.music.service.PlayerCacheHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

object StorageCacheManager {
    private const val PREFS_NAME = "frito_storage_cache_prefs"
    private const val KEY_MUSIC_CACHE_LIMIT = "music_cache_limit_bytes"

    // Límites requeridos: Mínimo 256MB, Predeterminado 1GB, Máximo 8GB
    const val MIN_CACHE_BYTES = 256L * 1024 * 1024
    const val DEFAULT_CACHE_BYTES = 1024L * 1024 * 1024 // 1 GB
    const val MAX_CACHE_BYTES = 8192L * 1024 * 1024 // 8 GB

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMusicCacheLimitBytes(context: Context): Long {
        return getPrefs(context).getLong(KEY_MUSIC_CACHE_LIMIT, DEFAULT_CACHE_BYTES)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }

    fun setMusicCacheLimitBytes(context: Context, bytes: Long) {
        val safeBytes = bytes.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        getPrefs(context).edit().putLong(KEY_MUSIC_CACHE_LIMIT, safeBytes).apply()
    }

    suspend fun getMusicCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val activeSpace = PlayerCacheHolder.cache?.cacheSpace ?: 0L
        val streamDir = File(context.cacheDir, "stream-cache")
        val dirSize = calculateDirectorySize(streamDir)
        maxOf(activeSpace, dirSize)
    }

    suspend fun getImageCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val coil = Coil.imageLoader(context)
        val coilDiskSize = runCatching { coil.diskCache?.size ?: 0L }.getOrDefault(0L)
        val coilDir = File(context.cacheDir, "image_cache")
        val altCoilDir = File(context.cacheDir, "coil")
        val dirSize = calculateDirectorySize(coilDir) + calculateDirectorySize(altCoilDir)
        maxOf(coilDiskSize, dirSize)
    }

    suspend fun clearMusicCache(context: Context): Unit = withContext(Dispatchers.IO) {
        try {
            PlayerCacheHolder.clearEntireCache()
        } catch (e: Exception) {
            android.util.Log.w("StorageCacheManager", "Error en PlayerCacheHolder.clear: ${e.message}")
        }
        val streamDir = File(context.cacheDir, "stream-cache")
        deleteDirectoryContents(streamDir)
    }

    suspend fun clearImageCache(context: Context): Unit = withContext(Dispatchers.IO) {
        try {
            val coil = Coil.imageLoader(context)
            coil.diskCache?.clear()
            coil.memoryCache?.clear()
        } catch (e: Exception) {
            android.util.Log.w("StorageCacheManager", "Error en Coil.clear: ${e.message}")
        }
        val coilDir = File(context.cacheDir, "image_cache")
        val altCoilDir = File(context.cacheDir, "coil")
        deleteDirectoryContents(coilDir)
        deleteDirectoryContents(altCoilDir)
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        }.getOrDefault(0L)
    }

    private fun deleteDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) file.deleteRecursively()
                else file.delete()
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val df = DecimalFormat("#.##")
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "${df.format(gb)} GB"
            mb >= 1.0 -> "${df.format(mb)} MB"
            kb >= 1.0 -> "${df.format(kb)} KB"
            else -> "$bytes B"
        }
    }
}
