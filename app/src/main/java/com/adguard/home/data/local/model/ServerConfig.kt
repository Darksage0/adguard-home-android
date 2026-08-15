package com.adguard.home.data.local.model

data class ServerConfig(
    val protocol: String = "http",
    val host: String = "",
    val port: Int = 3000,
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
    val requireBiometric: Boolean = false,
    val isConfigured: Boolean = false
) {
    val baseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            return if (cleanHost.isBlank()) "" else "$protocol://$cleanHost:$port/"
        }

    // Explicit manual toString to prevent any accidental password leakage into logs
    override fun toString(): String {
        return "ServerConfig(protocol='$protocol', host='$host', port=$port, username='$username', password=[REDACTED], trustSelfSigned=$trustSelfSigned, requireBiometric=$requireBiometric, isConfigured=$isConfigured)"
    }
}
