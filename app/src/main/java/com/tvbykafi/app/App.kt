package com.tvbykafi.app

import android.app.Application
import android.content.Context
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

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
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
