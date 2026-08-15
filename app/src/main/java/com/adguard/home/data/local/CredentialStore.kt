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
        val REQUIRE_BIOMETRIC = booleanPreferencesKey("require_biometric")
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
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
            val requireBiometric = preferences[PreferencesKeys.REQUIRE_BIOMETRIC] ?: false
            val isConfigured = preferences[PreferencesKeys.IS_CONFIGURED] ?: false

            val decryptedPassword = if (encryptedPassword.isNotBlank()) {
                try {
                    val cipherBytes = Base64.decode(encryptedPassword, Base64.NO_WRAP)
                    val plainBytes = tinkManager.decrypt(cipherBytes)
                    String(plainBytes, Charsets.UTF_8)
                } catch (e: Exception) {
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
                requireBiometric = requireBiometric,
                isConfigured = isConfigured && host.isNotBlank()
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
        requireBiometric: Boolean
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
            preferences[PreferencesKeys.REQUIRE_BIOMETRIC] = requireBiometric
            preferences[PreferencesKeys.IS_CONFIGURED] = true
        }
    }

    suspend fun updateSecurityPreferences(trustSelfSigned: Boolean, requireBiometric: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRUST_SELF_SIGNED] = trustSelfSigned
            preferences[PreferencesKeys.REQUIRE_BIOMETRIC] = requireBiometric
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
