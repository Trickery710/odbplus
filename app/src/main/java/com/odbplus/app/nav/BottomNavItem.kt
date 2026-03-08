package com.odbplus.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object AiChat : BottomNavItem(
        route = "ai_chat",
        title = "AI Chat",
        icon = Icons.Filled.Psychology
    )

    object OdbConnection : BottomNavItem(
        route = "odb_hub",
        title = "ODB",
        icon = Icons.Filled.Cable
    )

    object Parts : BottomNavItem(
        route = "parts",
        title = "Parts",
        icon = Icons.Filled.ShoppingCart
    )

    object Tools : BottomNavItem(
        route = "tools",
        title = "Tools",
        icon = Icons.Filled.Build
    )

    object Vehicle : BottomNavItem(
        route = "vehicle",
        title = "Vehicle",
        icon = Icons.Filled.History
    )

    companion object {
        val items = listOf(AiChat, OdbConnection, Parts, Tools, Vehicle)
    }
}
