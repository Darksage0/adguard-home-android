package com.adguard.home.data.remote

import com.adguard.home.data.remote.model.ClientsResponseDto
import com.adguard.home.data.remote.model.FilterAddUrlRequestDto
import com.adguard.home.data.remote.model.FilterRemoveUrlRequestDto
import com.adguard.home.data.remote.model.FilterSetUrlRequestDto
import com.adguard.home.data.remote.model.FilteringConfigRequestDto
import com.adguard.home.data.remote.model.FilteringRefreshResponseDto
import com.adguard.home.data.remote.model.FilteringStatusDto
import com.adguard.home.data.remote.model.QueryLogResponseDto
import com.adguard.home.data.remote.model.ServerStatusDto
import com.adguard.home.data.remote.model.SetProtectionRequestDto
import com.adguard.home.data.remote.model.SetRulesRequestDto
import com.adguard.home.data.remote.model.StatsConfigDto
import com.adguard.home.data.remote.model.StatsDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AdGuardApi {

    @GET("control/status")
    suspend fun getStatus(): ServerStatusDto

    @GET("control/stats")
    suspend fun getStats(
        @Query("recent") recent: Long? = null
    ): StatsDto

    @GET("control/stats/config")
    suspend fun getStatsConfig(): StatsConfigDto

    @POST("control/protection")
    suspend fun setProtection(
        @Body request: SetProtectionRequestDto
    ): Response<Unit>

    @GET("control/querylog")
    suspend fun getQueryLog(
        @Query("older_than") olderThan: String? = null,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("search") search: String? = null,
        @Query("reason") reasons: List<String>? = null
    ): QueryLogResponseDto

    @GET("control/filtering/status")
    suspend fun getFilteringStatus(): FilteringStatusDto

    @POST("control/filtering/add_url")
    suspend fun addFilter(
        @Body request: FilterAddUrlRequestDto
    ): Response<Unit>

    @POST("control/filtering/remove_url")
    suspend fun removeFilter(
        @Body request: FilterRemoveUrlRequestDto
    ): Response<Unit>

    @POST("control/filtering/set_url")
    suspend fun setFilterUrl(
        @Body request: FilterSetUrlRequestDto
    ): Response<Unit>

    @POST("control/filtering/refresh")
    suspend fun refreshFilters(
        @Body request: Map<String, Boolean>
    ): FilteringRefreshResponseDto

    @POST("control/filtering/config")
    suspend fun setFilteringConfig(
        @Body request: FilteringConfigRequestDto
    ): Response<Unit>

    @POST("control/filtering/set_rules")
    suspend fun setUserRules(
        @Body request: SetRulesRequestDto
    ): Response<Unit>

    @GET("control/clients")
    suspend fun getClients(): ClientsResponseDto
}
