package com.adguard.home.di

import com.adguard.home.BuildConfig
import com.adguard.home.data.local.CredentialStore
import com.adguard.home.data.remote.AdGuardApi
import com.adguard.home.data.remote.interceptor.AuthInterceptor
import com.adguard.home.data.remote.interceptor.DynamicHostInterceptor
import com.adguard.home.data.remote.ssl.SslConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DynamicOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppJson

/**
 * Interceptor ensuring bodyless POST requests do not send a Content-Type header.
 *
 * AdGuard Home v0.107.15+ strictly rejects bodyless POST requests that include
 * a Content-Type header with a 400 Bad Request. OkHttp attaches Content-Type to empty
 * bodies by default, so we strip it here if content length is 0 or body is absent.
 */
class EmptyBodyContentTypeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val body = originalRequest.body

        return if (body == null || body.contentLength() == 0L) {
            val strippedRequest = originalRequest.newBuilder()
                .removeHeader("Content-Type")
                .build()
            chain.proceed(strippedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}

/**
 * POST /filtering/refresh (§4.4 "Check for updates") can legitimately take 10+ seconds on
 * large blocklists per the spec's own warning. The rest of the app deliberately uses a short
 * 5s connect / 10s read timeout (§7.4) so an unreachable Pi fails fast, but that same short
 * read timeout was previously applied to this call too, which meant a refresh that was still
 * genuinely running server-side surfaced as a client-side timeout failure well before it had
 * a chance to finish. Give just this one call a generous timeout, per the spec's explicit
 * instruction to override the short LAN default for it specifically.
 */
class RefreshEndpointTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return if (request.url.encodedPath.endsWith("/filtering/refresh")) {
            chain.withReadTimeout(45, TimeUnit.SECONDS)
                .withWriteTimeout(45, TimeUnit.SECONDS)
                .proceed(request)
        } else {
            chain.proceed(request)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @AppJson
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor? {
        return if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("Cookie")
            }
        } else {
            null
        }
    }

    @Provides
    @Singleton
    fun provideEmptyBodyContentTypeInterceptor(): EmptyBodyContentTypeInterceptor {
        return EmptyBodyContentTypeInterceptor()
    }

    @Provides
    @Singleton
    fun provideRefreshEndpointTimeoutInterceptor(): RefreshEndpointTimeoutInterceptor {
        return RefreshEndpointTimeoutInterceptor()
    }

    @Provides
    @Singleton
    @DynamicOkHttpClient
    fun provideBaseOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor?,
        emptyBodyInterceptor: EmptyBodyContentTypeInterceptor,
        refreshEndpointTimeoutInterceptor: RefreshEndpointTimeoutInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(emptyBodyInterceptor)
            .addInterceptor(refreshEndpointTimeoutInterceptor)

        loggingInterceptor?.let {
            builder.addInterceptor(it)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @DynamicOkHttpClient baseOkHttpClient: OkHttpClient,
        dynamicHostInterceptor: DynamicHostInterceptor,
        authInterceptor: AuthInterceptor,
        credentialStore: CredentialStore
    ): OkHttpClient {
        // Always install a trust manager/hostname verifier that reads the live
        // "trust self-signed certificate" setting on every handshake (see SslConfig.
        // DynamicTrustManager) rather than deciding once with a blocking DataStore read at
        // singleton-construction time. This keeps DataStore I/O off the main thread and lets
        // the setting take effect on the very next request after being toggled, with no app
        // restart -- for HTTP-only setups (the spec's default) this trust manager simply
        // delegates to the system default and changes nothing.
        val dynamicTrustManager = SslConfig.DynamicTrustManager(credentialStore)

        return baseOkHttpClient.newBuilder()
            .addInterceptor(dynamicHostInterceptor)
            .addInterceptor(authInterceptor)
            .sslSocketFactory(SslConfig.dynamicSslSocketFactory(dynamicTrustManager), dynamicTrustManager)
            .hostnameVerifier(SslConfig.dynamicHostnameVerifier(credentialStore))
            .build()
    }

    @Provides
    @Singleton
    fun provideAdGuardApi(
        okHttpClient: OkHttpClient,
        @AppJson json: Json
    ): AdGuardApi {
        val contentType = "application/json".toMediaType()
        // The base URL will be dynamically rewritten by DynamicHostInterceptor
        return Retrofit.Builder()
            .baseUrl("http://127.0.0.1:3000/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AdGuardApi::class.java)
    }
}
