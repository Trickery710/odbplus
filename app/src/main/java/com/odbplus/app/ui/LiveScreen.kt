package com.odbplus.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.live.LiveDataUiState
import com.odbplus.app.live.LiveDataViewModel
import com.odbplus.app.live.LogSession
import com.odbplus.app.live.PidDisplayState
import com.odbplus.app.live.PidPreset
import com.odbplus.core.protocol.ObdPid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(viewModel: LiveDataViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showPidSelector by remember { mutableStateOf(false) }
    var showLogSessions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with connection status and controls
        LiveDataHeader(
            uiState = uiState,
            onTogglePolling = { viewModel.togglePolling() },
            onOpenPidSelector = { showPidSelector = true },
            onSetPollInterval = { viewModel.setPollInterval(it) },
            onToggleLogging = { viewModel.toggleLogging() },
            onOpenLogSessions = { showLogSessions = true },
            onStopReplay = { viewModel.stopReplay() },
            onSetReplaySpeed = { viewModel.setReplaySpeed(it) }
        )

        HorizontalDivider()

        if (uiState.selectedPids.isEmpty()) {
            EmptyStatePrompt(onOpenPidSelector = { showPidSelector = true })
        } else {
            LiveDataGrid(
                uiState = uiState,
                onRefreshPid = { viewModel.querySinglePid(it) }
            )
        }
    }

    // PID Selector Dialog
    if (showPidSelector) {
        PidSelectorDialog(
            uiState = uiState,
            onDismiss = { showPidSelector = false },
            onTogglePid = { viewModel.togglePidSelection(it) },
            onSelectPreset = { viewModel.selectPreset(it) },
            onClearAll = { viewModel.clearSelection() }
        )
    }

    // Log Sessions Dialog
    if (showLogSessions) {
        LogSessionsDialog(
            sessions = uiState.savedSessions,
            onDismiss = { showLogSessions = false },
            onReplay = { session ->
                showLogSessions = false
                viewModel.startReplay(session)
            },
            onDelete = { viewModel.deleteSession(it) },
            onClearAll = { viewModel.clearAllSessions() }
        )
    }
}

@Composable
private fun LiveDataHeader(
    uiState: LiveDataUiState,
    onTogglePolling: () -> Unit,
    onOpenPidSelector: () -> Unit,
    onSetPollInterval: (Long) -> Unit,
    onToggleLogging: () -> Unit,
    onOpenLogSessions: () -> Unit,
    onStopReplay: () -> Unit,
    onSetReplaySpeed: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection/Replay indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                uiState.isReplaying -> Color(0xFF2196F3)
                                uiState.isConnected -> Color(0xFF4CAF50)
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        uiState.isReplaying -> "Replaying"
                        uiState.isConnected -> "Connected"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                // Logging indicator
                if (uiState.isLogging) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = Color.Red,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "REC ${uiState.currentLogSession?.dataPointCount ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }

            // Selected/Session count
            Text(
                text = if (uiState.isReplaying) {
                    "${uiState.replayIndex + 1}/${uiState.replaySession?.dataPointCount ?: 0}"
                } else {
                    "${uiState.selectedPids.size} PIDs"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        // Replay controls (when replaying)
        if (uiState.isReplaying) {
            ReplayControls(
                uiState = uiState,
                onStop = onStopReplay,
                onSetSpeed = onSetReplaySpeed
            )
        } else {
            // Normal controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start/Stop button
                Button(
                    onClick = onTogglePolling,
                    enabled = uiState.isConnected && uiState.selectedPids.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isPolling)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isPolling) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (uiState.isPolling) "Stop" else "Start")
                }

                // Select PIDs button
                OutlinedButton(
                    onClick = onOpenPidSelector,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select PIDs")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Logger controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Record button
                Button(
                    onClick = onToggleLogging,
                    enabled = uiState.isConnected && uiState.selectedPids.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isLogging)
                            Color.Red
                        else
                            MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isLogging) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (uiState.isLogging) "Stop Rec" else "Record")
                }

                // Saved logs button
                OutlinedButton(
                    onClick = onOpenLogSessions,
                    enabled = uiState.savedSessions.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Logs (${uiState.savedSessions.size})")
                }
            }
        }

        // Poll interval slider (when not replaying)
        if (!uiState.isReplaying && uiState.selectedPids.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interval:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = uiState.pollIntervalMs.toFloat(),
                    onValueChange = { onSetPollInterval(it.toLong()) },
                    valueRange = 100f..2000f,
                    steps = 18,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${uiState.pollIntervalMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplayControls(
    uiState: LiveDataUiState,
    onStop: () -> Unit,
    onSetSpeed: (Float) -> Unit
) {
    Column {
        // Progress bar
        val progress = if (uiState.replaySession != null && uiState.replaySession.dataPointCount > 0) {
            (uiState.replayIndex + 1).toFloat() / uiState.replaySession.dataPointCount
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF2196F3)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stop button
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop Replay")
            }

            // Speed controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text("Speed:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                listOf(0.5f, 1f, 2f).forEach { speed ->
                    TextButton(
                        onClick = { onSetSpeed(speed) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (uiState.replaySpeed == speed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("${speed}x")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStatePrompt(onOpenPidSelector: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No PIDs Selected",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select which parameters to monitor from your vehicle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenPidSelector) {
            Text("Select PIDs")
        }
    }
}

@Composable
private fun LiveDataGrid(
    uiState: LiveDataUiState,
    onRefreshPid: (ObdPid) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(uiState.selectedPids) { pid ->
            val pidState = uiState.pidValues[pid] ?: PidDisplayState(pid)
            PidDisplayCard(
                pidState = pidState,
                onRefresh = { onRefreshPid(pid) },
                isReplaying = uiState.isReplaying
            )
        }
    }
}

@Composable
private fun PidDisplayCard(
    pidState: PidDisplayState,
    onRefresh: () -> Unit,
    isReplaying: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            pidState.error != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            pidState.value != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "cardBackground"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = pidState.pid.description,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isReplaying) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (pidState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pidState.error != null) {
                    Text(
                        text = pidState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = pidState.value?.let {
                            formatDisplayValue(it, pidState.pid)
                        } ?: "--",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pidState.pid.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "PID: ${pidState.pid.code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatDisplayValue(value: Double, pid: ObdPid): String {
    return when (pid) {
        ObdPid.ENGINE_RPM -> value.toInt().toString()
        ObdPid.VEHICLE_SPEED -> value.toInt().toString()
        ObdPid.ENGINE_COOLANT_TEMP,
        ObdPid.INTAKE_AIR_TEMP,
        ObdPid.AMBIENT_AIR_TEMP,
        ObdPid.ENGINE_OIL_TEMP -> value.toInt().toString()
        else -> if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PidSelectorDialog(
    uiState: LiveDataUiState,
    onDismiss: () -> Unit,
    onTogglePid: (ObdPid) -> Unit,
    onSelectPreset: (PidPreset) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select PIDs to Monitor") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PidPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.displayName) }
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Individual PIDs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onClearAll) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All")
                        }
                    }
                }

                items(uiState.availablePids) { pidState ->
                    PidSelectorItem(
                        pidState = pidState,
                        onToggle = { onTogglePid(pidState.pid) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PidSelectorItem(
    pidState: PidDisplayState,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (pidState.isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            Color.Transparent,
        label = "selectorBackground"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .background(backgroundColor),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pidState.pid.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "PID: ${pidState.pid.code} | ${pidState.pid.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pidState.isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LogSessionsDialog(
    sessions: List<LogSession>,
    onDismiss: () -> Unit,
    onReplay: (LogSession) -> Unit,
    onDelete: (LogSession) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved Logs") },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    text = "No saved logs yet.\nStart recording to create a log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions.reversed()) { session ->
                        LogSessionItem(
                            session = session,
                            onReplay = { onReplay(session) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (sessions.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun LogSessionItem(
    session: LogSession,
    onReplay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val durationSec = session.duration / 1000

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(session.startTime)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${session.dataPointCount} samples • ${durationSec}s • ${session.selectedPids.size} PIDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onReplay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Replay",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
