package com.adguard.home.data.local.model

data class ServerConfig(
    val protocol: String = "http",
    val host: String = "",
    val port: Int = 3000,
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
    val isConfigured: Boolean = false,
    val pinnedCertSha256: String? = null,
    // Transient: true when a saved password exists but couldn't be decrypted (e.g. the Android
    // Keystore key was invalidated by a biometric re-enrollment). Not persisted itself -- derived
    // fresh from the encrypted-password/decrypt-failure state on every CredentialStore read.
    val credentialDecryptionFailed: Boolean = false
) {
    val baseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            return if (cleanHost.isBlank()) "" else "$protocol://$cleanHost:$port/"
        }

    // Explicit manual toString to prevent any accidental password leakage into logs
    override fun toString(): String {
        return "ServerConfig(protocol='$protocol', host='$host', port=$port, username='$username', password=[REDACTED], trustSelfSigned=$trustSelfSigned, isConfigured=$isConfigured, pinnedCertSha256=$pinnedCertSha256, credentialDecryptionFailed=$credentialDecryptionFailed)"
    }
}
