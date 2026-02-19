package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.app.ui.theme.*
import com.odbplus.core.transport.ConnectionState

data class OdbMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val description: String,
    val requiresConnection: Boolean = false
)

@Composable
fun OdbHubScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    val menuItems = listOf(
        OdbMenuItem(
            id = "odb_hub/connect",
            label = "Connect",
            icon = Icons.Filled.Link,
            description = "Manage OBD connection",
            requiresConnection = false
        ),
        OdbMenuItem(
            id = "odb_hub/live",
            label = "Live Data",
            icon = Icons.Filled.Speed,
            description = "Real-time vehicle data",
            requiresConnection = true
        ),
        OdbMenuItem(
            id = "odb_hub/codes",
            label = "Codes",
            icon = Icons.Filled.Warning,
            description = "Read & clear DTCs",
            requiresConnection = true
        ),
        OdbMenuItem(
            id = "odb_hub/terminal",
            label = "Terminal",
            icon = Icons.Filled.Terminal,
            description = "Custom OBD commands",
            requiresConnection = true
        ),
        OdbMenuItem(
            id = "odb_hub/logs",
            label = "Logs",
            icon = Icons.Filled.History,
            description = "View recorded sessions",
            requiresConnection = false
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Hero connection status card
        ConnectionStatusHeader(
            connectionState = connectionState,
            onConnectTcp = { viewModel.connectTcp("10.0.2.2", 35000) },
            onConnectBt = { viewModel.connectBluetooth("00:11:22:33:44:55") },
            onDisconnect = { viewModel.disconnect() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Grid of ODB features
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(menuItems) { item ->
                OdbMenuCard(
                    item = item,
                    isEnabled = !item.requiresConnection || isConnected,
                    onClick = { onNavigate(item.id) }
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusHeader(
    connectionState: ConnectionState,
    onConnectTcp: () -> Unit,
    onConnectBt: () -> Unit,
    onDisconnect: () -> Unit
) {
    val borderColor = when (connectionState) {
        ConnectionState.CONNECTED -> GreenSuccess.copy(alpha = 0.3f)
        ConnectionState.CONNECTING -> CyanPrimary.copy(alpha = 0.3f)
        else -> RedError.copy(alpha = 0.2f)
    }
    val bgColor = when (connectionState) {
        ConnectionState.CONNECTED -> GreenContainer
        ConnectionState.CONNECTING -> CyanContainer
        else -> DarkSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Status icon with subtle glow effect via colored circle behind it
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> GreenSuccess.copy(alpha = 0.15f)
                                ConnectionState.CONNECTING -> CyanPrimary.copy(alpha = 0.15f)
                                else -> RedError.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ConnectionState.CONNECTED -> Icons.Filled.CheckCircle
                            ConnectionState.CONNECTING -> Icons.Filled.Sync
                            else -> Icons.Filled.LinkOff
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            ConnectionState.CONNECTED -> GreenSuccess
                            ConnectionState.CONNECTING -> CyanPrimary
                            else -> RedError
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "Connected"
                            ConnectionState.CONNECTING -> "Connecting..."
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> GreenSuccess
                            ConnectionState.CONNECTING -> CyanPrimary
                            else -> RedError
                        }
                    )
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "Vehicle link active"
                            ConnectionState.CONNECTING -> "Establishing link..."
                            else -> "No vehicle connected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (connectionState == ConnectionState.CONNECTING) {
                Spacer(modifier = Modifier.height(14.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CyanPrimary,
                    strokeWidth = 2.5.dp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (connectionState == ConnectionState.CONNECTED) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedError.copy(alpha = 0.15f),
                        contentColor = RedError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onConnectTcp,
                        enabled = connectionState != ConnectionState.CONNECTING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = TextOnAccent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WiFi/Sim", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConnectBt,
                        enabled = connectionState != ConnectionState.CONNECTING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = TextOnAccent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun OdbMenuCard(
    item: OdbMenuItem,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isEnabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.5f)
    val iconColor = if (isEnabled) CyanPrimary else TextTertiary
    val borderColor = if (isEnabled) DarkBorder else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (isEnabled) Modifier.border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon in a subtle circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (isEnabled) TextPrimary else TextTertiary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isEnabled) TextSecondary else TextTertiary
            )

            if (!isEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Connect first",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberSecondary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
