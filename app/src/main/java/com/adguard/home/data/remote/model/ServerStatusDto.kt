package com.adguard.home.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerStatusDto(
    @SerialName("protection_enabled") val protectionEnabled: Boolean = true,
    @SerialName("dns_addresses") val dnsAddresses: List<String> = emptyList(),
    @SerialName("dns_port") val dnsPort: Int = 53,
    @SerialName("http_port") val httpPort: Int = 80,
    @SerialName("running") val running: Boolean = true,
    @SerialName("version") val version: String = "",
    @SerialName("language") val language: String = "en",

    // NOTE: both millisecond fields below are typed Double?, NOT Long?, and that is deliberate.
    //
    // The OpenAPI spec types these as int64 milliseconds, but real servers do not all honour
    // that: this one reports `"start_time":1786713564601.578` -- millisecond precision carried
    // as a JSON float, presumably from a float64 conversion on the Go side. kotlinx.serialization
    // will not parse a fractional literal into a Long; it throws, and because the failure happens
    // while decoding the response body it takes down the ENTIRE /control/status call, not just
    // this one field. That blanked the whole dashboard (status is fetched alongside stats) while
    // every other screen kept working, because no other screen calls /control/status.
    //
    // Double parses both `0` and `1786713564601.578`, so this reads correctly against servers
    // that emit either form. Callers that need whole milliseconds convert with toLong().
    @SerialName("protection_disabled_duration") val protectionDisabledDuration: Double? = null,
    @SerialName("protection_disabled_until") val protectionDisabledUntil: String? = null,
    @SerialName("dhcp_available") val dhcpAvailable: Boolean? = null,
    @SerialName("start_time") val startTime: Double? = null
)
