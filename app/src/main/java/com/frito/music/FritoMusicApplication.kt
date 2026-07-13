package com.frito.music

import android.app.Application
import com.frito.music.utils.potoken.PoTokenGenerator

class FritoMusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PoTokenGenerator.init(this)
    }
}
