package com.tvbykafi.app.util

import android.content.Context
import android.os.Build
import com.tvbykafi.app.R
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

/**
 * Android 7.0 (API 24) er age device gulo (4.4 TV etc.) er jonno SSL compat:
 * 1. Trust store e notun root CA nai (ISRG Root X1 2015 e toiri, KitKat store
 *    2013-er) — bundled root gulo system roots er sathe ADD kora hoy.
 * 2. KitKat bug: "TLSv1.2" SSLContext er socket e default enabled protocol
 *    TLSv1 thake — prottekhta socket e TLSv1.2 force korte hoy.
 * Certificate validation puro intact — hostname verify + chain check baki thake.
 */
object SslCompat {

    @Volatile private var appContext: Context? = null
    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedTrustManager: X509TrustManager? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        installDefaults()
    }

    private fun installDefaults() {
        if (Build.VERSION.SDK_INT >= 24) return
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory())
        } catch (_: Exception) {
        }
    }

    fun socketFactory(): SSLSocketFactory {
        cachedFactory?.let { return it }
        synchronized(this) {
            cachedFactory?.let { return it }
            val sc = SSLContext.getInstance("TLSv1.2")
            sc.init(null, arrayOf(trustManager()), null)
            return Tls12SocketFactory(sc.socketFactory).also { cachedFactory = it }
        }
    }

    fun trustManager(): X509TrustManager {
        cachedTrustManager?.let { return it }
        synchronized(this) {
            cachedTrustManager?.let { return it }
            val ctx = appContext ?: throw IllegalStateException("SslCompat.init not called")
            return buildCompositeTrustManager(ctx).also { cachedTrustManager = it }
        }
    }

    // System default trust manager + bundled root CA — jekono ekta trust korlei OK.
    private fun buildCompositeTrustManager(ctx: Context): X509TrustManager {
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?)
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null)
        val cf = CertificateFactory.getInstance("X.509")
        listOf(R.raw.isrgrootx1, R.raw.amazonrootca1).forEach { resId ->
            ctx.resources.openRawResource(resId).use { ins ->
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
            socket.enabledProtocols = socket.supportedProtocols.intersect(
                listOf("TLSv1.3", "TLSv1.2")
            ).toTypedArray()
            return socket
        }
    }
}
