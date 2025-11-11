package com.odbplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.odbplus.core.transport.TransportRepository
import com.odbplus.core.transport.ConnType

// DI via CompositionLocal
private val LocalTransport = staticCompositionLocalOf<TransportRepository> {
    error("TransportRepository not provided")
}

@Composable
fun ProvideTransport(content: @Composable () -> Unit) {
    val repo = remember { TransportRepository() }
    CompositionLocalProvider(LocalTransport provides repo, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen() {
    val repo = LocalTransport.current
    val state by repo.state.collectAsState()

    var host by remember { mutableStateOf(state.host) }
    var portText by remember { mutableStateOf(state.port.toString()) }
    var typeExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Connection", style = MaterialTheme.typography.titleMedium)

        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = !typeExpanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = when (state.type) {
                    ConnType.TCP -> "TCP"
                    ConnType.BluetoothSPP -> "Bluetooth SPP"
                },
                onValueChange = {},
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                DropdownMenuItem(text = { Text("TCP") }, onClick = { typeExpanded = false; repo.setType(ConnType.TCP) })
                DropdownMenuItem(text = { Text("Bluetooth SPP") }, onClick = { typeExpanded = false; repo.setType(ConnType.BluetoothSPP) })
            }
        }

        if (state.type == ConnType.TCP) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host/IP") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    repo.updateTcpEndpoint(host.ifBlank { "10.0.2.2" }, portText.toIntOrNull() ?: 35000)
                    repo.connect()
                }) { Text("Connect") }
                Button(onClick = { repo.disconnect() }) { Text("Disconnect") }
                AssistChip(
                    onClick = {
                        host = "10.0.2.2"
                        portText = "35000"
                        repo.updateTcpEndpoint(host, 35000)
                        repo.connect()
                    },
                    label = { Text("Quick: OBDSim") }
                )
            }
        } else {
            Text("Bluetooth UI TODO")
        }

        Divider()

        var cmd by remember { mutableStateOf("") }
        Row {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                label = { Text("Command") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (cmd.isNotBlank()) {
                    repo.send(cmd.trim())
                    cmd = ""
                }
            }) { Text("Send") }
        }

        Text("Command Log")
        val lines by LocalTransport.current.log.lines.collectAsState()
        val listState = rememberLazyListState()
        LaunchedEffect(lines.size) {
            if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
        }
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(lines) { Text(it) }
            }
        }
    }
}

@Composable fun LiveScreen() { Text("Live Data (placeholder)") }
@Composable fun DiagnosticsScreen() { Text("Diagnostics (placeholder)") }
@Composable fun LoggerScreen() { Text("Logger (placeholder)") }
@Composable fun EcuProfileScreen() { Text("ECU Profile (placeholder)") }
