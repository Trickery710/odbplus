package com.odbplus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import kotlinx.coroutines.launch
import kotlin.text.decodeToString
import kotlin.text.trim
import com.odbplus.app.ui.theme.Odbplus_multi_module_scaffoldTheme



@Composable
fun ConnectScreen(
    vm: ConnectViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    var cmd by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connection")

        Row {
            Button(onClick = { vm.connect() }) { Text("Connect") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                // If you implemented repo.disconnect(), you can expose it via vm too.
                // For now we omit to keep it minimal.
                log.add("Disconnect not implemented in VM (optional).")
            }) { Text("Disconnect") }
        }

        Divider()

        Row {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                label = { Text("Command") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = cmd.isNotBlank(),
                onClick = {
                    val toSend = cmd.trim()
                    cmd = ""
                    vm.sendCustom(toSend) { result ->
                        log.add("> $toSend")
                        log.add(result.trim())
                    }
                }
            ) {
                Text("Send")
            }
        }

        Divider()

        Text("Command Log")
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            log.forEach { line -> Text(line) }
        }
    }

    // Optional: auto-connect once on first composition
    LaunchedEffect(Unit) {
        // Comment out if you only want manual connect
        // vm.connect()
    }
}

@Composable fun LiveScreen() { CenteredStub("Live Data (placeholder)") }
@Composable fun DiagnosticsScreen() { CenteredStub("Diagnostics (placeholder)") }
@Composable fun LoggerScreen() { CenteredStub("Logger (placeholder)") }
@Composable fun EcuProfileScreen() { CenteredStub("ECU Profile (placeholder)") }

@Composable
private fun CenteredStub(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title)
    }
}
