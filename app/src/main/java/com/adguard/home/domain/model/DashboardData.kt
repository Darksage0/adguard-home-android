package com.adguard.home.domain.model

data class DashboardData(
    val protectionState: ProtectionState,
    val timePeriodText: String,
    val headlineCards: HeadlineCards,
    val generalStatistics: GeneralStatistics,
    val topClients: List<TopClientItem>,
    val topQueriedDomains: List<TopDomainItem>,
    val topBlockedDomains: List<TopDomainItem>,
    val topUpstreams: List<TopUpstreamItem>,
    val upstreamResponseTimes: List<UpstreamResponseTimeItem>,
    val lastUpdatedFormatted: String
)

data class ProtectionState(
    val isEnabled: Boolean,
    val isPaused: Boolean,
    val pauseRemainingMs: Long? = null,
    val serverVersion: String = ""
)

data class HeadlineCards(
    val dnsQueries: StatCardMetric,
    val blockedFiltering: StatCardMetric,
    val blockedMalware: StatCardMetric,
    val blockedAdult: StatCardMetric
)

data class StatCardMetric(
    val label: String,
    val value: Long,
    val formattedValue: String,
    val percentage: Float? = null, // e.g. 15.4%
    val sparklinePoints: List<Float> = emptyList(),
    val timeUnit: String = "hours"
)

data class GeneralStatistics(
    val dnsQueries: Long,
    val blockedFiltering: Long,
    val blockedMalware: Long,
    val blockedAdult: Long,
    val enforcedSafeSearch: Long,
    val avgProcessingTimeMs: Double // Converted from seconds to ms (* 1000)
)

data class TopClientItem(
    val ipOrName: String,
    val count: Long,
    val percentage: Float
)

data class TopDomainItem(
    val domain: String,
    val count: Long,
    val percentage: Float,
    val isExplicitlyAllowed: Boolean = false
)

data class TopUpstreamItem(
    val address: String,
    val count: Long,
    val percentage: Float
)

data class UpstreamResponseTimeItem(
    val address: String,
    val responseTimeMs: Double // Converted from seconds to ms (* 1000)
)
