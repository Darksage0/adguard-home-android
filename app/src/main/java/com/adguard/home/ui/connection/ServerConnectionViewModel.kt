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

data class ConnectionFormState(
    val protocol: String = "http",
    val host: String = "",
    val port: String = "3000",
    val username: String = "",
    val password: String = "",
    val trustSelfSigned: Boolean = false,
    val requireBiometric: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isConfigured: Boolean = false,
    val savedBaseUrl: String = "",
    val savedVersion: String = "",
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Int = 0
) {
    val resolvedBaseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            val cleanPort = port.toIntOrNull() ?: 3000
            return if (cleanHost.isBlank()) "http://[HOST]:[PORT]/" else "$protocol://$cleanHost:$cleanPort/"
        }

    val isInputValid: Boolean
        get() = host.isNotBlank() && port.toIntOrNull() in 1..65535 && username.isNotBlank() && password.isNotBlank()

    val canSave: Boolean
        get() = isInputValid && (testResult?.isSuccess == true || isConfigured)
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
                        requireBiometric = config.requireBiometric,
                        isConfigured = config.isConfigured,
                        savedBaseUrl = config.baseUrl
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

    fun onRequireBiometricChanged(enabled: Boolean) {
        _uiState.update { it.copy(requireBiometric = enabled) }
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

        viewModelScope.launch {
            repository.saveServerConfig(
                protocol = state.protocol,
                host = state.host,
                port = portInt,
                username = state.username,
                password = state.password,
                trustSelfSigned = state.trustSelfSigned,
                requireBiometric = state.requireBiometric
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
