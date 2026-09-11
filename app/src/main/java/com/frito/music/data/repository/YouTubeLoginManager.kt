package com.frito.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object YouTubeLoginManager {
    private const val PREFS_NAME = "youtube_login"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_VISITOR_DATA = "visitor_data"
    private const val KEY_DATA_SYNC_ID = "data_sync_id"
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_ACCOUNT_HANDLE = "account_handle"
    private const val KEY_ACCOUNT_AVATAR = "account_avatar"

    private var prefs: SharedPreferences? = null

    private val _accountAvatar = MutableStateFlow("")
    val accountAvatar: StateFlow<String> = _accountAvatar.asStateFlow()

    private val _accountName = MutableStateFlow("")
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    private val _accountEmail = MutableStateFlow("")
    val accountEmail: StateFlow<String> = _accountEmail.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _accountAvatar.value = prefs?.getString(KEY_ACCOUNT_AVATAR, "") ?: ""
        _accountName.value = prefs?.getString(KEY_ACCOUNT_NAME, "") ?: ""
        _accountEmail.value = prefs?.getString(KEY_ACCOUNT_EMAIL, "") ?: ""
        _isLoggedIn.value = !getCookie().isNullOrEmpty()
    }

    fun isLoggedIn(): Boolean {
        return _isLoggedIn.value
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

    fun getAccountAvatar(): String {
        return prefs?.getString(KEY_ACCOUNT_AVATAR, "") ?: ""
    }

    fun saveLogin(cookie: String, visitorData: String, dataSyncId: String) {
        prefs?.edit()?.apply {
            putString(KEY_COOKIE, cookie)
            putString(KEY_VISITOR_DATA, visitorData)
            putString(KEY_DATA_SYNC_ID, dataSyncId)
            apply()
        }
        _isLoggedIn.value = true
    }

    fun saveVisitorData(visitorData: String) {
        prefs?.edit()?.putString(KEY_VISITOR_DATA, visitorData)?.apply()
    }

    fun saveDataSyncId(dataSyncId: String) {
        prefs?.edit()?.putString(KEY_DATA_SYNC_ID, dataSyncId)?.apply()
    }

    fun saveAccountInfo(name: String, email: String, handle: String, avatarUrl: String = "") {
        prefs?.edit()?.apply {
            putString(KEY_ACCOUNT_NAME, name)
            putString(KEY_ACCOUNT_EMAIL, email)
            putString(KEY_ACCOUNT_HANDLE, handle)
            putString(KEY_ACCOUNT_AVATAR, avatarUrl)
            apply()
        }
        _accountName.value = name
        _accountEmail.value = email
        _accountAvatar.value = avatarUrl
    }

    fun loadLoginToYouTube() {
        val cookie = getCookie()
        if (!cookie.isNullOrEmpty()) {
            YouTube.cookie = cookie
            YouTube.visitorData = getVisitorData().ifBlank { null }
            YouTube.dataSyncId = getDataSyncId().ifBlank { null }
            _isLoggedIn.value = true
            fetchAccountInfo()
        } else {
            _isLoggedIn.value = false
        }
    }

    fun fetchAccountInfo() {
        if (!isLoggedIn()) return
        CoroutineScope(Dispatchers.IO).launch {
            YouTube.accountInfo().onSuccess { accountInfo ->
                saveAccountInfo(
                    name = accountInfo.name,
                    email = accountInfo.email ?: "",
                    handle = accountInfo.channelHandle ?: "",
                    avatarUrl = accountInfo.thumbnailUrl ?: ""
                )
            }.onFailure { error ->
                android.util.Log.e("YouTubeLoginManager", "Failed to fetch account info", error)
            }
        }
    }

    fun logout() {
        prefs?.edit()?.clear()?.apply()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        _accountAvatar.value = ""
        _accountName.value = ""
        _accountEmail.value = ""
        _isLoggedIn.value = false
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
    }

    fun hasSeenTutorial(): Boolean {
        return prefs?.getBoolean("has_seen_stream_tutorial", false) ?: false
    }

    fun setTutorialSeen() {
        prefs?.edit()?.putBoolean("has_seen_stream_tutorial", true)?.apply()
    }
}
