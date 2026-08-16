package com.adguard.home.ui.filters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adguard.home.ui.components.AdGuardPullToRefresh

val FILTER_TABS = listOf("Blocklists", "Allowlists", "Custom rules")

val UPDATE_INTERVALS = listOf(
    Pair("Disabled", 0),
    Pair("1 hour", 1),
    Pair("12 hours", 12),
    Pair("24 hours (Daily)", 24),
    Pair("3 days", 72),
    Pair("7 days (Weekly)", 168)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    viewModel: FiltersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FilterUiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        event.onAction?.invoke()
                    }
                }
            }
        }
    }

    // contentWindowInsets = 0: see DashboardScreen.kt for why -- this Scaffold only hosts the
    // snackbar; the real topBar/inset handling lives in MainContainerScreen's outer Scaffold.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        AdGuardPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Transparent container: TabRow defaults to colorScheme.surface, which can render
                // a subtly different tone than the screen's actual background and read as an
                // extra block of header space stacked below the shared TopAppBar. Transparent
                // removes that seam so the tab row sits flush against the same background the
                // rest of the screen uses.
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent
                ) {
                    FILTER_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                if (state.isLoading && state.data == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    state.data?.let { data ->
                        when (selectedTabIndex) {
                            0 -> FilterListTab(
                                whitelist = false,
                                items = data.blocklists,
                                isCheckingUpdates = state.isCheckingUpdates,
                                updateIntervalHours = data.updateIntervalHours,
                                onSetUpdateInterval = { viewModel.setUpdateInterval(it) },
                                onToggle = { item, enabled -> viewModel.toggleFilter(item, whitelist = false, enabled) },
                                onAddFilter = { name, url -> viewModel.addFilter(name, url, whitelist = false) {} },
                                onEditFilter = { orig, name, url -> viewModel.editFilter(orig, name, url, whitelist = false) {} },
                                onDeleteFilter = { item -> viewModel.deleteFilter(item, whitelist = false) },
                                onCheckForUpdates = { viewModel.checkForUpdates() }
                            )
                            1 -> FilterListTab(
                                whitelist = true,
                                items = data.allowlists,
                                isCheckingUpdates = state.isCheckingUpdates,
                                updateIntervalHours = data.updateIntervalHours,
                                onSetUpdateInterval = { viewModel.setUpdateInterval(it) },
                                onToggle = { item, enabled -> viewModel.toggleFilter(item, whitelist = true, enabled) },
                                onAddFilter = { name, url -> viewModel.addFilter(name, url, whitelist = true) {} },
                                onEditFilter = { orig, name, url -> viewModel.editFilter(orig, name, url, whitelist = true) {} },
                                onDeleteFilter = { item -> viewModel.deleteFilter(item, whitelist = true) },
                                onCheckForUpdates = { viewModel.checkForUpdates() }
                            )
                            2 -> CustomRulesTab(
                                rulesText = state.customRulesText,
                                isDirty = state.isCustomRulesDirty,
                                isSaving = state.isSavingRules,
                                onTextChanged = { viewModel.onCustomRulesTextChanged(it) },
                                onSave = { viewModel.saveCustomRules() }
                            )
                        }
                    }
                }
            }
        }
    }
}
