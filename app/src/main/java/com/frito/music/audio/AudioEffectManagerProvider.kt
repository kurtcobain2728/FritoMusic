package com.frito.music.audio

import android.content.Context

object AudioEffectManagerProvider {
    private var equalizerManager: EqualizerManager? = null

    fun getManager(context: Context): EqualizerManager {
        if (equalizerManager == null) {
            equalizerManager = EqualizerManager(context.applicationContext)
        }
        return equalizerManager!!
    }
}
