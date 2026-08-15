package com.adguard.home.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adguard.home.data.local.model.ServerConfig
import com.adguard.home.ui.clients.ClientsScreen
import com.adguard.home.ui.connection.ServerConnectionScreen
import com.adguard.home.ui.dashboard.DashboardScreen
import com.adguard.home.ui.filters.FiltersScreen
import com.adguard.home.ui.querylog.QueryLogScreen
import com.adguard.home.ui.settings.SettingsScreen

sealed class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector) {
    data object Dashboard : BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Default.Dashboard)
    data object QueryLog : BottomNavItem(Screen.QueryLog, "Query Log", Icons.Default.List)
    data object Filters : BottomNavItem(Screen.Filters, "Filters", Icons.Default.FilterList)
    data object Clients : BottomNavItem(Screen.Clients, "Clients", Icons.Default.Devices)
}

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem.Dashboard,
    BottomNavItem.QueryLog,
    BottomNavItem.Filters,
    BottomNavItem.Clients
)

@Composable
fun AppNavigation(
    serverConfig: ServerConfig?,
    navController: NavHostController = rememberNavController()
) {
    if (serverConfig == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (serverConfig.isConfigured) Screen.Main.route else Screen.Setup.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Setup.route) {
            ServerConnectionScreen(
                isFirstLaunch = true,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onNavigateBack = {}
            )
        }

        composable(Screen.Main.route) {
            MainContainerScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToServerConnection = {
                    navController.navigate(Screen.ServerConnection.route)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ServerConnection.route) {
            ServerConnectionScreen(
                isFirstLaunch = false,
                onNavigateToDashboard = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    onNavigateToSettings: () -> Unit
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentTitle = when {
                        currentRoute?.startsWith("query_log") == true -> "Query Log"
                        currentRoute == Screen.Filters.route -> "Filters"
                        currentRoute == Screen.Clients.route -> "Clients"
                        else -> "Dashboard"
                    }
                    Text(currentTitle)
                },
                actions = {
                    IconButton(onClick = { overflowMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                overflowMenuExpanded = false
                                onNavigateToSettings()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                BOTTOM_NAV_ITEMS.forEach { item ->
                    val isSelected = when (item) {
                        BottomNavItem.QueryLog -> currentRoute?.startsWith("query_log") == true
                        else -> currentRoute == item.screen.route
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            bottomNavController.navigate(item.screen.route) {
                                popUpTo(bottomNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToQueryLog = { reason ->
                        bottomNavController.navigate(Screen.QueryLog.createRoute(reason)) {
                            popUpTo(bottomNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.QueryLog.route,
                arguments = listOf(navArgument("reason") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                QueryLogScreen()
            }

            composable(Screen.Filters.route) {
                FiltersScreen()
            }

            composable(Screen.Clients.route) {
                ClientsScreen()
            }
        }
    }
}
