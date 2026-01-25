package com.odbplus.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.core.transport.ConnectionState

data class HomeMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val description: String,
    val requiresConnection: Boolean = false
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    val menuItems = listOf(
        HomeMenuItem(
            id = "live",
            label = "Live Data",
            icon = Icons.Filled.Speed,
            description = "Real-time vehicle data",
            requiresConnection = true
        ),
        HomeMenuItem(
            id = "codes",
            label = "Codes",
            icon = Icons.Filled.Warning,
            description = "Read & clear DTCs",
            requiresConnection = true
        ),
        HomeMenuItem(
            id = "terminal",
            label = "Custom",
            icon = Icons.Filled.Terminal,
            description = "Custom OBD commands",
            requiresConnection = true
        ),
        HomeMenuItem(
            id = "logs",
            label = "Logs",
            icon = Icons.Filled.History,
            description = "View recorded sessions"
        ),
        HomeMenuItem(
            id = "diag",
            label = "AI Diagnostics",
            icon = Icons.Filled.Psychology,
            description = "AI-powered diagnosis"
        ),
        HomeMenuItem(
            id = "parts",
            label = "Parts",
            icon = Icons.Filled.ShoppingCart,
            description = "Recommended parts"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with connection status
        HomeHeader(
            connectionState = connectionState,
            onConnectTcp = { viewModel.connectTcp("10.0.2.2", 35000) },
            onConnectBt = { viewModel.connectBluetooth("00:11:22:33:44:55") },
            onDisconnect = { viewModel.disconnect() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Menu grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(menuItems) { item ->
                HomeMenuCard(
                    item = item,
                    isEnabled = !item.requiresConnection || isConnected,
                    onClick = { onNavigate(item.id) }
                )
            }
        }
    }
}

@Composable
fun HomeHeader(
    connectionState: ConnectionState,
    onConnectTcp: () -> Unit,
    onConnectBt: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        ConnectionState.CONNECTED -> Icons.Filled.CheckCircle
                        ConnectionState.CONNECTING -> Icons.Filled.Sync
                        else -> Icons.Filled.LinkOff
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            if (connectionState == ConnectionState.CONNECTING) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (connectionState == ConnectionState.CONNECTED) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onConnectTcp,
                        enabled = connectionState != ConnectionState.CONNECTING
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WiFi/Sim")
                    }
                    Button(
                        onClick = onConnectBt,
                        enabled = connectionState != ConnectionState.CONNECTING
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bluetooth")
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMenuCard(
    item: HomeMenuItem,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = if (isEnabled)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isEnabled)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )

            if (!isEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
