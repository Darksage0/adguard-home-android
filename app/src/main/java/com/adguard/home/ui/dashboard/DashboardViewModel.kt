package com.adguard.home.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.DashboardData
import com.adguard.home.domain.model.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isProtectionToggling: Boolean = false,
    val data: DashboardData? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AdGuardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _snackBarMessages = MutableSharedFlow<String>()
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    private var refreshJob: Job? = null

    init {
        loadDashboardData(isInitial = true)
    }

    /**
     * Called on ON_RESUME and manual user refresh.
     * Idempotent & non-overlapping.
     */
    fun refresh() {
        loadDashboardData(isInitial = false)
    }

    private fun loadDashboardData(isInitial: Boolean) {
        // If a refresh is already in progress, ignore duplicate taps
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isInitialLoading = isInitial && current.data == null,
                    isRefreshing = !isInitial,
                    errorMessage = null
                )
            }

            when (val result = repository.getDashboardData()) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            data = result.data,
                            errorMessage = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    val msg = result.message ?: "Failed to refresh statistics."
                    _uiState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = msg
                        )
                    }
                    _snackBarMessages.emit(msg)
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun setProtection(enabled: Boolean, durationMs: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProtectionToggling = true) }
            val result = repository.setProtection(enabled, durationMs)
            if (result is NetworkResult.Error) {
                _snackBarMessages.emit(result.message ?: "Failed to change protection state.")
            }
            _uiState.update { it.copy(isProtectionToggling = false) }
            // Re-sync dashboard state
            loadDashboardData(isInitial = false)
        }
    }

    fun blockDomain(domain: String) {
        viewModelScope.launch {
            val result = repository.appendDomainRule(domain, isBlock = true)
            when (result) {
                is NetworkResult.Success -> _snackBarMessages.emit("Added block rule for $domain")
                is NetworkResult.Error -> _snackBarMessages.emit(result.message ?: "Failed to block domain.")
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun unblockDomain(domain: String) {
        viewModelScope.launch {
            val result = repository.appendDomainRule(domain, isBlock = false)
            when (result) {
                is NetworkResult.Success -> _snackBarMessages.emit("Added allow rule for $domain")
                is NetworkResult.Error -> _snackBarMessages.emit(result.message ?: "Failed to unblock domain.")
                is NetworkResult.Loading -> Unit
            }
        }
    }
}
