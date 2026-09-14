package com.tvbykafi.app

import android.app.Application
import android.content.Context
import android.net.HttpsURLConnection
import android.os.Build
import androidx.multidex.MultiDex
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.FirebaseApp
import com.tvbykafi.app.util.DeviceUtils
import java.security.SSLContext

class App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // API 19 (KitKat) e 64K+ method er app manually multidex install korte hoy
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        enableTls12OnPreLollipop()
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
        }
    }

    // Android 4.4 e TLS 1.2 default enabled thake na — GitHub/Firebase er HTTPS
    // er jonno default SSL socket factory ke TLSv1.2 e set kora hoy.
    // Certificate validation unchanged — security weak kora hoy na.
    private fun enableTls12OnPreLollipop() {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                val sc = SSLContext.getInstance("TLSv1.2")
                sc.init(null, null, null)
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            } catch (_: Exception) {
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            Glide.get(this).clearMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }
}

@GlideModule
class TVGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val isTV = DeviceUtils.isTV(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isLowRam = am.isLowRamDevice

        val memoryCacheSize = if (isTV || isLowRam) {
            1024 * 1024 * 16 // 16MB
        } else {
            1024 * 1024 * 32 // 32MB
        }

        val diskCacheSize = if (isTV || isLowRam) {
            1024 * 1024 * 50  // 50MB
        } else {
            1024 * 1024 * 100 // 100MB
        }

        val format = if (isTV || isLowRam) {
            DecodeFormat.PREFER_RGB_565
        } else {
            DecodeFormat.PREFER_ARGB_8888
        }

        builder.setMemoryCache(LruResourceCache(memoryCacheSize.toLong()))
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSize.toLong()))
        builder.setDefaultRequestOptions(RequestOptions().format(format))
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
