package com.odbplus.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
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
        icon = Icons.Filled.DirectionsCar
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

    companion object {
        val items = listOf(AiChat, OdbConnection, Parts, Tools)
    }
}
