package com.adguard.home.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adguard.home.domain.model.ProtectionState
import com.adguard.home.ui.components.AdGuardPullToRefresh
import com.adguard.home.ui.components.GeneralStatsCard
import com.adguard.home.ui.components.HeadlineMetricCard
import com.adguard.home.ui.components.PauseProtectionSheet
import com.adguard.home.ui.components.TopClientsMetricCard
import com.adguard.home.ui.components.TopDomainsMetricCard
import com.adguard.home.ui.components.TopUpstreamsMetricCard
import com.adguard.home.ui.components.UpstreamResponseTimesCard
import com.adguard.home.ui.theme.DarkTertiary
import com.adguard.home.ui.theme.MetricBlockedFiltering
import com.adguard.home.ui.theme.MetricDnsQueries
import com.adguard.home.ui.theme.MetricParental
import com.adguard.home.ui.theme.MetricSafeBrowsing
import com.adguard.home.ui.theme.MetricUpstreamFast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToQueryLog: (reason: String?) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()
    var showPauseSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackBarMessages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Retry",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refresh()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Read the state's data once per recomposition into a local. Previously the screen
        // null-checked `state.data` and then dereferenced `state.data!!`; because `state` is a
        // delegated Compose State read, those are two separate reads of a value that can change
        // between them, so the `!!` could in principle throw. A single local read is both safer
        // and cheaper.
        val data = state.data

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // SECTION A: Action row -- pinned at the top and deliberately OUTSIDE the scrolling
            // column below, so the protection toggle and "Refresh statistics" stay reachable no
            // matter how far down the dashboard is scrolled.
            DashboardActionRow(
                protectionState = data?.protectionState,
                isRefreshing = state.isRefreshing,
                lastUpdatedFormatted = data?.lastUpdatedFormatted,
                isStale = state.errorMessage != null,
                onRefresh = { viewModel.refresh() },
                onToggleProtection = { enable ->
                    if (enable) {
                        viewModel.setProtection(enabled = true, durationMs = null)
                    } else {
                        showPauseSheet = true
                    }
                },
                onResumeNow = { viewModel.setProtection(enabled = true, durationMs = null) }
            )

            // weight(1f), not fillMaxSize(): a Column measures a fillMaxSize child against the
            // full column height rather than the height left over after the pinned row above,
            // which would push the bottom of the scroll area off-screen.
            AdGuardPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.isInitialLoading && data == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (data != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SECTION B: 2x2 Headline Metrics Grid
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HeadlineMetricCard(
                                    metric = data.headlineCards.dnsQueries,
                                    sparklineColor = MetricDnsQueries,
                                    modifier = Modifier.weight(1f)
                                )
                                HeadlineMetricCard(
                                    metric = data.headlineCards.blockedFiltering,
                                    sparklineColor = MetricBlockedFiltering,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HeadlineMetricCard(
                                    metric = data.headlineCards.blockedMalware,
                                    sparklineColor = MetricSafeBrowsing,
                                    modifier = Modifier.weight(1f)
                                )
                                HeadlineMetricCard(
                                    metric = data.headlineCards.blockedAdult,
                                    sparklineColor = MetricParental,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // SECTION C: General Statistics.
                        // Rows 1-5 carry the AdGuard `reason` value for their category, so pass it
                        // straight through to the query log instead of discarding it -- tapping
                        // "Blocked by Filters" must land on a log pre-filtered to that category.
                        GeneralStatsCard(
                            stats = data.generalStatistics,
                            periodText = data.timePeriodText,
                            onRefreshCard = { viewModel.refresh() },
                            onCategoryClick = { reason ->
                                onNavigateToQueryLog(reason.takeIf { it != "all" })
                            }
                        )

                        // SECTION D: Top Clients
                        TopClientsMetricCard(
                            title = "Top Clients",
                            items = data.topClients,
                            barColor = DarkTertiary
                        )

                        // SECTION E: Top Queried Domains
                        TopDomainsMetricCard(
                            title = "Top Queried Domains",
                            items = data.topQueriedDomains,
                            barColor = MetricDnsQueries,
                            onBlockDomain = { viewModel.blockDomain(it) },
                            onUnblockDomain = { viewModel.unblockDomain(it) }
                        )

                        // SECTION F: Top Blocked Domains
                        TopDomainsMetricCard(
                            title = "Top Blocked Domains",
                            items = data.topBlockedDomains,
                            barColor = MetricBlockedFiltering,
                            onBlockDomain = { viewModel.blockDomain(it) },
                            onUnblockDomain = { viewModel.unblockDomain(it) }
                        )

                        // SECTIONS G & H: upstream statistics.
                        // These two fields are the ones most likely to be absent on an older
                        // AdGuard Home build. The spec is explicit that a card whose underlying
                        // field is missing must be HIDDEN rather than rendered as zeros or an
                        // empty placeholder, so gate both on actually having data.
                        if (data.topUpstreams.isNotEmpty()) {
                            TopUpstreamsMetricCard(
                                title = "Top Upstreams",
                                items = data.topUpstreams,
                                barColor = MetricUpstreamFast
                            )
                        }

                        if (data.upstreamResponseTimes.isNotEmpty()) {
                            UpstreamResponseTimesCard(
                                title = "Upstream Response Times",
                                items = data.upstreamResponseTimes,
                                barColor = MetricUpstreamFast
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showPauseSheet) {
        PauseProtectionSheet(
            sheetState = sheetState,
            onDismissRequest = { showPauseSheet = false },
            onDurationSelected = { durationMs ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    showPauseSheet = false
                    viewModel.setProtection(enabled = false, durationMs = durationMs)
                }
            }
        )
    }
}

/**
 * Section A of the dashboard: the pinned action row.
 *
 * Holds the protection toggle (with live pause countdown and "resume now"), the
 * "Refresh statistics" action that refreshes every card in one pass, and the
 * "last updated HH:mm" line that makes stale data obvious after a failed refresh.
 */
@Composable
private fun DashboardActionRow(
    protectionState: ProtectionState?,
    isRefreshing: Boolean,
    lastUpdatedFormatted: String?,
    isStale: Boolean,
    onRefresh: () -> Unit,
    onToggleProtection: (Boolean) -> Unit,
    onResumeNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        if (protectionState != null) {
            ProtectionMasterCard(
                isEnabled = protectionState.isEnabled,
                isPaused = protectionState.isPaused,
                pauseRemainingMs = protectionState.pauseRemainingMs,
                onToggle = onToggleProtection,
                onResumeNow = onResumeNow
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    lastUpdatedFormatted == null -> "Loading statistics…"
                    isStale -> "Stale — last updated $lastUpdatedFormatted"
                    else -> "Last updated $lastUpdatedFormatted"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isStale) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )

            // Refresh stays enabled and the cards below stay populated and readable while a
            // refresh runs -- the indicator replaces only the icon, never the data.
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh statistics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionMasterCard(
    isEnabled: Boolean,
    isPaused: Boolean,
    pauseRemainingMs: Long?,
    onToggle: (Boolean) -> Unit,
    onResumeNow: () -> Unit
) {
    // Purely UI-local countdown ticker. This makes no network calls -- it just counts down the
    // pause window the server already reported, so it does not violate the app's
    // no-polling/no-background-work rule.
    var remainingMs by remember { mutableStateOf(0L) }
    LaunchedEffect(pauseRemainingMs, isPaused) {
        val start = if (isPaused) (pauseRemainingMs ?: 0L) else 0L
        remainingMs = start
        while (remainingMs > 0L) {
            delay(1000L)
            remainingMs = (remainingMs - 1000L).coerceAtLeast(0L)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled) "Protection is Active" else if (isPaused) "Protection Paused" else "Protection Disabled",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = when {
                            isEnabled -> "AdGuard Home is filtering DNS traffic"
                            isPaused && remainingMs > 0L -> "Resuming in ${formatCountdown(remainingMs)}"
                            isPaused -> "Protection temporarily paused"
                            else -> "All traffic passes unfiltered"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (isPaused) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResumeNow,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Resume protection now")
                }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = millis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
