package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.YouTube

object YouTubeLoginManager {
    private const val PREFS_NAME = "youtube_login"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_VISITOR_DATA = "visitor_data"
    private const val KEY_DATA_SYNC_ID = "data_sync_id"
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_ACCOUNT_HANDLE = "account_handle"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(): Boolean {
        return !getCookie().isNullOrEmpty()
    }

    fun getCookie(): String? {
        return prefs?.getString(KEY_COOKIE, null)
    }

    fun getVisitorData(): String {
        return prefs?.getString(KEY_VISITOR_DATA, "") ?: ""
    }

    fun getDataSyncId(): String {
        return prefs?.getString(KEY_DATA_SYNC_ID, "") ?: ""
    }

    fun getAccountName(): String {
        return prefs?.getString(KEY_ACCOUNT_NAME, "") ?: ""
    }

    fun getAccountEmail(): String {
        return prefs?.getString(KEY_ACCOUNT_EMAIL, "") ?: ""
    }

    fun getAccountHandle(): String {
        return prefs?.getString(KEY_ACCOUNT_HANDLE, "") ?: ""
    }

    fun saveLogin(cookie: String, visitorData: String, dataSyncId: String) {
        prefs?.edit()?.apply {
            putString(KEY_COOKIE, cookie)
            putString(KEY_VISITOR_DATA, visitorData)
            putString(KEY_DATA_SYNC_ID, dataSyncId)
            apply()
        }
    }

    fun saveVisitorData(visitorData: String) {
        prefs?.edit()?.putString(KEY_VISITOR_DATA, visitorData)?.apply()
    }

    fun saveDataSyncId(dataSyncId: String) {
        prefs?.edit()?.putString(KEY_DATA_SYNC_ID, dataSyncId)?.apply()
    }

    fun saveAccountInfo(name: String, email: String, handle: String) {
        prefs?.edit()?.apply {
            putString(KEY_ACCOUNT_NAME, name)
            putString(KEY_ACCOUNT_EMAIL, email)
            putString(KEY_ACCOUNT_HANDLE, handle)
            apply()
        }
    }

    fun loadLoginToYouTube() {
        val cookie = getCookie()
        if (!cookie.isNullOrEmpty()) {
            YouTube.cookie = cookie
            YouTube.visitorData = getVisitorData()
            YouTube.dataSyncId = getDataSyncId()
        }
    }

    fun logout() {
        prefs?.edit()?.clear()?.apply()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
    }

    fun hasSeenTutorial(): Boolean {
        return prefs?.getBoolean("has_seen_stream_tutorial", false) ?: false
    }

    fun setTutorialSeen() {
        prefs?.edit()?.putBoolean("has_seen_stream_tutorial", true)?.apply()
    }
}
