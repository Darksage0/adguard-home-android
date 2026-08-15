package com.adguard.home.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryLogResponseDto(
    @SerialName("oldest") val oldest: String = "",
    @SerialName("data") val data: List<QueryLogItemDto> = emptyList()
)

@Serializable
data class QueryLogItemDto(
    @SerialName("time") val time: String = "",
    @SerialName("elapsedMs") val elapsedMs: String = "0",
    @SerialName("client") val client: String = "",
    @SerialName("client_proto") val clientProto: String? = null,
    @SerialName("client_info") val clientInfo: ClientInfoDto? = null,
    @SerialName("question") val question: QuestionDto? = null,
    @SerialName("reason") val reason: String = "NotFilteredNotFound",
    @SerialName("service_name") val serviceName: String? = null,
    @SerialName("cached") val cached: Boolean = false,
    @SerialName("status") val status: String? = null,
    @SerialName("rules") val rules: List<QueryRuleDto> = emptyList()
)

@Serializable
data class ClientInfoDto(
    @SerialName("name") val name: String? = null,
    @SerialName("disallowed") val disallowed: Boolean = false,
    @SerialName("disallowed_rule") val disallowedRule: String? = null
)

@Serializable
data class QuestionDto(
    @SerialName("name") val name: String = "",
    @SerialName("unicode_name") val unicodeName: String? = null,
    @SerialName("type") val type: String = "A",
    @SerialName("class") val qClass: String? = null
)

@Serializable
data class QueryRuleDto(
    @SerialName("filter_list_id") val filterListId: Long = 0,
    @SerialName("text") val text: String = ""
)
