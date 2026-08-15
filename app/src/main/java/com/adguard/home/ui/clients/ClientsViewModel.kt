package com.adguard.home.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.ClientItem
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

data class ClientsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val clients: List<ClientItem> = emptyList(),
    val selectedClient: ClientItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val repository: AdGuardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    private val _snackBarMessages = MutableSharedFlow<String>()
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadClients(isInitial = true)
    }

    fun refresh() {
        loadClients(isInitial = false)
    }

    private fun loadClients(isInitial: Boolean) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = isInitial && current.clients.isEmpty(),
                    isRefreshing = !isInitial
                )
            }

            when (val result = repository.getClients()) {
                is NetworkResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            clients = result.data,
                            errorMessage = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    val msg = result.message ?: "Failed to load clients"
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
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

    fun selectClient(client: ClientItem?) {
        _uiState.update { it.copy(selectedClient = client) }
    }
}
