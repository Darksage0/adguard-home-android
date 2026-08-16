package com.adguard.home.data.remote.ssl

import com.adguard.home.data.local.CredentialStore
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SslConfig {

    private val systemTrustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    fun sha256Fingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
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
     *
     * Trust-on-first-use pinning: enabling "trust self-signed certificate" does not disable
     * certificate validation outright. Instead the first certificate seen is pinned by its
     * SHA-256 fingerprint (persisted via CredentialStore), and every later handshake is checked
     * against that pin. A certificate that doesn't match the pin is rejected -- this bounds the
     * exposure to "the one certificate this app was first shown" rather than "any certificate
     * from any attacker who can intercept this connection."
     */
    class DynamicTrustManager(
        private val credentialStore: CredentialStore
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val config = runBlocking { credentialStore.getServerConfig() }
            if (!config.trustSelfSigned) {
                systemTrustManager.checkServerTrusted(chain, authType)
                return
            }

            val leaf = chain?.firstOrNull()
                ?: throw CertificateException("No certificate presented by server")
            val fingerprint = sha256Fingerprint(leaf)
            val pinned = config.pinnedCertSha256

            if (pinned == null) {
                // First connection since the toggle was enabled (or re-enabled): trust and
                // remember this exact certificate.
                runBlocking { credentialStore.pinCertificate(fingerprint) }
                return
            }

            if (fingerprint != pinned) {
                throw CertificateException(
                    "Server certificate changed since it was trusted. Re-enable 'trust " +
                        "self-signed certificate' in Settings to accept the new certificate."
                )
            }
            // Fingerprint matches the pin -- same certificate this app was already shown.
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
    }

    fun dynamicSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return sslContext.socketFactory
    }

    /**
     * Companion hostname verifier for [DynamicTrustManager]. When trust-self-signed is on,
     * [DynamicTrustManager.checkServerTrusted] has already pinned/verified the exact certificate
     * bytes -- a stronger guarantee than hostname matching, which self-signed LAN certs (issued
     * for a bare IP or a `.local` name) frequently fail anyway. Otherwise defers to the platform
     * default verifier.
     */
    fun dynamicHostnameVerifier(credentialStore: CredentialStore): HostnameVerifier {
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { hostname, session ->
            val trustSelfSigned = runBlocking { credentialStore.getServerConfig().trustSelfSigned }
            trustSelfSigned || defaultVerifier.verify(hostname, session)
        }
    }
}
