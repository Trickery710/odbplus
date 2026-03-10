package com.odbplus.app.nav

import com.odbplus.app.expertdiag.ui.ExpertDiagnosticScreen
import com.odbplus.app.ui.AiChatScreen
import com.odbplus.app.ui.SplashScreen
import com.odbplus.app.ui.CodesScreen
import com.odbplus.app.ui.ConnectScreen
import com.odbplus.app.ui.DiagnosticHudScreen
import com.odbplus.app.ui.GuidedRpmTestScreen
import com.odbplus.app.ui.LiveScreen
import com.odbplus.app.ui.LogsScreen
import com.odbplus.app.ui.OdbHubScreen
import com.odbplus.app.ui.PartsAndToolsScreen
import com.odbplus.app.ui.TerminalScreen
import com.odbplus.app.ui.SessionDetailScreen
import com.odbplus.app.ui.SettingsScreen
import com.odbplus.app.ui.VehicleDetailScreen
import com.odbplus.app.ui.VehicleHistoryScreen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.ai.VehicleContextViewModel
import com.odbplus.app.ui.theme.*

@Composable
fun AppScreen() {
    // Instantiate at app scope so it collects connection/vehicle data for the lifetime of the session.
    hiltViewModel<VehicleContextViewModel>()

    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if bottom bar should be visible (hide on sub-screens if needed)
    val showBottomBar = true // Always show for this app

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.AiChat.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Tab 1: AI Chat (Default)
            composable(BottomNavItem.AiChat.route) {
                AiChatScreen()
            }

            // Tab 2: ODB Hub with nested navigation
            navigation(
                startDestination = "odb_hub/home",
                route = BottomNavItem.OdbConnection.route
            ) {
                composable("odb_hub/home") {
                    OdbHubScreen(
                        onNavigate = { route ->
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("odb_hub/connect") {
                    ConnectScreen()
                }
                composable("odb_hub/live") {
                    LiveScreen()
                }
                composable("odb_hub/codes") {
                    CodesScreen()
                }
                composable("odb_hub/terminal") {
                    TerminalScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("odb_hub/logs") {
                    LogsScreen(
                        onSessionClick = { sessionId ->
                            navController.navigate("vehicle/session/$sessionId") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("odb_hub/tests") {
                    DiagnosticHudScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("odb_hub/expert_diag") {
                    ExpertDiagnosticScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("odb_hub/guided_test") {
                    GuidedRpmTestScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Tab 3: Parts & Tools (combined)
            composable(BottomNavItem.Parts.route) {
                PartsAndToolsScreen(
                    onNavigateToAi = {
                        navController.navigate(BottomNavItem.AiChat.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // Tab 4: Vehicle History
            // Tab 5: Settings (standalone tab — moved out of ODB Hub)
            composable(BottomNavItem.Settings.route) {
                SettingsScreen()
            }

            // Tab 4: Vehicle History
            navigation(
                startDestination = "vehicle/history",
                route = BottomNavItem.Vehicle.route
            ) {
                composable("vehicle/history") {
                    VehicleHistoryScreen(
                        onVehicleClick = { vin ->
                            navController.navigate("vehicle/detail/$vin") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("vehicle/detail/{vin}") { backStackEntry ->
                    VehicleDetailScreen(
                        vin = backStackEntry.arguments?.getString("vin") ?: "",
                        onBack = { navController.popBackStack() },
                        onSessionClick = { sessionId ->
                            navController.navigate("vehicle/session/$sessionId") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("vehicle/session/{sessionId}") { backStackEntry ->
                    SessionDetailScreen(
                        sessionId = backStackEntry.arguments?.getString("sessionId") ?: "",
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    currentRoute: String?
) {
    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        BottomNavItem.items.forEach { item ->
            val selected = currentRoute?.startsWith(item.route) == true
            NavigationBarItem(
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.title,
                        modifier = Modifier
                    )
                },
                label = {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyanPrimary,
                    selectedTextColor = CyanPrimary,
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor = CyanPrimary.copy(alpha = 0.12f)
                )
            )
        }
    }
}
