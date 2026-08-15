package com.adguard.home.data.remote.ssl

import com.adguard.home.data.local.CredentialStore
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SslConfig {

    val permissiveTrustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val permissiveSslSocketFactory: SSLSocketFactory = run {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(permissiveTrustManager), SecureRandom())
        sslContext.socketFactory
    }

    private val systemTrustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * A trust manager for the app's main (post-setup) OkHttpClient singleton that re-reads the
     * live "trust self-signed certificate" setting (§4.6 / §7.2) on every TLS handshake instead
     * of a fixed decision baked in once.
     *
     * Previously NetworkModule.provideOkHttpClient read this flag a single time via
     * `runBlocking { credentialStore.serverConfigFlow.firstOrNull() }` while building the
     * @Singleton OkHttpClient. That had two bugs: it ran synchronous DataStore disk I/O on
     * whichever thread first resolved the Hilt graph -- in practice the main thread, since
     * DashboardViewModel/etc are created during Compose composition -- which is exactly the
     * main-thread DataStore I/O the spec (§4.10) says to avoid; and because the client is a
     * singleton, the trust decision was then frozen for the life of the process, so toggling
     * the setting in Settings silently did nothing until the app was force-killed and
     * restarted, violating "changing settings takes effect immediately, no app restart".
     *
     * checkServerTrusted/checkClientTrusted are invoked by OkHttp during the TLS handshake,
     * which for a suspend Retrofit call always happens on a background OkHttp call thread, so
     * the runBlocking read here is safe and does not touch the main thread.
     */
    class DynamicTrustManager(
        private val credentialStore: CredentialStore
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val trustSelfSigned = runBlocking { credentialStore.getServerConfig().trustSelfSigned }
            if (trustSelfSigned) {
                // User explicitly opted in (default OFF) to trusting this LAN server's certificate.
                return
            }
            systemTrustManager.checkServerTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
    }

    fun dynamicSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return sslContext.socketFactory
    }

    /**
     * Companion hostname verifier for [DynamicTrustManager]: skips hostname verification only
     * when the live trust-self-signed setting is on (self-signed LAN certs are frequently
     * issued for an IP or a `.local` name the cert's CN/SAN won't match), otherwise defers to
     * the platform default verifier.
     */
    fun dynamicHostnameVerifier(credentialStore: CredentialStore): HostnameVerifier {
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { hostname, session ->
            val trustSelfSigned = runBlocking { credentialStore.getServerConfig().trustSelfSigned }
            trustSelfSigned || defaultVerifier.verify(hostname, session)
        }
    }
}
