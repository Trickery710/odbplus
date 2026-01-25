package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.connect.ConnectViewModel
import com.odbplus.core.transport.ConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            lazyListState.animateScrollToItem(logLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Copy logs button
                    IconButton(
                        onClick = {
                            val logsText = logLines.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(logsText))
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Logs")
                    }
                    // Clear logs button
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection status banner
            if (connectionState != ConnectionState.CONNECTED) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Not connected - Terminal requires active connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Log display
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(logLines) { line ->
                    val color = when {
                        line.startsWith(">>") -> Color(0xFF4FC3F7) // Light blue for sent
                        line.startsWith("<<") -> Color(0xFF81C784) // Light green for received
                        line.startsWith("!!") -> Color(0xFFE57373) // Light red for errors
                        line.startsWith("--") -> Color(0xFFFFB74D) // Orange for info
                        else -> Color(0xFFB0B0B0) // Gray for other
                    }
                    Text(
                        text = line,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
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
                                text = "No log data yet.\nConnect to a vehicle and send commands.",
                                color = Color(0xFF808080),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Command input bar
            TerminalCommandInput(
                enabled = connectionState == ConnectionState.CONNECTED,
                onSendCommand = { cmd ->
                    scope.launch { viewModel.sendCustomCommand(cmd) }
                }
            )
        }
    }
}

@Composable
fun TerminalCommandInput(
    enabled: Boolean,
    onSendCommand: (String) -> Unit
) {
    var command by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val send = {
        if (command.isNotBlank() && enabled) {
            onSendCommand(command.uppercase())
            command = ""
            keyboardController?.hide()
        }
    }

    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(8.dp)
        ) {
            // Quick command chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickCommandChip("ATZ", enabled) { onSendCommand("ATZ") }
                QuickCommandChip("ATI", enabled) { onSendCommand("ATI") }
                QuickCommandChip("0100", enabled) { onSendCommand("0100") }
                QuickCommandChip("010C", enabled) { onSendCommand("010C") }
            }

            OutlinedTextField(
                value = command,
                onValueChange = { command = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                placeholder = { Text("Enter OBD command (e.g., 010C)") },
                trailingIcon = {
                    IconButton(
                        onClick = { send() },
                        enabled = enabled && command.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

@Composable
fun QuickCommandChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
        }
    )
}
