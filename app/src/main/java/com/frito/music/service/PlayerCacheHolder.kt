package com.frito.music.service

import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Acceso compartido a la caché de streams del reproductor (Media3 SimpleCache).
 *
 * Permite a capas externas (p. ej. PlayerViewModel al manejar un error de
 * reproducción) eliminar entradas corruptas/parciales, igual que hace
 * FridaMusic con performAggressiveCacheClear. Sin esto, una entrada corrupta
 * bajo el mismo videoId hacía fallar la canción una y otra vez (error
 * ERROR_CODE_IO_UNSPECIFIED con http=null, sin petición de red).
 *
 * La limpieza corre en Dispatchers.IO: removeResource hace E/S síncrona de
 * índice/disco y se invoca desde onPlayerError (hilo principal).
 */
object PlayerCacheHolder {

    @Volatile
    var cache: SimpleCache? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Elimina todas las entradas de caché asociadas a un videoId. */
    fun removeForVideo(videoId: String) {
        val c = cache ?: return
        scope.launch {
            runCatching {
                val targets = c.keys.filter { key -> key.contains(videoId) }
                targets.forEach { key -> c.removeResource(key) }
                android.util.Log.i(
                    "PlayerCacheHolder",
                    "Caché limpiada para $videoId (${targets.size} entradas)"
                )
            }.onFailure {
                android.util.Log.w(
                    "PlayerCacheHolder",
                    "No se pudo limpiar la caché de $videoId: ${it.message}"
                )
            }
        }
    }
}
