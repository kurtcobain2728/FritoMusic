package com.frito.music.data.preferences

import android.content.Context
import android.content.SharedPreferences

object CrossfadePreferences {
    private const val PREFS_NAME = "frito_crossfade_prefs"
    private const val KEY_ENABLED = "crossfade_enabled"
    private const val KEY_DURATION = "crossfade_duration"
    private const val KEY_OFFLINE = "crossfade_offline"
    private const val KEY_ONLINE = "crossfade_online"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getDurationSeconds(context: Context): Int {
        return getPrefs(context).getInt(KEY_DURATION, 4).coerceIn(1, 12)
    }

    fun setDurationSeconds(context: Context, duration: Int) {
        getPrefs(context).edit().putInt(KEY_DURATION, duration.coerceIn(1, 12)).apply()
    }

    fun isApplyToOffline(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OFFLINE, true)
    }

    fun setApplyToOffline(context: Context, apply: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OFFLINE, apply).apply()
    }

    fun isApplyToOnline(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONLINE, true)
    }

    fun setApplyToOnline(context: Context, apply: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONLINE, apply).apply()
    }
}
