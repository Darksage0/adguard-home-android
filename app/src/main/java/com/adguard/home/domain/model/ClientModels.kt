package com.adguard.home.domain.model

data class ClientItem(
    val name: String,
    val primaryIpOrId: String,
    val allIds: List<String>,
    val isFilteringEnabled: Boolean,
    val isParentalEnabled: Boolean,
    val isSafeBrowsingEnabled: Boolean,
    val isAutoDiscovered: Boolean,
    val tags: List<String>,
    val blockedServices: List<String>
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val serverVersion: String? = null,
    val isProtectionEnabled: Boolean = true,
    val errorType: NetworkErrorType? = null,
    val errorMessage: String? = null
)
