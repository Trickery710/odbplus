package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.core.transport.ConnectionState
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            lazyListState.animateScrollToItem(logLines.size - 1)
        }
    }

    // --- DEFINITIVE FIX: Removed the nested Scaffold, using a Column instead ---
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Top Bar content remains at the top
        ConnectScreenTopBar(
            connectionState = connectionState,
            onConnectTcp = { viewModel.connectTcp("10.0.2.2", 35000) },
            onConnectBt = { viewModel.connectBluetooth("00:11:22:33:44:55") },
            onDisconnect = { viewModel.disconnect() }
        )

        // 2. The LazyColumn (log) takes up all the remaining space
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // This is the key change
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp)
        ) {
            items(logLines) { line ->
                val color = when {
                    line.startsWith(">>") -> MaterialTheme.colorScheme.primary
                    line.startsWith("<<") -> Color(0xFF006400) // Dark Green
                    line.startsWith("!!") -> MaterialTheme.colorScheme.error // Error color
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
                Text(
                    text = line,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        // 3. The Command Input Bar sits at the bottom of the column
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Status: ${connectionState.name}",
            style = MaterialTheme.typography.headlineSmall,
            color = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
                ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
        )

        if (connectionState == ConnectionState.CONNECTING) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(16.dp))

        if (connectionState == ConnectionState.CONNECTED) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Disconnect") }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onConnectTcp, enabled = connectionState != ConnectionState.CONNECTING) {
                    Text("Quick: OBDSim")
                }
                Button(onClick = onConnectBt, enabled = connectionState != ConnectionState.CONNECTING) {
                    Text("Bluetooth")
                }
            }
        }
        Divider(modifier = Modifier.padding(top = 16.dp))
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

    // Use Surface with a higher elevation and wrap the text field
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .fillMaxWidth()
                // Use imePadding() to add space for the keyboard
                .imePadding()
                .padding(8.dp),
            placeholder = { Text("Enter command (e.g., 010C)") },
            trailingIcon = {
                IconButton(onClick = send) {
                    Icon(Icons.Default.Send, contentDescription = "Send Command")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            singleLine = true
        )
    }
}
