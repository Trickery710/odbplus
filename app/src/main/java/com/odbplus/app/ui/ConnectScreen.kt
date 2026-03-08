package com.odbplus.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.app.ui.theme.*
import com.odbplus.core.protocol.PidDiscoveryState
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import com.odbplus.core.transport.ConnectionState

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val currentVehicle by viewModel.currentVehicle.collectAsState()
    val isAcquiring by viewModel.isAcquiring.collectAsState()
    val acquireStatus by viewModel.acquireStatus.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val lastBtMac  by viewModel.lastBtMac.collectAsState()
    val lastBtName by viewModel.lastBtName.collectAsState()
    val lastWifiHost by viewModel.lastWifiHost.collectAsState()
    val lastWifiPort by viewModel.lastWifiPort.collectAsState()

    var showBtPicker by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    val connectBluetooth = rememberBluetoothGuard { showBtPicker = true }

    if (showBtPicker) {
        BluetoothDevicePickerDialog(
            onDeviceSelected = { address -> viewModel.connectBluetooth(address) },
            onDismiss = { showBtPicker = false },
            lastMac = lastBtMac,
            lastName = lastBtName,
        )
    }

    if (showWifiDialog) {
        WifiConnectDialog(
            onConnect = { host, port -> viewModel.connectTcp(host, port) },
            onDismiss = { showWifiDialog = false },
            initialHost = lastWifiHost ?: "",
            initialPort = lastWifiPort,
        )
    }

    if (showLogsDialog) {
        ConnectionLogsDialog(
            logLines = logLines,
            onDismiss = { showLogsDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        ConnectScreenTopBar(
            connectionState = connectionState,
            onConnectTcp = { showWifiDialog = true },
            onConnectBt = connectBluetooth,
            onDisconnect = { viewModel.disconnect() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VehicleInfoCard(
                sessionState = sessionState,
                discoveryState = discoveryState,
                currentVehicle = currentVehicle,
                isAcquiring = isAcquiring,
                acquireStatus = acquireStatus,
                onAcquire = { viewModel.acquireVehicleInfo() }
            )

            OutlinedButton(
                onClick = { showLogsDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Connection Logs", fontSize = 14.sp)
            }

            ManualVinCard(onSave = { vin -> viewModel.saveManualVin(vin) })
        }
    }
}

@Composable
fun ConnectScreenTopBar(
    connectionState: ConnectionState,
    onConnectTcp: () -> Unit,
    onConnectBt: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionState) {
                            ConnectionState.CONNECTED -> GreenSuccess
                            ConnectionState.CONNECTING -> CyanPrimary
                            else -> RedError
                        }
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Status: ${connectionState.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> GreenSuccess
                    ConnectionState.CONNECTING -> CyanPrimary
                    else -> RedError
                }
            )
        }

        if (connectionState == ConnectionState.CONNECTING) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = CyanPrimary,
                strokeWidth = 2.5.dp
            )
        }

        Spacer(Modifier.height(18.dp))

        if (connectionState == ConnectionState.CONNECTED) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedError.copy(alpha = 0.15f),
                    contentColor = RedError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onConnectTcp,
                    enabled = connectionState != ConnectionState.CONNECTING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wi-Fi", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onConnectBt,
                    enabled = connectionState != ConnectionState.CONNECTING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bluetooth", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp),
            color = DarkBorder
        )
    }
}

@Composable
private fun VehicleInfoCard(
    sessionState: ProtocolSessionState,
    discoveryState: PidDiscoveryState,
    currentVehicle: VehicleInfo?,
    isAcquiring: Boolean,
    acquireStatus: String?,
    onAcquire: () -> Unit
) {
    val canAcquire = sessionState.canSendCommands && !isAcquiring

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Vehicle Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onAcquire,
                enabled = canAcquire,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary
                )
            ) {
                if (isAcquiring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = TextOnAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Acquiring...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Acquire Vehicle Info", fontWeight = FontWeight.SemiBold)
                }
            }

            // Hint text when session not ready
            when {
                sessionState == ProtocolSessionState.DISCONNECTED -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to a vehicle to acquire info",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
                !sessionState.canSendCommands -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Session initializing — please wait",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            }

            // PID discovery status
            when (discoveryState) {
                PidDiscoveryState.DISCOVERING -> {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = CyanPrimary,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Scanning supported PIDs…",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                PidDiscoveryState.COMPLETE -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "PID scan complete — ready for live data",
                        fontSize = 12.sp,
                        color = GreenSuccess
                    )
                }
                PidDiscoveryState.FAILED -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "PID scan failed — live data may be limited",
                        fontSize = 12.sp,
                        color = RedLight
                    )
                }
                else -> Unit
            }

            // Status message
            acquireStatus?.let { status ->
                Spacer(Modifier.height(8.dp))
                val isError = status.startsWith("Error") || status.startsWith("VIN not")
                Text(
                    status,
                    fontSize = 12.sp,
                    color = if (isError) RedLight else GreenSuccess
                )
            }

            // Fetched vehicle info display
            currentVehicle?.takeIf { it.vin.isNotBlank() }?.let { info ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(Modifier.height(12.dp))
                VehicleInfoRows(info)
            }
        }
    }
}

@Composable
private fun VehicleInfoRows(info: VehicleInfo) {
    val decoded = info.decodeVin()
    VehicleInfoRow("VIN", info.vin)
    decoded["Manufacturer"]?.let { VehicleInfoRow("Manufacturer", it) }
    decoded["Model Year"]?.let { VehicleInfoRow("Model Year", it) }
    info.ecuName?.let { VehicleInfoRow("ECU Name", it) }
    info.calibrationId?.let { VehicleInfoRow("Calibration ID", it) }
    info.calibrationVerificationNumber?.let { VehicleInfoRow("CVN", it) }
}

@Composable
private fun VehicleInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun ManualVinCard(onSave: (String) -> Unit) {
    var vinInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = AmberSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Manual VIN Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = vinInput,
                onValueChange = { vinInput = it.uppercase().take(17) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Enter VIN (17 characters)",
                        color = TextTertiary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberSecondary,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = AmberSecondary
                ),
                supportingText = {
                    Text(
                        "${vinInput.length}/17",
                        color = if (vinInput.length == 17) GreenSuccess else TextTertiary,
                        fontSize = 11.sp
                    )
                }
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    if (vinInput.isNotBlank()) {
                        onSave(vinInput)
                        vinInput = ""
                    }
                },
                enabled = vinInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberSecondary,
                    contentColor = TextOnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary
                )
            ) {
                Text("Save VIN", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConnectionLogsDialog(logLines: List<String>, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connection Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
            HorizontalDivider(color = DarkBorder)

            if (logLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TerminalBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No connection logs yet.",
                        color = TerminalMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TerminalBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    items(logLines) { line ->
                        val color = when {
                            line.startsWith(">>") -> TerminalSent
                            line.startsWith("<<") -> TerminalReceived
                            line.startsWith("!!") -> TerminalError
                            line.startsWith("--") -> TerminalInfo
                            else -> TerminalMuted
                        }
                        Text(
                            text = line,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
