package com.adguard.home.ui.querylog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.NetworkResult
import com.adguard.home.domain.model.QueryLogEntry
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

data class QueryLogUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val entries: List<QueryLogEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedReason: String? = null,
    val oldestTimestamp: String? = null,
    val endOfListReached: Boolean = false,
    val selectedEntryForDetail: QueryLogEntry? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class QueryLogViewModel @Inject constructor(
    private val repository: AdGuardRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialReason: String? = savedStateHandle["reason"]

    private val _uiState = MutableStateFlow(
        QueryLogUiState(selectedReason = initialReason)
    )
    val uiState: StateFlow<QueryLogUiState> = _uiState.asStateFlow()

    private val _snackBarMessages = MutableSharedFlow<String>()
    val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadQueries(isInitial = true)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchSubmit() {
        loadQueries(isInitial = false)
    }

    fun onReasonSelected(reason: String?) {
        _uiState.update { it.copy(selectedReason = reason) }
        loadQueries(isInitial = false)
    }

    fun refresh() {
        loadQueries(isInitial = false)
    }

    private fun loadQueries(isInitial: Boolean) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = isInitial && current.entries.isEmpty(),
                    isRefreshing = !isInitial,
                    oldestTimestamp = null,
                    endOfListReached = false
                )
            }

            val state = _uiState.value
            val reasonsList = reasonsFor(state.selectedReason)

            when (val result = repository.getQueryLog(
                olderThan = null,
                search = state.searchQuery.ifBlank { null },
                reasons = reasonsList,
                limit = 50
            )) {
                is NetworkResult.Success -> {
                    val lastRawIso = result.data.lastOrNull()?.rawIsoTime
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            entries = result.data,
                            oldestTimestamp = lastRawIso,
                            endOfListReached = result.data.size < 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    val msg = result.message ?: "Failed to load query log"
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

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingNextPage || state.endOfListReached || state.oldestTimestamp == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNextPage = true) }

            val reasonsList = reasonsFor(state.selectedReason)

            when (val result = repository.getQueryLog(
                olderThan = state.oldestTimestamp,
                search = state.searchQuery.ifBlank { null },
                reasons = reasonsList,
                limit = 50
            )) {
                is NetworkResult.Success -> {
                    val lastRawIso = result.data.lastOrNull()?.rawIsoTime
                    _uiState.update { current ->
                        current.copy(
                            isLoadingNextPage = false,
                            entries = current.entries + result.data,
                            oldestTimestamp = lastRawIso,
                            endOfListReached = result.data.isEmpty() || result.data.size < 50
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoadingNextPage = false) }
                    _snackBarMessages.emit("Failed to load older queries")
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    /**
     * Expands a selected filter chip into the full set of AdGuard `reason` values it stands for.
     *
     * The `reason` query parameter accepts multiple values, and several of the UI's categories
     * genuinely cover more than one: an "allowed" answer is either NotFilteredNotFound or
     * NotFilteredWhiteList (explicitly allowlisted), and a rewrite is any of Rewrite,
     * RewriteEtcHosts or RewriteRule. Sending only the first value of each silently hid whole
     * classes of matching queries -- picking "Allowed" would, for instance, omit every query
     * allowed by one of the user's own @@ rules.
     */
    private fun reasonsFor(selectedReason: String?): List<String>? = when (selectedReason) {
        null -> null
        "NotFilteredNotFound" -> listOf("NotFilteredNotFound", "NotFilteredWhiteList")
        "Rewrite" -> listOf("Rewrite", "RewriteEtcHosts", "RewriteRule")
        else -> listOf(selectedReason)
    }

    fun selectEntryForDetail(entry: QueryLogEntry?) {
        _uiState.update { it.copy(selectedEntryForDetail = entry) }
    }

    fun blockDomain(domain: String) {
        viewModelScope.launch {
            val result = repository.appendDomainRule(domain, isBlock = true)
            when (result) {
                is NetworkResult.Success -> _snackBarMessages.emit("Blocked domain: $domain")
                is NetworkResult.Error -> _snackBarMessages.emit(result.message ?: "Failed to block domain")
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun unblockDomain(domain: String) {
        viewModelScope.launch {
            val result = repository.appendDomainRule(domain, isBlock = false)
            when (result) {
                is NetworkResult.Success -> _snackBarMessages.emit("Allowed domain: $domain")
                is NetworkResult.Error -> _snackBarMessages.emit(result.message ?: "Failed to allow domain")
                is NetworkResult.Loading -> Unit
            }
        }
    }
}
