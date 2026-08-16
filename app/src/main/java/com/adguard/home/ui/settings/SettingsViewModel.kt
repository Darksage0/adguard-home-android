package com.adguard.home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adguard.home.data.local.model.ServerConfig
import com.adguard.home.data.repository.ServerSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ServerSettingsRepository
) : ViewModel() {

    val serverConfig: StateFlow<ServerConfig> = repository.serverConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerConfig()
        )

    fun updateSecurityPreferences(trustSelfSigned: Boolean) {
        viewModelScope.launch {
            repository.updateSecurityPreferences(trustSelfSigned)
        }
    }
}
