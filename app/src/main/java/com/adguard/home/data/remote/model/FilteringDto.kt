package com.adguard.home.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FilteringStatusDto(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("interval") val interval: Int = 24,
    @SerialName("filters") val filters: List<FilterDto> = emptyList(),
    @SerialName("whitelist_filters") val whitelistFilters: List<FilterDto> = emptyList(),
    @SerialName("user_rules") val userRules: List<String> = emptyList()
)

@Serializable
data class FilterDto(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("id") @Serializable(with = LenientLongSerializer::class) val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("rules_count") @Serializable(with = LenientLongSerializer::class) val rulesCount: Long = 0,
    @SerialName("url") val url: String = "",
    @SerialName("last_updated") val lastUpdated: String? = null
)

@Serializable
data class FilterSetUrlRequestDto(
    @SerialName("url") val url: String,
    @SerialName("whitelist") val whitelist: Boolean,
    @SerialName("data") val data: FilterSetUrlDataDto
)

@Serializable
data class FilterSetUrlDataDto(
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("name") val name: String,
    @SerialName("url") val url: String
)

@Serializable
data class FilterAddUrlRequestDto(
    @SerialName("name") val name: String,
    @SerialName("url") val url: String,
    @SerialName("whitelist") val whitelist: Boolean
)

@Serializable
data class FilterRemoveUrlRequestDto(
    @SerialName("url") val url: String,
    @SerialName("whitelist") val whitelist: Boolean
)

@Serializable
data class FilteringRefreshResponseDto(
    @SerialName("updated") val updated: Int = 0
)

@Serializable
data class FilteringConfigRequestDto(
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("interval") val interval: Int
)

@Serializable
data class SetRulesRequestDto(
    @SerialName("rules") val rules: List<String>
)
