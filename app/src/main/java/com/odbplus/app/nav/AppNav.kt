package com.odbplus.app.nav

import com.odbplus.app.ui.HomeScreen
import com.odbplus.app.ui.LiveScreen
import com.odbplus.app.ui.CodesScreen
import com.odbplus.app.ui.AiChatScreen
import com.odbplus.app.ui.LogsScreen
import com.odbplus.app.ui.TerminalScreen
import com.odbplus.app.ui.PartsScreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

// --- Destination Enum ---
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Connect("connect", "Connect", Icons.Filled.Link),
    Live("live", "Live", Icons.Filled.Speed),
    Logs("logs", "Logs", Icons.Filled.History),
    Codes("codes", "Codes", Icons.Filled.Warning),
    Diagnostics("diag", "Diag", Icons.Filled.Build),
    Terminal("terminal", "Terminal", Icons.Filled.Terminal),
    Parts("parts", "Parts", Icons.Filled.ShoppingCart)
}

// --- Navigation Host ---
@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = nav, startDestination = Dest.Home.route, modifier = modifier) {
        composable(Dest.Home.route) {
            HomeScreen(
                onNavigate = { destination ->
                    nav.navigate(destination) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Dest.Live.route) { LiveScreen() }
        composable(Dest.Logs.route) {
            LogsScreen(
                onReplaySession = { session ->
                    nav.navigate(Dest.Live.route) {
                        popUpTo(Dest.Live.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Dest.Codes.route) { CodesScreen() }
        composable(Dest.Diagnostics.route) { AiChatScreen() }
        composable(Dest.Terminal.route) {
            TerminalScreen(
                onBack = { nav.popBackStack() }
            )
        }
        composable(Dest.Parts.route) {
            PartsScreen(
                onBack = { nav.popBackStack() },
                onNavigateToAi = {
                    nav.navigate(Dest.Diagnostics.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

@Composable
fun AppScreen() {
    val navController = rememberNavController()
    AppNavHost(
        nav = navController,
        modifier = Modifier
    )
}
