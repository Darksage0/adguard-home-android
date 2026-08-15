package com.adguard.home.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.FilterItem
import com.adguard.home.domain.model.FilterListsData
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

data class FiltersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val data: FilterListsData? = null,
    val customRulesText: String = "",
    val isCustomRulesDirty: Boolean = false,
    val isSavingRules: Boolean = false,
    val errorMessage: String? = null
)

sealed interface FilterUiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : FilterUiEvent
}

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val repository: AdGuardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FiltersUiState())
    val uiState: StateFlow<FiltersUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<FilterUiEvent>()
    val uiEvents: SharedFlow<FilterUiEvent> = _uiEvents.asSharedFlow()

    private var initialLoadedRules = ""
    private var refreshJob: Job? = null

    init {
        loadFilters(isInitial = true)
    }

    fun refresh() {
        loadFilters(isInitial = false)
    }

    private fun loadFilters(isInitial: Boolean) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = isInitial && current.data == null,
                    isRefreshing = !isInitial
                )
            }

            when (val result = repository.getFilteringData()) {
                is NetworkResult.Success -> {
                    val rulesJoined = result.data.userRules.joinToString("\n")
                    initialLoadedRules = rulesJoined
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            data = result.data,
                            customRulesText = if (!current.isCustomRulesDirty) rulesJoined else current.customRulesText,
                            errorMessage = null
                        )
                    }
                }
                is NetworkResult.Error -> {
                    val msg = result.message ?: "Failed to load filters"
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = msg
                        )
                    }
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(msg))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun toggleFilter(filter: FilterItem, whitelist: Boolean, newEnabled: Boolean) {
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { current ->
                val currentData = current.data ?: return@update current
                val updatedBlocklists = if (!whitelist) {
                    currentData.blocklists.map { if (it.url == filter.url) it.copy(isEnabled = newEnabled) else it }
                } else currentData.blocklists

                val updatedAllowlists = if (whitelist) {
                    currentData.allowlists.map { if (it.url == filter.url) it.copy(isEnabled = newEnabled) else it }
                } else currentData.allowlists

                current.copy(
                    data = currentData.copy(
                        blocklists = updatedBlocklists,
                        allowlists = updatedAllowlists
                    )
                )
            }

            val result = repository.toggleFilter(
                url = filter.url,
                whitelist = whitelist,
                enabled = newEnabled,
                currentName = filter.name
            )

            if (result is NetworkResult.Error) {
                // Revert on error
                _uiEvents.emit(FilterUiEvent.ShowSnackbar("Failed to toggle ${filter.name}. Reverting."))
                loadFilters(isInitial = false)
            }
        }
    }

    fun addFilter(name: String, url: String, whitelist: Boolean, onSuccess: () -> Unit) {
        val cleanUrl = url.trim()
        val cleanName = name.trim()

        val existingUrls = (_uiState.value.data?.blocklists?.map { it.url } ?: emptyList()) +
                (_uiState.value.data?.allowlists?.map { it.url } ?: emptyList())

        if (existingUrls.contains(cleanUrl)) {
            viewModelScope.launch {
                _uiEvents.emit(FilterUiEvent.ShowSnackbar("This list URL is already added."))
            }
            return
        }

        viewModelScope.launch {
            val result = repository.addFilter(name = cleanName, url = cleanUrl, whitelist = whitelist)
            when (result) {
                is NetworkResult.Success -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar("Filter list added successfully"))
                    onSuccess()
                    loadFilters(isInitial = false)
                }
                is NetworkResult.Error -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to add filter list"))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun editFilter(originalUrl: String, newName: String, newUrl: String, whitelist: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.editFilter(
                originalUrl = originalUrl,
                whitelist = whitelist,
                newName = newName,
                newUrl = newUrl
            )
            when (result) {
                is NetworkResult.Success -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar("Filter list updated"))
                    onSuccess()
                    loadFilters(isInitial = false)
                }
                is NetworkResult.Error -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to update filter list"))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun deleteFilter(filter: FilterItem, whitelist: Boolean) {
        viewModelScope.launch {
            val result = repository.removeFilter(url = filter.url, whitelist = whitelist)
            when (result) {
                is NetworkResult.Success -> {
                    _uiEvents.emit(
                        FilterUiEvent.ShowSnackbar(
                            message = "Deleted \"${filter.name}\"",
                            actionLabel = "Undo",
                            onAction = {
                                addFilter(filter.name, filter.url, whitelist) {}
                            }
                        )
                    )
                    loadFilters(isInitial = false)
                }
                is NetworkResult.Error -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to delete filter list"))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true) }
            val result = repository.refreshFilters(whitelist = false)
            _uiState.update { it.copy(isCheckingUpdates = false) }

            when (result) {
                is NetworkResult.Success -> {
                    val count = result.data
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(if (count > 0) "Updated $count filter list(s)" else "All lists are up to date"))
                    loadFilters(isInitial = false)
                }
                is NetworkResult.Error -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to check for updates"))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun setUpdateInterval(intervalHours: Int) {
        viewModelScope.launch {
            val result = repository.setFilteringUpdateInterval(intervalHours)
            if (result is NetworkResult.Success) {
                _uiEvents.emit(FilterUiEvent.ShowSnackbar("Update interval set"))
                loadFilters(isInitial = false)
            } else if (result is NetworkResult.Error) {
                _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to set update interval"))
            }
        }
    }

    fun onCustomRulesTextChanged(newText: String) {
        _uiState.update {
            it.copy(
                customRulesText = newText,
                isCustomRulesDirty = newText != initialLoadedRules
            )
        }
    }

    fun saveCustomRules() {
        val lines = _uiState.value.customRulesText.lines().filter { it.isNotBlank() }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingRules = true) }
            val result = repository.saveUserRules(lines)
            _uiState.update { it.copy(isSavingRules = false) }

            when (result) {
                is NetworkResult.Success -> {
                    initialLoadedRules = _uiState.value.customRulesText
                    _uiState.update { it.copy(isCustomRulesDirty = false) }
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar("Custom rules saved successfully"))
                }
                is NetworkResult.Error -> {
                    _uiEvents.emit(FilterUiEvent.ShowSnackbar(result.message ?: "Failed to save custom rules"))
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }
}
