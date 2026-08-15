package com.adguard.home.data.remote.interceptor

import com.adguard.home.data.local.CredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that dynamically alters the request scheme, host, and port based on
 * the currently stored server configuration. This ensures changes to the server IP
 * or protocol take effect immediately without rebuilding Retrofit.
 */
@Singleton
class DynamicHostInterceptor @Inject constructor(
    private val credentialStore: CredentialStore
) : Interceptor {

    // Overridable host for testing unpersisted credentials (e.g. Test Connection screen)
    @Volatile
    var temporaryBaseUrl: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val targetBaseUrl = temporaryBaseUrl ?: runBlocking {
            credentialStore.getServerConfig().baseUrl
        }

        if (targetBaseUrl.isNotBlank()) {
            val parsedUrl = targetBaseUrl.toHttpUrlOrNull()
            if (parsedUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(parsedUrl.scheme)
                    .host(parsedUrl.host)
                    .port(parsedUrl.port)
                    .build()

                request = request.newBuilder()
                    .url(newUrl)
                    .build()
            }
        }

        return chain.proceed(request)
    }
}
