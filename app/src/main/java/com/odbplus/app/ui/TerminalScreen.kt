package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
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
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Terminal",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val logsText = logLines.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(logsText))
                        }
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear Logs",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
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
                    color = RedContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = RedError.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(0.dp)
                        )
                ) {
                    Text(
                        text = "Not connected -- Terminal requires active connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = RedOnContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Terminal log display -- dark hacker aesthetic
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TerminalBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(logLines) { line ->
                    val color = when {
                        line.startsWith(">>") -> TerminalSent      // Cyan for sent
                        line.startsWith("<<") -> TerminalReceived   // Green for received
                        line.startsWith("!!") -> TerminalError      // Red for errors
                        line.startsWith("--") -> TerminalInfo       // Amber for info
                        else -> TerminalMuted                        // Gray for other
                    }
                    // Prefix marker styling
                    val prefix = when {
                        line.startsWith(">>") -> "> "
                        line.startsWith("<<") -> "< "
                        line.startsWith("!!") -> "! "
                        line.startsWith("--") -> "- "
                        else -> "  "
                    }
                    val content = when {
                        line.startsWith(">>") || line.startsWith("<<") ||
                        line.startsWith("!!") || line.startsWith("--") -> line.substring(2).trimStart()
                        else -> line
                    }
                    Text(
                        text = "$prefix$content",
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
                                text = "No log data yet.\nConnect to a vehicle and send commands.",
                                color = TerminalMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
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
        color = DarkSurface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(10.dp)
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
                placeholder = {
                    Text(
                        "Enter OBD command (e.g., 010C)",
                        color = TextTertiary,
                        fontFamily = FontFamily.Monospace
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { send() },
                        enabled = enabled && command.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = if (enabled && command.isNotBlank()) CyanPrimary else TextTertiary
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
                    cursorColor = CyanPrimary,
                    disabledBorderColor = DarkBorder.copy(alpha = 0.5f),
                    disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f)
                )
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) CyanPrimary else TextTertiary
            )
        },
        shape = RoundedCornerShape(8.dp),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = if (enabled) CyanPrimary.copy(alpha = 0.3f) else DarkBorder
        ),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (enabled) CyanContainer else DarkSurfaceVariant,
            labelColor = if (enabled) CyanPrimary else TextTertiary,
            disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
            disabledLabelColor = TextTertiary
        )
    )
}
