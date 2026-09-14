package com.tvbykafi.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

object NetworkUtil {

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        // API 19: activeNetwork/NetworkCapabilities nei — purono API use korte hoy
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    // API 21+ e NetworkCallback, API 19 e CONNECTIVITY_ACTION broadcast —
    // dui path e ek monitor wrapper diye common interface
    class NetworkMonitor private constructor(
        private val onAvailable: () -> Unit,
        private val onLost: () -> Unit
    ) {
        private var callback: ConnectivityManager.NetworkCallback? = null
        private var receiver: BroadcastReceiver? = null

        fun stop(ctx: Context) {
            if (callback != null) {
                try {
                    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
                callback = null
            }
            if (receiver != null) {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                receiver = null
            }
        }

        companion object {
            fun start(
                ctx: Context,
                onAvailable: () -> Unit,
                onLost: () -> Unit
            ): NetworkMonitor {
                val monitor = NetworkMonitor(onAvailable, onLost)
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            onAvailable()
                        }
                        override fun onLost(network: Network) {
                            onLost()
                        }
                    }
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, callback)
                    monitor.callback = callback
                } else {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (isOnline(context)) onAvailable() else onLost()
                        }
                    }
                    ctx.registerReceiver(
                        receiver,
                        IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                    )
                    monitor.receiver = receiver
                }
                return monitor
            }
        }
    }
}
