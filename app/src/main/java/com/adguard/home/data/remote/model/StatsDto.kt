package com.adguard.home.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    @SerialName("num_dns_queries") @Serializable(with = LenientLongSerializer::class) val numDnsQueries: Long = 0,
    @SerialName("num_blocked_filtering") @Serializable(with = LenientLongSerializer::class) val numBlockedFiltering: Long = 0,
    @SerialName("num_replaced_safebrowsing") @Serializable(with = LenientLongSerializer::class) val numReplacedSafebrowsing: Long = 0,
    @SerialName("num_replaced_safesearch") @Serializable(with = LenientLongSerializer::class) val numReplacedSafesearch: Long = 0,
    @SerialName("num_replaced_parental") @Serializable(with = LenientLongSerializer::class) val numReplacedParental: Long = 0,
    @SerialName("avg_processing_time") val avgProcessingTime: Double = 0.0,
    @SerialName("time_units") val timeUnits: String = "hours",

    @SerialName("dns_queries") @Serializable(with = LenientLongListSerializer::class) val dnsQueries: List<Long> = emptyList(),
    @SerialName("blocked_filtering") @Serializable(with = LenientLongListSerializer::class) val blockedFiltering: List<Long> = emptyList(),
    @SerialName("replaced_safebrowsing") @Serializable(with = LenientLongListSerializer::class) val replacedSafebrowsing: List<Long> = emptyList(),
    @SerialName("replaced_parental") @Serializable(with = LenientLongListSerializer::class) val replacedParental: List<Long> = emptyList(),

    @SerialName("top_queried_domains") val topQueriedDomains: List<TopEntryDto> = emptyList(),
    @SerialName("top_clients") val topClients: List<TopEntryDto> = emptyList(),
    @SerialName("top_blocked_domains") val topBlockedDomains: List<TopEntryDto> = emptyList(),
    @SerialName("top_upstreams_responses") val topUpstreamsResponses: List<TopEntryDto> = emptyList(),
    @SerialName("top_upstreams_avg_time") val topUpstreamsAvgTime: List<TopEntryDto> = emptyList()
)

@Serializable
data class StatsConfigDto(
    @SerialName("enabled") val enabled: Boolean = true,
    // Double, not Long: another millisecond duration, and therefore exposed to the same
    // fractional-literal decode failure that broke /control/status (see ServerStatusDto). A throw
    // here is quieter but still wrong -- the caller swallows it and silently falls back to a
    // hardcoded 7 days, so every card would claim "for the last 7 days" regardless of what the
    // server is actually configured to keep.
    @SerialName("interval") val interval: Double = 604800000.0, // default 7 days in ms
    @SerialName("ignored") val ignored: List<String> = emptyList(),
    @SerialName("ignored_enabled") val ignoredEnabled: Boolean = false
)
