package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.app.ui.theme.*
import com.odbplus.core.transport.ConnectionState
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showBtPicker by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }

    // Guard the BT picker behind a runtime permission request.
    val connectBluetooth = rememberBluetoothGuard { showBtPicker = true }

    if (showBtPicker) {
        BluetoothDevicePickerDialog(
            onDeviceSelected = { address -> viewModel.connectBluetooth(address) },
            onDismiss = { showBtPicker = false },
        )
    }

    if (showWifiDialog) {
        WifiConnectDialog(
            onConnect = { host, port -> viewModel.connectTcp(host, port) },
            onDismiss = { showWifiDialog = false },
        )
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            lazyListState.animateScrollToItem(logLines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Top Bar content
        ConnectScreenTopBar(
            connectionState = connectionState,
            onConnectTcp = { showWifiDialog = true },
            onConnectBt = connectBluetooth,
            onDisconnect = { viewModel.disconnect() }
        )

        // 2. Log display area
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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

            // Empty state
            if (logLines.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Connection logs will appear here.\nConnect to start.",
                            color = TerminalMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 3. Command Input Bar
        if (connectionState == ConnectionState.CONNECTED) {
            CommandInputBar(onSendCommand = { cmd ->
                scope.launch { viewModel.sendCustomCommand(cmd) }
            })
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
fun CommandInputBar(onSendCommand: (String) -> Unit) {
    var command by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val send = {
        if (command.isNotBlank()) {
            onSendCommand(command)
            command = ""
            keyboardController?.hide()
        }
    }

    Surface(
        shadowElevation = 8.dp,
        color = DarkSurface
    ) {
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(10.dp),
            placeholder = {
                Text(
                    "Enter command (e.g., 010C)",
                    color = TextTertiary,
                    fontFamily = FontFamily.Monospace
                )
            },
            trailingIcon = {
                IconButton(onClick = send) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = if (command.isNotBlank()) CyanPrimary else TextTertiary
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = DarkBorder,
                focusedContainerColor = DarkSurfaceVariant,
                unfocusedContainerColor = DarkSurfaceVariant,
                cursorColor = CyanPrimary
            )
        )
    }
}
