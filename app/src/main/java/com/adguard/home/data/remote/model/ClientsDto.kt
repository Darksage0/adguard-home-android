package com.adguard.home.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientsResponseDto(
    @SerialName("clients") val clients: List<ClientDto> = emptyList(),
    @SerialName("auto_clients") val autoClients: List<ClientDto> = emptyList(),
    @SerialName("supported_tags") val supportedTags: List<String> = emptyList()
)

@Serializable
data class ClientDto(
    @SerialName("name") val name: String = "",
    @SerialName("ip") val ip: String = "",
    @SerialName("ids") val ids: List<String> = emptyList(),
    @SerialName("filtering_enabled") val filteringEnabled: Boolean = true,
    @SerialName("parental_enabled") val parentalEnabled: Boolean = false,
    @SerialName("safebrowsing_enabled") val safebrowsingEnabled: Boolean = false,
    @SerialName("safesearch_enabled") val safesearchEnabled: Boolean = false,
    @SerialName("use_global_blocked_services") val useGlobalBlockedServices: Boolean = true,
    @SerialName("blocked_services") val blockedServices: List<String> = emptyList(),
    @SerialName("tags") val tags: List<String> = emptyList()
)

@Serializable
data class SetProtectionRequestDto(
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("duration") val duration: Long? = null
)
