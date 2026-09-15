package com.tvbykafi.app.data

import android.os.Build
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.util.DnsCompat
import com.tvbykafi.app.util.SslCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PlaylistRepository private constructor() {

    companion object {
        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/Kafi-1/kafi_tv-min_iptv-playlist/refs/heads/main/kafi_iptv_playlist.m3u"

        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository().also { instance = it }
            }
        }
    }

    // Purona TV gulo IPv6 address e connect kore fail kore (ENETUNREACH —
    // network e IPv6 route nai). OkHttp + custom DNS diye IPv4-first
    // resolve kora hoy, ar KitKat e TLS 1.2 + bundled root CA o ensure hoy.
    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .dns { host ->
                val addrs = DnsCompat.resolveIPv4First(host)
                if (addrs.isEmpty()) throw UnknownHostException(host)
                addrs
            }
        if (Build.VERSION.SDK_INT < 24) {
            try {
                builder.sslSocketFactory(SslCompat.socketFactory(), SslCompat.trustManager())
            } catch (_: Exception) {
            }
        }
        builder.build()
    }

    private var cachedChannels: List<Channel>? = null
    private var cacheTime = 0L
    private val cacheTtl = 30 * 60 * 1000L

    fun getCachedChannels(): List<Channel>? = cachedChannels

    suspend fun fetchPlaylist(url: String = DEFAULT_PLAYLIST_URL): List<Channel> {
        val now = System.currentTimeMillis()
        val cached = cachedChannels
        if (cached != null && (now - cacheTime) < cacheTtl) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            val content = downloadContent(url)
            val channels = M3UParser.parse(content)
            cachedChannels = channels
            cacheTime = System.currentTimeMillis()
            ChannelHolder.channels = channels
            channels
        }
    }

    private fun downloadContent(urlString: String): String {
        val request = okhttp3.Request.Builder()
            .url(urlString)
            .header("User-Agent", "TVbyKafi/2.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code()} loading playlist")
            }
            return response.body()?.string()
                ?: throw java.io.IOException("Empty response body")
        }
    }

    fun clearCache() {
        cachedChannels = null
        cacheTime = 0L
    }
}
