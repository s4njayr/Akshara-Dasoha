package com.aksharadasoha.ledger.ui.navigation

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Ledger : Route("ledger")
    data object Config : Route("config")
    data object Backup : Route("backup")
    data object Settings : Route("settings")
    data object Reports : Route("reports")
}
