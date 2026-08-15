package com.adguard.home.domain.model

enum class QueryDisposition {
    BLOCKED,
    ALLOWED,
    REWRITTEN
}

data class QueryLogEntry(
    val domain: String,
    val clientNameOrIp: String,
    val clientIp: String,
    val queryType: String,
    val formattedTime: String,
    val rawIsoTime: String,
    val elapsedMs: Double,
    val disposition: QueryDisposition,
    val reason: String,
    val serviceName: String?,
    val isCached: Boolean,
    val appliedRules: List<AppliedRule>
)

data class AppliedRule(
    val filterListId: Long,
    val filterListName: String,
    val ruleText: String
)

fun mapReasonToDisposition(reason: String): QueryDisposition {
    return when {
        reason.startsWith("Filtered") -> QueryDisposition.BLOCKED
        reason.startsWith("Rewrite") -> QueryDisposition.REWRITTEN
        else -> QueryDisposition.ALLOWED
    }
}

fun decodeBuiltInFilterName(filterId: Long): String {
    return when (filterId) {
        0L -> "User rules"
        -1L -> "/etc/hosts"
        -2L -> "Blocked services"
        -3L -> "Parental control"
        -4L -> "Safe browsing"
        -5L -> "Safe search"
        else -> "List #$filterId"
    }
}
