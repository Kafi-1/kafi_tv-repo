package com.tvbykafi.app.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object PrefsManager {
    private const val PREFS_NAME = "iptv_prefs"
    private const val KEY_USER_ID = "user_doc_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_CHANNEL = "last_channel_chno"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_USER_ID, id).apply()
    }

    fun getUserId(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_ID, null)

    fun clearUser(ctx: Context) {
        prefs(ctx).edit().remove(KEY_USER_ID).apply()
    }

    fun getDeviceId(ctx: Context): String {
        val p = prefs(ctx)
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(9).uppercase()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun saveLastChannel(ctx: Context, chno: Int) {
        prefs(ctx).edit().putInt(KEY_LAST_CHANNEL, chno).apply()
    }

    fun getLastChannel(ctx: Context): Int =
        prefs(ctx).getInt(KEY_LAST_CHANNEL, -1)
}
