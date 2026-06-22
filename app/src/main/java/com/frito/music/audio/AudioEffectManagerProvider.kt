package com.frito.music.audio

import android.content.Context

import android.annotation.SuppressLint

object AudioEffectManagerProvider {
    @SuppressLint("StaticFieldLeak")
    private var equalizerManager: EqualizerManager? = null

    fun getManager(context: Context): EqualizerManager {
        if (equalizerManager == null) {
            equalizerManager = EqualizerManager(context.applicationContext)
        }
        return equalizerManager!!
    }
}
