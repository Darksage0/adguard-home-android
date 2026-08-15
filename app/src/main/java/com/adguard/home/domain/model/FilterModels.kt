package com.adguard.home.domain.model

data class FilterItem(
    val id: Long,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val rulesCount: Long,
    val lastUpdatedRelative: String,
    val lastUpdatedRaw: String?
)

data class FilterListsData(
    val blocklists: List<FilterItem>,
    val allowlists: List<FilterItem>,
    val userRules: List<String>,
    val updateIntervalHours: Int,
    val isFilteringEnabled: Boolean,
    val totalBlocklistsCount: Int,
    val enabledBlocklistsCount: Int,
    val totalEnabledRulesCount: Long
)
