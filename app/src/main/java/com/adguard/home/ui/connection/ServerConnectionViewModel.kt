package com.adguard.home.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.repository.ServerSettingsRepository
import com.adguard.home.domain.model.ConnectionTestResult
import com.adguard.home.domain.model.NetworkErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Only allow plaintext HTTP to hosts that are actually LAN-shaped. network_security_config.xml
// permits cleartext globally because Android's <domain> elements can't express private IP
// ranges (see the comment in that file) -- this is the app-level guard rail that compensates,
// so a mistyped or public hostname doesn't send Basic Auth credentials over the open internet.
private val PRIVATE_HOST_REGEX = Regex(
    "^(10\\..*|172\\.(1[6-9]|2\\d|3[01])\\..*|192\\.168\\..*|127\\..*|169\\.254\\..*|" +
        "localhost|.*\\.local|.*\\.lan|.*\\.home)$",
    RegexOption.IGNORE_CASE
)

data class ConnectionFormState(
    val protocol: String = "http",
    val host: String = "",
    val port: String = "3000",
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isConfigured: Boolean = false,
    val savedBaseUrl: String = "",
    val savedVersion: String = "",
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val existingPinnedCertSha256: String? = null,
    val credentialDecryptionFailed: Boolean = false
) {
    val resolvedBaseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            val cleanPort = port.toIntOrNull() ?: 3000
            return if (cleanHost.isBlank()) "http://[HOST]:[PORT]/" else "$protocol://$cleanHost:$cleanPort/"
        }

    val isPlaintextToPublicHost: Boolean
        get() = protocol == "http" && host.isNotBlank() && !PRIVATE_HOST_REGEX.matches(host.trim())

    val isInputValid: Boolean
        get() = host.isNotBlank() && port.toIntOrNull() in 1..65535 &&
            username.isNotBlank() && password.isNotBlank() && !isPlaintextToPublicHost

    val canSave: Boolean
        get() = isInputValid && (testResult?.isSuccess == true || isConfigured)

    // Explicit manual toString to prevent any accidental password leakage into logs (mirrors
    // ServerConfig.toString(), which this state is a near-duplicate of).
    override fun toString(): String {
        return "ConnectionFormState(protocol='$protocol', host='$host', port='$port', " +
            "username='$username', password=[REDACTED], trustSelfSigned=$trustSelfSigned, " +
            "isTesting=$isTesting, testResult=$testResult, " +
            "isConfigured=$isConfigured, savedBaseUrl='$savedBaseUrl', savedVersion='$savedVersion', " +
            "isLockedOut=$isLockedOut, lockoutRemainingSeconds=$lockoutRemainingSeconds, " +
            "existingPinnedCertSha256=$existingPinnedCertSha256, " +
            "credentialDecryptionFailed=$credentialDecryptionFailed)"
    }
}

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    private val repository: ServerSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionFormState())
    val uiState: StateFlow<ConnectionFormState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            repository.serverConfigFlow.collect { config ->
                _uiState.update { current ->
                    current.copy(
                        protocol = if (config.isConfigured) config.protocol else current.protocol,
                        host = if (config.isConfigured) config.host else current.host,
                        port = if (config.isConfigured) config.port.toString() else current.port,
                        username = if (config.isConfigured) config.username else current.username,
                        password = if (config.isConfigured) config.password else current.password,
                        trustSelfSigned = config.trustSelfSigned,
                        isConfigured = config.isConfigured,
                        savedBaseUrl = config.baseUrl,
                        existingPinnedCertSha256 = config.pinnedCertSha256,
                        credentialDecryptionFailed = config.credentialDecryptionFailed
                    )
                }
            }
        }
    }

    fun onProtocolChanged(newProtocol: String) {
        _uiState.update { it.copy(protocol = newProtocol, testResult = null) }
    }

    fun onHostChanged(input: String) {
        var cleanInput = input.trim()
        var detectedProtocol = _uiState.value.protocol

        // Automatically detect and strip pasted scheme
        if (cleanInput.startsWith("https://", ignoreCase = true)) {
            detectedProtocol = "https"
            cleanInput = cleanInput.substring(8)
        } else if (cleanInput.startsWith("http://", ignoreCase = true)) {
            detectedProtocol = "http"
            cleanInput = cleanInput.substring(7)
        }

        // If port is attached in host (e.g. 192.168.1.50:3000/control), parse and split it
        var detectedPort = _uiState.value.port
        if (cleanInput.contains(":")) {
            val parts = cleanInput.split(":")
            cleanInput = parts[0]
            val portPart = parts.getOrNull(1)?.substringBefore('/')?.substringBefore('?')
            if (portPart?.toIntOrNull() != null) {
                detectedPort = portPart
            }
        } else if (cleanInput.contains("/")) {
            cleanInput = cleanInput.substringBefore('/')
        }

        _uiState.update {
            it.copy(
                host = cleanInput,
                protocol = detectedProtocol,
                port = detectedPort,
                testResult = null
            )
        }
    }

    fun onPortChanged(input: String) {
        _uiState.update { it.copy(port = input.filter { char -> char.isDigit() }, testResult = null) }
    }

    fun onUsernameChanged(input: String) {
        _uiState.update { it.copy(username = input.trim(), testResult = null) }
    }

    fun onPasswordChanged(input: String) {
        _uiState.update { it.copy(password = input, testResult = null) }
    }

    fun onTrustSelfSignedChanged(enabled: Boolean) {
        _uiState.update { it.copy(trustSelfSigned = enabled, testResult = null) }
    }

    fun testConnection() {
        if (testJob?.isActive == true || _uiState.value.isLockedOut) return

        val state = _uiState.value
        val portInt = state.port.toIntOrNull() ?: 3000

        _uiState.update { it.copy(isTesting = true, testResult = null) }

        testJob = viewModelScope.launch {
            val result = repository.testConnection(
                protocol = state.protocol,
                host = state.host,
                port = portInt,
                username = state.username,
                password = state.password,
                trustSelfSigned = state.trustSelfSigned
            )

            val isRateLimited = result.errorType == NetworkErrorType.RATE_LIMITED

            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResult = result,
                    isLockedOut = isRateLimited,
                    lockoutRemainingSeconds = if (isRateLimited) 900 else 0
                )
            }
        }
    }

    fun saveConnection(onSuccess: () -> Unit) {
        val state = _uiState.value
        val portInt = state.port.toIntOrNull() ?: 3000

        // Prefer a fingerprint captured by a fresh "Test Connection" just now; otherwise keep
        // whatever was already pinned, but only if trust-self-signed is still on -- if the user
        // is saving without retesting, the pin they already have is still the right one to keep,
        // but if they toggle the setting off, saveServerConfig's own logic must drop the pin.
        val pinnedCertSha256 = state.testResult?.pinnedCertSha256
            ?: state.existingPinnedCertSha256.takeIf { state.trustSelfSigned }

        viewModelScope.launch {
            repository.saveServerConfig(
                protocol = state.protocol,
                host = state.host,
                port = portInt,
                username = state.username,
                password = state.password,
                trustSelfSigned = state.trustSelfSigned,
                pinnedCertSha256 = pinnedCertSha256
            )
            onSuccess()
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.signOut()
            _uiState.update { ConnectionFormState() }
            onComplete()
        }
    }
}
