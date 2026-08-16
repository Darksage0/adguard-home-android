package com.adguard.home.data.repository

import com.adguard.home.data.local.CredentialStore
import com.adguard.home.data.local.model.ServerConfig
import com.adguard.home.data.remote.AdGuardApi
import com.adguard.home.data.remote.interceptor.AuthInterceptor
import com.adguard.home.data.remote.interceptor.DynamicHostInterceptor
import com.adguard.home.data.remote.ssl.SslConfig
import com.adguard.home.di.AppJson
import com.adguard.home.di.DynamicOkHttpClient
import com.adguard.home.di.IoDispatcher
import com.adguard.home.domain.model.ConnectionTestResult
import com.adguard.home.domain.model.NetworkErrorType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager

@Singleton
class ServerSettingsRepository @Inject constructor(
    private val credentialStore: CredentialStore,
    private val dynamicHostInterceptor: DynamicHostInterceptor,
    private val authInterceptor: AuthInterceptor,
    @DynamicOkHttpClient private val baseOkHttpClient: OkHttpClient,
    @AppJson private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    val serverConfigFlow: Flow<ServerConfig> = credentialStore.serverConfigFlow

    /**
     * Used only by [testConnection]: accepts any certificate (there's nothing saved to pin
     * against yet -- this is the first-contact discovery step) but records the leaf
     * certificate's fingerprint so it can become the trust-on-first-use pin if the user goes on
     * to save this connection. Never itself persists anything.
     */
    private class CapturingTrustManager : X509TrustManager {
        var capturedFingerprint: String? = null
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull()
                ?: throw CertificateException("No certificate presented by server")
            capturedFingerprint = SslConfig.sha256Fingerprint(leaf)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    suspend fun getServerConfig(): ServerConfig = withContext(ioDispatcher) {
        credentialStore.getServerConfig()
    }

    suspend fun testConnection(
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        trustSelfSigned: Boolean
    ): ConnectionTestResult = withContext(ioDispatcher) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val baseUrl = "$protocol://$cleanHost:$port/"

        val capturingTrustManager = CapturingTrustManager()

        try {
            val okHttpBuilder = baseOkHttpClient.newBuilder()
            if (trustSelfSigned && protocol.equals("https", ignoreCase = true)) {
                okHttpBuilder.sslSocketFactory(
                    SslConfig.dynamicSslSocketFactory(capturingTrustManager),
                    capturingTrustManager
                )
                okHttpBuilder.hostnameVerifier { _, _ -> true }
            }

            // The test client is built from baseOkHttpClient, which does NOT include the
            // app-wide AuthInterceptor/DynamicHostInterceptor (those only wrap the singleton
            // OkHttpClient created in NetworkModule.provideOkHttpClient, used after a config is
            // saved). Setting authInterceptor.temporaryAuthHeader here had no effect on this
            // client's requests, so every test connection was sent with no Authorization header
            // at all and always failed with 401 -- even with the correct username/password.
            // Fix: attach the Basic Auth header directly on this client's own interceptor chain.
            val authHeader = if (username.isNotBlank() && password.isNotBlank()) {
                Credentials.basic(username, password)
            } else {
                null
            }

            if (authHeader != null) {
                okHttpBuilder.addInterceptor { chain ->
                    val authorizedRequest = chain.request().newBuilder()
                        .header("Authorization", authHeader)
                        .build()
                    chain.proceed(authorizedRequest)
                }
            }

            val testOkHttpClient = okHttpBuilder.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(testOkHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val testApi = retrofit.create(AdGuardApi::class.java)

            val status = testApi.getStatus()

            ConnectionTestResult(
                isSuccess = true,
                serverVersion = status.version,
                isProtectionEnabled = status.protectionEnabled,
                pinnedCertSha256 = capturingTrustManager.capturedFingerprint
            )
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> ConnectionTestResult(
                    isSuccess = false,
                    errorType = NetworkErrorType.UNAUTHORIZED,
                    errorMessage = "Server found, but that username or password was rejected."
                )
                429 -> {
                    val retryAfter = e.response()?.headers()?.get("Retry-After")?.toIntOrNull() ?: 15
                    ConnectionTestResult(
                        isSuccess = false,
                        errorType = NetworkErrorType.RATE_LIMITED,
                        errorMessage = "Too many failed attempts. Locked out for another $retryAfter minutes."
                    )
                }
                else -> ConnectionTestResult(
                    isSuccess = false,
                    errorType = NetworkErrorType.SERVER_ERROR,
                    errorMessage = "Server returned error: HTTP ${e.code()}"
                )
            }
        } catch (e: SSLHandshakeException) {
            ConnectionTestResult(
                isSuccess = false,
                errorType = NetworkErrorType.TLS_ERROR,
                errorMessage = "Certificate rejected — enable 'trust self-signed certificate' below if this is your own certificate."
            )
        } catch (e: SSLException) {
            ConnectionTestResult(
                isSuccess = false,
                errorType = NetworkErrorType.TLS_ERROR,
                errorMessage = "TLS handshake failure. If using self-signed certs, enable the trust toggle."
            )
        } catch (e: SocketTimeoutException) {
            ConnectionTestResult(
                isSuccess = false,
                errorType = NetworkErrorType.UNREACHABLE,
                errorMessage = "Connection timed out. Check the IP and that you're on your home Wi-Fi."
            )
        } catch (e: ConnectException) {
            ConnectionTestResult(
                isSuccess = false,
                errorType = NetworkErrorType.UNREACHABLE,
                errorMessage = "Couldn't reach that address. Check the IP, port, and that you're on your home Wi-Fi."
            )
        } catch (e: UnknownHostException) {
            ConnectionTestResult(
                isSuccess = false,
                errorType = NetworkErrorType.UNREACHABLE,
                errorMessage = "Host could not be resolved. Verify the hostname or IP address."
            )
        } catch (e: Exception) {
            // Json parsing error or unexpected format
            if (e !is IOException) {
                ConnectionTestResult(
                    isSuccess = false,
                    errorType = NetworkErrorType.INVALID_RESPONSE,
                    errorMessage = "Something's answering on that port, but it isn't AdGuard Home."
                )
            } else {
                ConnectionTestResult(
                    isSuccess = false,
                    errorType = NetworkErrorType.UNKNOWN,
                    errorMessage = e.message ?: "An unexpected error occurred."
                )
            }
        }
    }

    suspend fun saveServerConfig(
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
        pinnedCertSha256: String? = null
    ) = withContext(ioDispatcher) {
        credentialStore.saveServerConfig(
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
            trustSelfSigned = trustSelfSigned,
            pinnedCertSha256 = pinnedCertSha256
        )
    }

    suspend fun updateSecurityPreferences(trustSelfSigned: Boolean) =
        withContext(ioDispatcher) {
            credentialStore.updateSecurityPreferences(trustSelfSigned)
        }

    suspend fun signOut() = withContext(ioDispatcher) {
        credentialStore.clearCredentials()
    }
}
