package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the LetsFG bearer token for this app.
 *
 * The token CANNOT be read from Termux's `~/.letsfg/config.json` - that file
 * lives under Termux's private data directory, which is unreadable from a
 * normal app UID. So the app runs its own OAuth flow and keeps its own copy.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("letsfg_auth", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    /** A manually pasted bearer token, used when OAuth is not wanted. */
    var manualToken: String?
        get() = prefs.getString(KEY_MANUAL, null)
        set(value) = prefs.edit().putString(KEY_MANUAL, value).apply()

    fun hasCredentials(): Boolean =
        !accessToken.isNullOrBlank() || !manualToken.isNullOrBlank()

    fun currentToken(): String? {
        manualToken?.takeIf { it.isNotBlank() }?.let { return it }
        val token = accessToken
        return if (!token.isNullOrBlank() && System.currentTimeMillis() < expiresAt - SKEW_MS) token else null
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_MANUAL = "manual_token"
        private const val SKEW_MS = 60_000L
    }
}
