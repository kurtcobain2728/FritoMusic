package com.frito.music

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.frito.music.data.network.yt.YouTubeRepository
import com.frito.music.downloader.OnlineMusicDownloadWorker
import com.frito.music.utils.potoken.PoTokenGenerator

class FritoMusicApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        PoTokenGenerator.init(this)
        // Carga el último cliente de stream exitoso (arranque rápido de la 1ª canción)
        YouTubeRepository.init(this)
        OnlineMusicDownloadWorker.createNotificationChannels(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
