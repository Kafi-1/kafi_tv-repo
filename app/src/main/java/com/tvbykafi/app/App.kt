package com.tvbykafi.app

import android.app.Application
import android.content.Context
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
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class App : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // API 19 (KitKat) e 64K+ method er app manually multidex install korte hoy
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        enableLegacySslCompat()
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
        }
    }

    // Android 7.0 (API 24) er age device gulo (4.4 TV etc.) er trust store e
    // notun root CA gulo nai — ISRG Root X1 (Let's Encrypt, GitHub raw er chain)
    // ar Amazon Root CA 1 (channel logo host). ISRG Root X1 2015 e toiri,
    // KitKat er store 2013-er. Root cert gulo embed kore system roots er sathe
    // ADD kora hoy — certificate validation unchanged, security weak hoy na.
    // network_security_config API 24+ e kaj kore, tai ei manual path lagbe.
    private fun enableLegacySslCompat() {
        if (Build.VERSION.SDK_INT >= 24) return
        try {
            val trustManager = buildCompositeTrustManager()
            val sc = SSLContext.getInstance("TLSv1.2")
            sc.init(null, arrayOf(trustManager), null)
            // KitKat bug: "TLSv1.2" SSLContext theke banano socket er enabled
            // protocol default e TLSv1 thake — server (GitHub) TLS 1.0/1.1 reject
            // kore tai handshake fail kore. Prottekhta socket e TLSv1.2 force
            // korte wrapped factory lagbe (Google er official KitKat workaround).
            HttpsURLConnection.setDefaultSSLSocketFactory(Tls12SocketFactory(sc.socketFactory))
        } catch (_: Exception) {
        }
    }

    // System default trust manager + bundled root CA — jekono ekta trust korlei OK.
    // Hostname verification default HttpsURLConnection er — untouched.
    private fun buildCompositeTrustManager(): X509TrustManager {
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?)
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val bundledCerts = listOf(R.raw.isrgrootx1, R.raw.amazonrootca1)
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null)
        val cf = CertificateFactory.getInstance("X.509")
        bundledCerts.forEach { resId ->
            resources.openRawResource(resId).use { ins ->
                ks.setCertificateEntry("bundled_ca_$resId", cf.generateCertificate(ins))
            }
        }
        val bundledTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        bundledTmf.init(ks)
        val bundledTm = bundledTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                systemTm.checkClientTrusted(chain, authType)
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    systemTm.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    try {
                        bundledTm.checkServerTrusted(chain, authType)
                    } catch (_: CertificateException) {
                        throw e
                    }
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> =
                systemTm.acceptedIssuers + bundledTm.acceptedIssuers
        }
    }

    // KitKat e SSLContext("TLSv1.2") er socket gulo default e TLSv1 dia handshake
    // kore — ei wrapper prottekhta socket e TLSv1.2 enable kore dey.
    private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            patch(delegate.createSocket(s, host, port, autoClose) as SSLSocket)

        override fun createSocket(host: String, port: Int): Socket =
            patch(delegate.createSocket(host, port) as SSLSocket)

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            patch(delegate.createSocket(host, port, localHost, localPort) as SSLSocket)

        override fun createSocket(host: InetAddress, port: Int): Socket =
            patch(delegate.createSocket(host, port) as SSLSocket)

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            patch(delegate.createSocket(address, port, localAddress, localPort) as SSLSocket)

        private fun patch(socket: SSLSocket): SSLSocket {
            socket.enabledProtocols = arrayOf("TLSv1.2")
            return socket
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
