package com.adguard.home.data.repository

import com.adguard.home.data.remote.AdGuardApi
import com.adguard.home.data.remote.model.FilterAddUrlRequestDto
import com.adguard.home.data.remote.model.FilterDto
import com.adguard.home.data.remote.model.FilterRemoveUrlRequestDto
import com.adguard.home.data.remote.model.FilterSetUrlDataDto
import com.adguard.home.data.remote.model.FilterSetUrlRequestDto
import com.adguard.home.data.remote.model.FilteringConfigRequestDto
import com.adguard.home.data.remote.model.SetProtectionRequestDto
import com.adguard.home.data.remote.model.SetRulesRequestDto
import com.adguard.home.di.IoDispatcher
import com.adguard.home.domain.model.AppliedRule
import com.adguard.home.domain.model.ClientItem
import com.adguard.home.domain.model.DashboardData
import com.adguard.home.domain.model.FilterItem
import com.adguard.home.domain.model.FilterListsData
import com.adguard.home.domain.model.GeneralStatistics
import com.adguard.home.domain.model.HeadlineCards
import com.adguard.home.domain.model.NetworkErrorType
import com.adguard.home.domain.model.NetworkResult
import com.adguard.home.domain.model.ProtectionState
import com.adguard.home.domain.model.QueryLogEntry
import com.adguard.home.domain.model.StatCardMetric
import com.adguard.home.domain.model.TopClientItem
import com.adguard.home.domain.model.TopDomainItem
import com.adguard.home.domain.model.TopUpstreamItem
import com.adguard.home.domain.model.UpstreamResponseTimeItem
import com.adguard.home.domain.model.decodeBuiltInFilterName
import com.adguard.home.domain.model.mapReasonToDisposition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import java.net.UnknownHostException
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

@Singleton
class AdGuardRepository @Inject constructor(
    private val api: AdGuardApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val dashboardMutex = Mutex()
    private val queryLogMutex = Mutex()
    private val filteringMutex = Mutex()

    /**
     * Fetches status, stats, and stats config concurrently and merges them into DashboardData.
     * Guaranteed single in-flight execution via Mutex.
     */
    suspend fun getDashboardData(): NetworkResult<DashboardData> = withContext(ioDispatcher) {
        dashboardMutex.withLock {
            safeApiCall {
                coroutineScope {
                    val statusDeferred = async { api.getStatus() }
                    val statsDeferred = async { api.getStats() }
                    val configDeferred = async {
                        try {
                            api.getStatsConfig()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val status = statusDeferred.await()
                    val stats = statsDeferred.await()
                    val config = configDeferred.await()

                    val intervalMs = config?.interval?.toLong() ?: 604800000L
                    val periodText = formatIntervalText(intervalMs)

                    val totalQueries = stats.numDnsQueries.coerceAtLeast(1)
                    val blockedFiltering = stats.numBlockedFiltering
                    val blockedMalware = stats.numReplacedSafebrowsing
                    val blockedAdult = stats.numReplacedParental

                    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

                    val headlineCards = HeadlineCards(
                        dnsQueries = StatCardMetric(
                            label = "DNS Queries",
                            value = stats.numDnsQueries,
                            formattedValue = numberFormat.format(stats.numDnsQueries),
                            percentage = null,
                            sparklinePoints = stats.dnsQueries.map { it.toFloat() },
                            timeUnit = stats.timeUnits
                        ),
                        blockedFiltering = StatCardMetric(
                            label = "Blocked by Filters",
                            value = blockedFiltering,
                            formattedValue = numberFormat.format(blockedFiltering),
                            percentage = (blockedFiltering.toFloat() / totalQueries) * 100f,
                            sparklinePoints = stats.blockedFiltering.map { it.toFloat() },
                            timeUnit = stats.timeUnits
                        ),
                        blockedMalware = StatCardMetric(
                            label = "Blocked malware/phishing",
                            value = blockedMalware,
                            formattedValue = numberFormat.format(blockedMalware),
                            percentage = (blockedMalware.toFloat() / totalQueries) * 100f,
                            sparklinePoints = stats.replacedSafebrowsing.map { it.toFloat() },
                            timeUnit = stats.timeUnits
                        ),
                        blockedAdult = StatCardMetric(
                            label = "Blocked adult websites",
                            value = blockedAdult,
                            formattedValue = numberFormat.format(blockedAdult),
                            percentage = (blockedAdult.toFloat() / totalQueries) * 100f,
                            sparklinePoints = stats.replacedParental.map { it.toFloat() },
                            timeUnit = stats.timeUnits
                        )
                    )

                    val generalStatistics = GeneralStatistics(
                        dnsQueries = stats.numDnsQueries,
                        blockedFiltering = stats.numBlockedFiltering,
                        blockedMalware = stats.numReplacedSafebrowsing,
                        blockedAdult = stats.numReplacedParental,
                        enforcedSafeSearch = stats.numReplacedSafesearch,
                        // Convert seconds to milliseconds
                        avgProcessingTimeMs = stats.avgProcessingTime * 1000.0
                    )

                    val topClients = stats.topClients.map { entry ->
                        val count = entry.value.toLong()
                        TopClientItem(
                            ipOrName = entry.key,
                            count = count,
                            percentage = (count.toFloat() / totalQueries) * 100f
                        )
                    }

                    val topQueriedDomains = stats.topQueriedDomains.map { entry ->
                        val count = entry.value.toLong()
                        TopDomainItem(
                            domain = entry.key,
                            count = count,
                            percentage = (count.toFloat() / totalQueries) * 100f
                        )
                    }

                    val totalBlocked = blockedFiltering.coerceAtLeast(1)
                    val topBlockedDomains = stats.topBlockedDomains.map { entry ->
                        val count = entry.value.toLong()
                        TopDomainItem(
                            domain = entry.key,
                            count = count,
                            percentage = (count.toFloat() / totalBlocked) * 100f
                        )
                    }

                    val topUpstreamTotal = stats.topUpstreamsResponses.sumOf { it.value.toLong() }.coerceAtLeast(1)
                    val topUpstreams = stats.topUpstreamsResponses.map { entry ->
                        val count = entry.value.toLong()
                        TopUpstreamItem(
                            address = entry.key,
                            count = count,
                            percentage = (count.toFloat() / topUpstreamTotal) * 100f
                        )
                    }

                    val upstreamResponseTimes = stats.topUpstreamsAvgTime.map { entry ->
                        UpstreamResponseTimeItem(
                            address = entry.key,
                            // Convert seconds to milliseconds
                            responseTimeMs = entry.value * 1000.0
                        )
                    }.sortedBy { it.responseTimeMs }

                    val now = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(java.time.LocalTime.now())

                    // protection_disabled_duration arrives as a Double so that servers reporting
                    // sub-millisecond precision parse instead of crashing the call (see
                    // ServerStatusDto). Round to whole milliseconds once, here, so the rest of
                    // the app keeps working in Long ms.
                    val pauseRemainingMs = status.protectionDisabledDuration?.toLong()
                    val isPaused = !status.protectionEnabled && (pauseRemainingMs != null && pauseRemainingMs > 0)

                    DashboardData(
                        protectionState = ProtectionState(
                            isEnabled = status.protectionEnabled,
                            isPaused = isPaused,
                            pauseRemainingMs = pauseRemainingMs,
                            serverVersion = status.version
                        ),
                        timePeriodText = periodText,
                        headlineCards = headlineCards,
                        generalStatistics = generalStatistics,
                        topClients = topClients,
                        topQueriedDomains = topQueriedDomains,
                        topBlockedDomains = topBlockedDomains,
                        topUpstreams = topUpstreams,
                        upstreamResponseTimes = upstreamResponseTimes,
                        lastUpdatedFormatted = now
                    )
                }
            }
        }
    }

    suspend fun setProtection(enabled: Boolean, durationMs: Long? = null): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val request = SetProtectionRequestDto(
                    enabled = enabled,
                    duration = if (!enabled && durationMs != null) durationMs else null
                )
                val response = api.setProtection(request)
                if (!response.isSuccessful) {
                    throw HttpException(response)
                }
            }
        }

    suspend fun getQueryLog(
        olderThan: String? = null,
        offset: Int? = null,
        limit: Int = 50,
        search: String? = null,
        reasons: List<String>? = null
    ): NetworkResult<List<QueryLogEntry>> = withContext(ioDispatcher) {
        queryLogMutex.withLock {
            safeApiCall {
                val response = api.getQueryLog(
                    olderThan = olderThan,
                    offset = offset,
                    limit = limit,
                    search = search?.ifBlank { null },
                    reasons = reasons?.ifEmpty { null }
                )

                response.data.map { item ->
                    val elapsedMsParsed = item.elapsedMs.toDoubleOrNull() ?: 0.0
                    val disposition = mapReasonToDisposition(item.reason)
                    val formattedTime = formatIsoTime(item.time)

                    val rules = item.rules.map { r ->
                        AppliedRule(
                            filterListId = r.filterListId,
                            filterListName = decodeBuiltInFilterName(r.filterListId),
                            ruleText = r.text
                        )
                    }

                    val clientName = item.clientInfo?.name?.ifBlank { null } ?: item.client

                    QueryLogEntry(
                        domain = item.question?.name ?: "",
                        clientNameOrIp = clientName,
                        clientIp = item.client,
                        queryType = item.question?.type ?: "A",
                        formattedTime = formattedTime,
                        rawIsoTime = item.time,
                        elapsedMs = elapsedMsParsed,
                        disposition = disposition,
                        reason = item.reason,
                        serviceName = item.serviceName,
                        isCached = item.cached,
                        appliedRules = rules
                    )
                }
            }
        }
    }

    suspend fun getFilteringData(): NetworkResult<FilterListsData> = withContext(ioDispatcher) {
        filteringMutex.withLock {
            safeApiCall {
                val status = api.getFilteringStatus()

                val blocklists = status.filters.map { it.toDomainFilterItem() }
                val allowlists = status.whitelistFilters.map { it.toDomainFilterItem() }

                val enabledBlocklists = blocklists.filter { it.isEnabled }
                val totalEnabledRules = enabledBlocklists.sumOf { it.rulesCount }

                FilterListsData(
                    blocklists = blocklists,
                    allowlists = allowlists,
                    userRules = status.userRules,
                    updateIntervalHours = status.interval,
                    isFilteringEnabled = status.enabled,
                    totalBlocklistsCount = blocklists.size,
                    enabledBlocklistsCount = enabledBlocklists.size,
                    totalEnabledRulesCount = totalEnabledRules
                )
            }
        }
    }

    suspend fun toggleFilter(url: String, whitelist: Boolean, enabled: Boolean, currentName: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val request = FilterSetUrlRequestDto(
                    url = url,
                    whitelist = whitelist,
                    data = FilterSetUrlDataDto(
                        enabled = enabled,
                        name = currentName,
                        url = url
                    )
                )
                val response = api.setFilterUrl(request)
                if (!response.isSuccessful) throw HttpException(response)
            }
        }

    suspend fun addFilter(name: String, url: String, whitelist: Boolean): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val request = FilterAddUrlRequestDto(
                    name = name.trim(),
                    url = url.trim(),
                    whitelist = whitelist
                )
                val response = api.addFilter(request)
                if (!response.isSuccessful) throw HttpException(response)
            }
        }

    suspend fun removeFilter(url: String, whitelist: Boolean): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val request = FilterRemoveUrlRequestDto(url = url, whitelist = whitelist)
                val response = api.removeFilter(request)
                if (!response.isSuccessful) throw HttpException(response)
            }
        }

    suspend fun editFilter(originalUrl: String, whitelist: Boolean, newName: String, newUrl: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val request = FilterSetUrlRequestDto(
                    url = originalUrl,
                    whitelist = whitelist,
                    data = FilterSetUrlDataDto(
                        enabled = true,
                        name = newName.trim(),
                        url = newUrl.trim()
                    )
                )
                val response = api.setFilterUrl(request)
                if (!response.isSuccessful) throw HttpException(response)
            }
        }

    suspend fun refreshFilters(whitelist: Boolean = false): NetworkResult<Int> =
        withContext(ioDispatcher) {
            safeApiCall {
                val result = api.refreshFilters(mapOf("whitelist" to whitelist))
                result.updated
            }
        }

    suspend fun setFilteringUpdateInterval(intervalHours: Int): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = api.setFilteringConfig(FilteringConfigRequestDto(enabled = intervalHours > 0, interval = intervalHours))
                if (!response.isSuccessful) throw HttpException(response)
            }
        }

    suspend fun saveUserRules(rules: List<String>): NetworkResult<Unit> = withContext(ioDispatcher) {
        safeApiCall {
            val response = api.setUserRules(SetRulesRequestDto(rules = rules))
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    /**
     * Appends a block or allow rule without clobbering existing custom rules.
     * Reads current rules, appends, and writes back full list.
     */
    suspend fun appendDomainRule(domain: String, isBlock: Boolean): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            safeApiCall {
                val currentStatus = api.getFilteringStatus()
                val newRule = if (isBlock) "||$domain^" else "@@||$domain^"
                if (!currentStatus.userRules.contains(newRule)) {
                    val updatedRules = currentStatus.userRules + newRule
                    val response = api.setUserRules(SetRulesRequestDto(rules = updatedRules))
                    if (!response.isSuccessful) throw HttpException(response)
                }
            }
        }

    suspend fun getClients(): NetworkResult<List<ClientItem>> = withContext(ioDispatcher) {
        safeApiCall {
            val response = api.getClients()
            val configured = response.clients.map { c ->
                ClientItem(
                    name = c.name,
                    primaryIpOrId = c.ip.ifBlank { c.ids.firstOrNull() ?: "" },
                    allIds = c.ids,
                    isFilteringEnabled = c.filteringEnabled,
                    isParentalEnabled = c.parentalEnabled,
                    isSafeBrowsingEnabled = c.safebrowsingEnabled,
                    isAutoDiscovered = false,
                    tags = c.tags,
                    blockedServices = c.blockedServices
                )
            }
            val auto = response.autoClients.map { c ->
                ClientItem(
                    name = c.name.ifBlank { c.ip },
                    primaryIpOrId = c.ip.ifBlank { c.ids.firstOrNull() ?: "" },
                    allIds = c.ids,
                    isFilteringEnabled = c.filteringEnabled,
                    isParentalEnabled = c.parentalEnabled,
                    isSafeBrowsingEnabled = c.safebrowsingEnabled,
                    isAutoDiscovered = true,
                    tags = c.tags,
                    blockedServices = c.blockedServices
                )
            }
            configured + auto
        }
    }

    private fun FilterDto.toDomainFilterItem(): FilterItem {
        val relativeTime = formatRelativeTime(lastUpdated)
        return FilterItem(
            id = id,
            name = name,
            url = url,
            isEnabled = enabled,
            rulesCount = rulesCount,
            lastUpdatedRelative = relativeTime,
            lastUpdatedRaw = lastUpdated
        )
    }

    private fun formatIntervalText(intervalMs: Long): String {
        val hours = intervalMs / 3600000L
        val days = hours / 24L
        return when {
            days > 0 -> "for the last $days day${if (days > 1) "s" else ""}"
            hours > 0 -> "for the last $hours hour${if (hours > 1) "s" else ""}"
            else -> "for the last $intervalMs ms"
        }
    }

    private fun formatIsoTime(isoString: String): String {
        return try {
            val odt = OffsetDateTime.parse(isoString)
            odt.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()))
        } catch (e: Exception) {
            isoString
        }
    }

    private fun formatRelativeTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "Never"
        return try {
            val odt = OffsetDateTime.parse(isoString)
            val diffSeconds = java.time.Duration.between(odt.toInstant(), java.time.Instant.now()).seconds
            when {
                diffSeconds < 60 -> "just now"
                diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
                diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
                else -> "${diffSeconds / 86400}d ago"
            }
        } catch (e: Exception) {
            isoString
        }
    }

    private inline fun <T> safeApiCall(call: () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(call())
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> NetworkResult.Error(
                    errorType = NetworkErrorType.UNAUTHORIZED,
                    message = "Credentials rejected by server."
                )
                429 -> {
                    val retryAfter = e.response()?.headers()?.get("Retry-After")?.toIntOrNull()
                    NetworkResult.Error(
                        errorType = NetworkErrorType.RATE_LIMITED,
                        message = "Too many failed attempts. Rate limited.",
                        retryAfterSeconds = retryAfter
                    )
                }
                else -> NetworkResult.Error(
                    errorType = NetworkErrorType.SERVER_ERROR,
                    message = "Server error HTTP ${e.code()}"
                )
            }
        } catch (e: SSLHandshakeException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.TLS_ERROR,
                message = "Certificate error. Check self-signed trust setting."
            )
        } catch (e: SSLException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.TLS_ERROR,
                message = "TLS Handshake failed."
            )
        } catch (e: SocketTimeoutException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.UNREACHABLE,
                message = "Request timed out."
            )
        } catch (e: ConnectException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.UNREACHABLE,
                message = "Could not reach server on LAN."
            )
        } catch (e: UnknownHostException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.UNREACHABLE,
                message = "Unable to resolve server host."
            )
        } catch (e: IOException) {
            NetworkResult.Error(
                errorType = NetworkErrorType.UNREACHABLE,
                message = "Network error: ${e.localizedMessage ?: "Unreachable"}"
            )
        } catch (e: SerializationException) {
            // The server answered, but a field was shaped differently than this AdGuard version's
            // spec describes. Report it as a response-format problem in plain language rather than
            // letting the raw decoder message -- which embeds a slice of the response body -- reach
            // a snackbar. Full detail still goes to Logcat on debug builds for diagnosis.
            NetworkResult.Error(
                errorType = NetworkErrorType.INVALID_RESPONSE,
                message = "The server sent a response this app couldn't read. It may be running a " +
                    "different AdGuard Home version than expected."
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                errorType = NetworkErrorType.UNKNOWN,
                message = e.localizedMessage ?: "Unknown error"
            )
        }
    }
}
