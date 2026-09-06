package com.tvbykafi.app.data

import com.tvbykafi.app.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "TVbyKafi/2.0")
        try {
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    fun clearCache() {
        cachedChannels = null
        cacheTime = 0L
    }
}
