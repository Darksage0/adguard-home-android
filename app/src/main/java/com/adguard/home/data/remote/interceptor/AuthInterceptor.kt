package com.adguard.home.data.remote.interceptor

import com.adguard.home.data.local.CredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that reads credentials on-demand and attaches HTTP Basic Authentication.
 * Plaintext passwords are not cached in long-lived variables.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore
) : Interceptor {

    // Overridable credentials for testing connection form inputs before saving
    @Volatile
    var temporaryAuthHeader: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If an Authorization header is already provided on the request, don't overwrite it
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val authHeader = temporaryAuthHeader ?: runBlocking {
            val config = credentialStore.getServerConfig()
            if (config.username.isNotBlank() && config.password.isNotBlank()) {
                Credentials.basic(config.username, config.password)
            } else {
                null
            }
        }

        val request = if (authHeader != null) {
            originalRequest.newBuilder()
                .header("Authorization", authHeader)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
