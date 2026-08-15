package com.adguard.home.ui.navigation

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Main : Screen("main")
    data object Dashboard : Screen("dashboard")
    data object QueryLog : Screen("query_log?reason={reason}") {
        fun createRoute(reason: String? = null) = if (reason != null) "query_log?reason=$reason" else "query_log"
    }
    data object Filters : Screen("filters")
    data object Clients : Screen("clients")
    data object Settings : Screen("settings")
    data object ServerConnection : Screen("server_connection")
}
