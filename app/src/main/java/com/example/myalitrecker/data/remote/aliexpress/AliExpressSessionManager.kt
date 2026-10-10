package com.example.myalitrecker.data.remote.aliexpress

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager

class AliExpressSessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "aliexpress_session_prefs"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // Key cookie names that indicate logged in session on AliExpress
        private val AUTH_COOKIE_MARKERS = listOf(
            "xman_us_t",
            "login_aliyunid_ticket",
            "intl_common_token",
            "aep_usuc_f"
        )
    }

    fun saveCookies(cookieString: String) {
        prefs.edit()
            .putString(KEY_COOKIES, cookieString)
            .apply()
    }

    fun getCookies(): String {
        return prefs.getString(KEY_COOKIES, "") ?: ""
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun updateLastSyncTime() {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun isLoggedIn(): Boolean {
        val cookies = getCookies()
        if (cookies.isBlank()) return false
        // Must contain at least two AliExpress authentication markers
        var matches = 0
        for (marker in AUTH_COOKIE_MARKERS) {
            if (cookies.contains(marker)) {
                matches++
            }
        }
        return matches >= 1
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
