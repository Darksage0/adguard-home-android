package com.adguard.home.data.local

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adguard.home.data.local.model.ServerConfig
import com.adguard.home.data.local.security.TinkKeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tinkManager: TinkKeystoreManager
) {
    private object PreferencesKeys {
        val PROTOCOL = stringPreferencesKey("server_protocol")
        val HOST = stringPreferencesKey("server_host")
        val PORT = intPreferencesKey("server_port")
        val USERNAME = stringPreferencesKey("server_username")
        val ENCRYPTED_PASSWORD = stringPreferencesKey("server_encrypted_password")
        val TRUST_SELF_SIGNED = booleanPreferencesKey("trust_self_signed")
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val PINNED_CERT_SHA256 = stringPreferencesKey("pinned_cert_sha256")
    }

    val serverConfigFlow: Flow<ServerConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val protocol = preferences[PreferencesKeys.PROTOCOL] ?: "http"
            val host = preferences[PreferencesKeys.HOST] ?: ""
            val port = preferences[PreferencesKeys.PORT] ?: 3000
            val username = preferences[PreferencesKeys.USERNAME] ?: ""
            val encryptedPassword = preferences[PreferencesKeys.ENCRYPTED_PASSWORD] ?: ""
            val trustSelfSigned = preferences[PreferencesKeys.TRUST_SELF_SIGNED] ?: false
            val isConfigured = preferences[PreferencesKeys.IS_CONFIGURED] ?: false
            val pinnedCertSha256 = preferences[PreferencesKeys.PINNED_CERT_SHA256]

            var decryptionFailed = false
            val decryptedPassword = if (encryptedPassword.isNotBlank()) {
                try {
                    val cipherBytes = Base64.decode(encryptedPassword, Base64.NO_WRAP)
                    val plainBytes = tinkManager.decrypt(cipherBytes)
                    String(plainBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    decryptionFailed = true
                    ""
                }
            } else {
                ""
            }

            ServerConfig(
                protocol = protocol,
                host = host,
                port = port,
                username = username,
                password = decryptedPassword,
                trustSelfSigned = trustSelfSigned,
                isConfigured = isConfigured && host.isNotBlank(),
                pinnedCertSha256 = pinnedCertSha256,
                credentialDecryptionFailed = decryptionFailed
            )
        }

    suspend fun getServerConfig(): ServerConfig {
        return serverConfigFlow.first()
    }

    suspend fun saveServerConfig(
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
        pinnedCertSha256: String? = null
    ) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val cipherBytes = tinkManager.encrypt(password.toByteArray(Charsets.UTF_8))
        val encryptedPassword = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROTOCOL] = protocol.lowercase()
            preferences[PreferencesKeys.HOST] = cleanHost
            preferences[PreferencesKeys.PORT] = port
            preferences[PreferencesKeys.USERNAME] = username.trim()
            preferences[PreferencesKeys.ENCRYPTED_PASSWORD] = encryptedPassword
            preferences[PreferencesKeys.TRUST_SELF_SIGNED] = trustSelfSigned
            preferences[PreferencesKeys.IS_CONFIGURED] = true
            if (trustSelfSigned && pinnedCertSha256 != null) {
                preferences[PreferencesKeys.PINNED_CERT_SHA256] = pinnedCertSha256
            } else {
                preferences.remove(PreferencesKeys.PINNED_CERT_SHA256)
            }
        }
    }

    suspend fun updateSecurityPreferences(trustSelfSigned: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRUST_SELF_SIGNED] = trustSelfSigned
            // Disabling the toggle invalidates any pin -- re-enabling later must re-pin fresh
            // rather than silently trusting whatever certificate happened to be pinned before.
            if (!trustSelfSigned) {
                preferences.remove(PreferencesKeys.PINNED_CERT_SHA256)
            }
        }
    }

    /**
     * Called from [com.adguard.home.data.remote.ssl.SslConfig.DynamicTrustManager] the first time
     * a certificate is seen after "trust self-signed certificate" is enabled. Trust-on-first-use:
     * this fingerprint becomes the only certificate accepted for this server going forward.
     */
    suspend fun pinCertificate(sha256Fingerprint: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PINNED_CERT_SHA256] = sha256Fingerprint
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
