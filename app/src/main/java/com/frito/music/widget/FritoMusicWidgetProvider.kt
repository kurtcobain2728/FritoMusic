package com.frito.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.frito.music.MainActivity
import com.frito.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FritoMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, null, null, false, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                sendMediaKeyToService(context, KEY_PLAY_PAUSE)
            }
            ACTION_NEXT -> {
                sendMediaKeyToService(context, KEY_NEXT)
            }
            ACTION_PREV -> {
                sendMediaKeyToService(context, KEY_PREV)
            }
            ACTION_UPDATE_WIDGET -> {
                val title = intent.getStringExtra(EXTRA_TITLE)
                val artist = intent.getStringExtra(EXTRA_ARTIST)
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val albumUri = intent.getStringExtra(EXTRA_ALBUM_URI)

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, FritoMusicWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId, title, artist, isPlaying, albumUri)
                }
            }
        }
    }

    private fun sendMediaKeyToService(context: Context, command: String) {
        val intent = Intent(context, com.frito.music.service.MusicService::class.java).apply {
            action = command
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.frito.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.frito.music.ACTION_NEXT"
        const val ACTION_PREV = "com.frito.music.ACTION_PREV"
        const val ACTION_UPDATE_WIDGET = "com.frito.music.ACTION_UPDATE_WIDGET"

        const val KEY_PLAY_PAUSE = "ACTION_WIDGET_PLAY_PAUSE"
        const val KEY_NEXT = "ACTION_WIDGET_NEXT"
        const val KEY_PREV = "ACTION_WIDGET_PREV"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ALBUM_URI = "extra_album_uri"

        fun notifyWidgetUpdate(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            albumUri: String?
        ) {
            val intent = Intent(context, FritoMusicWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_ALBUM_URI, albumUri)
            }
            context.sendBroadcast(intent)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            albumUri: String?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            views.setTextViewText(R.id.widget_title, title?.ifBlank { "Frito Music" } ?: "Frito Music")
            views.setTextViewText(R.id.widget_artist, artist?.ifBlank { "Toca para reproducir" } ?: "Toca para reproducir")

            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // Abrir app al tocar el widget
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Controles
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                createActionPendingIntent(context, ACTION_PLAY_PAUSE, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                createActionPendingIntent(context, ACTION_NEXT, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                createActionPendingIntent(context, ACTION_PREV, 103)
            )

            // Cargar carátula de forma asíncrona usando Coil si hay Uri
            if (!albumUri.isNullOrBlank()) {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(albumUri)
                    .allowHardware(false) // Necesario para extraer Bitmap a RemoteViews
                    .target { drawable ->
                        val bitmap = (drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.widget_cover, bitmap)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                    .build()
                CoroutineScope(Dispatchers.IO).launch {
                    imageLoader.execute(request)
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_shortcut_library)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, FritoMusicWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
