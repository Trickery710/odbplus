package com.odbplus.app.nav

import com.odbplus.app.ui.ConnectScreen
import com.odbplus.app.ui.LiveScreen
import com.odbplus.app.ui.CodesScreen
import com.odbplus.app.ui.DiagnosticsScreen
import com.odbplus.app.ui.EcuProfileScreen

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.padding

// --- Destination Enum ---
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Connect("connect", "Connect", Icons.Filled.Link),
    Live("live", "Live", Icons.Filled.Speed),
    Codes("codes", "Codes", Icons.Filled.Warning),
    Diagnostics("diag", "Diag", Icons.Filled.Build),
    EcuProfile("ecu", "ECU", Icons.Filled.Memory)
}

// --- Navigation Host ---
@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = nav, startDestination = Dest.Connect.route, modifier = modifier) {
        composable(Dest.Connect.route) { ConnectScreen() }
        composable(Dest.Live.route) { LiveScreen() }
        composable(Dest.Codes.route) { CodesScreen() }
        composable(Dest.Diagnostics.route) { DiagnosticsScreen() }
        composable(Dest.EcuProfile.route) { EcuProfileScreen() }
    }
}

// --- Bottom Navigation Bar ---
@Composable
fun BottomBar(nav: NavHostController) {
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    val items = listOf(Dest.Connect, Dest.Live, Dest.Codes, Dest.Diagnostics, Dest.EcuProfile)

    NavigationBar {
        items.forEach { d ->
            NavigationBarItem(
                selected = currentRoute == d.route,
                onClick = { if (currentRoute != d.route) nav.navigate(d.route) },
                icon = { Icon(d.icon, contentDescription = d.label) },
                label = { Text(d.label) }
            )
        }
    }
}

@Composable
fun AppScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomBar(nav = navController) }
    ) { innerPadding ->
        AppNavHost(
            nav = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
