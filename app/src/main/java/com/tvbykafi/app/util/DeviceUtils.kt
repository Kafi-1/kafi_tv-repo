package com.tvbykafi.app.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceUtils {

    fun getHardwareFingerprint(ctx: Context): String {
        val raw = StringBuilder().apply {
            append(Build.BOARD)
            append(Build.BRAND)
            append(Build.DEVICE)
            append(Build.HARDWARE)
            append(Build.MANUFACTURER)
            append(Build.MODEL)
            append(Build.PRODUCT)
            try {
                append(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID))
            } catch (_: Exception) {}
        }.toString()

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(raw.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16).uppercase()
    }

    fun isTV(ctx: Context): Boolean {
        val uiMode = ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_TYPE_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun getOptimalSpanCount(ctx: Context): Int {
        val displayMetrics = ctx.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        return when {
            dpWidth >= 1200 -> 10
            dpWidth >= 900 -> 8
            dpWidth >= 600 -> 6
            dpWidth >= 400 -> 4
            else -> 3
        }
    }
}
