package com.odbplus.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.live.LiveDataUiState
import com.odbplus.app.live.LiveDataViewModel
import com.odbplus.app.live.LogSession
import com.odbplus.app.live.PidDisplayState
import com.odbplus.app.live.PidPreset
import com.odbplus.app.ui.theme.*
import com.odbplus.core.protocol.ObdPid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(viewModel: LiveDataViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showPidSelector by remember { mutableStateOf(false) }
    var showLogSessions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
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

        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

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
            .background(DarkSurface)
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
                                uiState.isReplaying -> ReplayBlue
                                uiState.isConnected -> GreenSuccess
                                else -> TextTertiary
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        uiState.isReplaying -> ReplayBlue
                        uiState.isConnected -> GreenSuccess
                        else -> TextSecondary
                    },
                    fontWeight = FontWeight.Medium
                )

                // Recording indicator with pulse feel
                if (uiState.isLogging) {
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RecordingPulse.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, RecordingPulse.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Recording",
                                tint = RecordingPulse,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "REC ${uiState.currentLogSession?.dataPointCount ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RecordingPulse,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Selected/Session count
            Text(
                text = if (uiState.isReplaying) {
                    "${uiState.replayIndex + 1}/${uiState.replaySession?.dataPointCount ?: 0}"
                } else {
                    "${uiState.selectedPids.size} PIDs"
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(14.dp))

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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePolling,
                    enabled = uiState.isConnected && uiState.selectedPids.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isPolling) RedError else CyanPrimary,
                        contentColor = if (uiState.isPolling) Color.White else TextOnAccent,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextTertiary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isPolling) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (uiState.isPolling) "Stop" else "Start",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onOpenPidSelector,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("Select PIDs", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Logger controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleLogging,
                    enabled = uiState.isConnected && uiState.selectedPids.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isLogging) RecordingPulse else DarkSurfaceHigh,
                        contentColor = if (uiState.isLogging) Color.White else TextPrimary,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextTertiary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isLogging) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (uiState.isLogging) Color.White else RecordingPulse
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (uiState.isLogging) "Stop Rec" else "Record",
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onOpenLogSessions,
                    enabled = uiState.savedSessions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text(
                        "Logs (${uiState.savedSessions.size})",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Poll interval slider (when not replaying)
        if (!uiState.isReplaying && uiState.selectedPids.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interval:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = uiState.pollIntervalMs.toFloat(),
                    onValueChange = { onSetPollInterval(it.toLong()) },
                    valueRange = 100f..2000f,
                    steps = 18,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = DarkBorder
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${uiState.pollIntervalMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(52.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = ReplayBlue,
            trackColor = DarkBorder
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedError,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop Replay", fontWeight = FontWeight.SemiBold)
            }

            // Speed controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Speed:", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Spacer(Modifier.width(6.dp))
                listOf(0.5f, 1f, 2f).forEach { speed ->
                    TextButton(
                        onClick = { onSetSpeed(speed) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (uiState.replaySpeed == speed)
                                CyanPrimary
                            else
                                TextTertiary
                        )
                    ) {
                        Text(
                            "${speed}x",
                            fontWeight = if (uiState.replaySpeed == speed) FontWeight.Bold else FontWeight.Normal
                        )
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
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select which parameters to monitor from your vehicle",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onOpenPidSelector,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = TextOnAccent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Select PIDs", fontWeight = FontWeight.SemiBold)
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
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
    // Color-code based on state: error = red, active value = cyan glow, empty = neutral
    val borderColor by animateColorAsState(
        targetValue = when {
            pidState.error != null -> RedError.copy(alpha = 0.3f)
            pidState.value != null -> CyanPrimary.copy(alpha = 0.25f)
            else -> DarkBorder
        },
        label = "cardBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            pidState.error != null -> RedContainer
            pidState.value != null -> DarkSurfaceVariant
            else -> DarkSurfaceVariant
        },
        label = "cardBackground"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp)
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
                    modifier = Modifier.weight(1f),
                    color = TextSecondary
                )
                if (!isReplaying) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (pidState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = CyanPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp),
                                tint = TextTertiary
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
                        color = RedLight,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Large bold value -- the dashboard-style readout
                    Text(
                        text = pidState.value?.let {
                            formatDisplayValue(it, pidState.pid)
                        } ?: "--",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (pidState.value != null) CyanPrimary else TextTertiary,
                        fontFamily = FontFamily.Default
                    )
                    // Unit label in smaller muted text
                    Text(
                        text = pidState.pid.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            // PID code in bottom corner
            Text(
                text = "PID: ${pidState.pid.code}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
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

/**
 * Categories for organizing PIDs in the selector.
 */
enum class PidCategory(val displayName: String) {
    ENGINE("Engine"),
    FUEL("Fuel System"),
    TEMPERATURE("Temperature"),
    SPEED_DISTANCE("Speed & Distance"),
    OXYGEN_SENSORS("Oxygen Sensors"),
    EMISSIONS("Emissions & EGR"),
    THROTTLE_PEDAL("Throttle & Pedal"),
    TURBO_BOOST("Turbo & Boost"),
    DIAGNOSTICS("Diagnostics"),
    OTHER("Other")
}

/**
 * Get the category for a PID based on its code and description.
 */
private fun ObdPid.getCategory(): PidCategory {
    return when {
        code in listOf("04", "0C", "0E", "1F", "61", "62", "63", "64", "8E") -> PidCategory.ENGINE
        description.contains("Engine", ignoreCase = true) && !description.contains("Temp", ignoreCase = true) -> PidCategory.ENGINE
        code in listOf("03", "06", "07", "08", "09", "0A", "22", "23", "2F", "51", "52", "59", "5D", "5E", "9D") -> PidCategory.FUEL
        description.contains("Fuel", ignoreCase = true) -> PidCategory.FUEL
        code in listOf("05", "0F", "46", "5C", "67", "68", "6B", "75", "76", "77", "78", "79", "84") -> PidCategory.TEMPERATURE
        description.contains("Temp", ignoreCase = true) -> PidCategory.TEMPERATURE
        code in listOf("0D", "21", "30", "31", "4D", "4E", "7F") -> PidCategory.SPEED_DISTANCE
        description.contains("Speed", ignoreCase = true) || description.contains("Distance", ignoreCase = true) -> PidCategory.SPEED_DISTANCE
        code in listOf("13", "14", "15", "16", "17", "18", "19", "1A", "1B", "1D",
            "24", "25", "26", "27", "28", "29", "2A", "2B",
            "34", "35", "36", "37", "38", "39", "3A", "3B",
            "55", "56", "57", "58", "8C", "9C") -> PidCategory.OXYGEN_SENSORS
        description.contains("O2", ignoreCase = true) || description.contains("Oxygen", ignoreCase = true) -> PidCategory.OXYGEN_SENSORS
        code in listOf("12", "2C", "2D", "2E", "32", "3C", "3D", "3E", "3F", "53", "54",
            "69", "6A", "7A", "7B", "7C", "7D", "7E", "83", "85", "86", "88", "8B", "94") -> PidCategory.EMISSIONS
        description.contains("EGR", ignoreCase = true) || description.contains("Catalyst", ignoreCase = true) ||
        description.contains("Evap", ignoreCase = true) || description.contains("NOx", ignoreCase = true) ||
        description.contains("DPF", ignoreCase = true) || description.contains("Particulate", ignoreCase = true) -> PidCategory.EMISSIONS
        code in listOf("11", "45", "47", "48", "49", "4A", "4B", "4C", "5A", "6C", "8D") -> PidCategory.THROTTLE_PEDAL
        description.contains("Throttle", ignoreCase = true) || description.contains("Pedal", ignoreCase = true) -> PidCategory.THROTTLE_PEDAL
        code in listOf("6F", "70", "71", "72", "73", "74") -> PidCategory.TURBO_BOOST
        description.contains("Turbo", ignoreCase = true) || description.contains("Boost", ignoreCase = true) ||
        description.contains("Wastegate", ignoreCase = true) -> PidCategory.TURBO_BOOST
        code in listOf("00", "01", "02", "1C", "1E", "20", "40", "41", "60", "65", "80", "A0", "C0") -> PidCategory.DIAGNOSTICS
        description.contains("Monitor", ignoreCase = true) || description.contains("DTC", ignoreCase = true) ||
        description.contains("Supported", ignoreCase = true) || description.contains("Status", ignoreCase = true) -> PidCategory.DIAGNOSTICS
        else -> PidCategory.OTHER
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
    val groupedPids = remember(uiState.availablePids) {
        uiState.availablePids
            .groupBy { it.pid.getCategory() }
            .toSortedMap(compareBy { it.ordinal })
    }

    var expandedCategory by remember { mutableStateOf<PidCategory?>(PidCategory.ENGINE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        title = {
            Column {
                Text(
                    "Select PIDs to Monitor",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.selectedPids.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanPrimary
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "QUICK PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PidPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Text(
                                        preset.displayName,
                                        color = CyanPrimary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CyanContainer,
                                    labelColor = CyanPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = CyanPrimary.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = DarkBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BY CATEGORY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary,
                            letterSpacing = 1.sp
                        )
                        TextButton(onClick = onClearAll) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = RedError
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All", color = RedError)
                        }
                    }
                }

                // Render each category
                groupedPids.forEach { (category, pidsInCategory) ->
                    val selectedCount = pidsInCategory.count { it.isSelected }
                    val isExpanded = expandedCategory == category

                    item {
                        PidCategoryHeader(
                            category = category,
                            totalCount = pidsInCategory.size,
                            selectedCount = selectedCount,
                            isExpanded = isExpanded,
                            onClick = {
                                expandedCategory = if (isExpanded) null else category
                            }
                        )
                    }

                    if (isExpanded) {
                        items(pidsInCategory) { pidState ->
                            PidSelectorItem(
                                pidState = pidState,
                                onToggle = { onTogglePid(pidState.pid) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun PidCategoryHeader(
    category: PidCategory,
    totalCount: Int,
    selectedCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (selectedCount > 0)
            CyanContainer
        else
            DarkSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowDown
                    else
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = if (selectedCount > 0) CyanPrimary else TextSecondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedCount > 0) CyanOnContainer else TextPrimary
                )
            }
            Text(
                text = if (selectedCount > 0) "$selectedCount / $totalCount" else "$totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedCount > 0) CyanPrimary else TextTertiary,
                fontWeight = if (selectedCount > 0) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun PidSelectorItem(
    pidState: PidDisplayState,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (pidState.isSelected)
            CyanPrimary.copy(alpha = 0.1f)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "PID: ${pidState.pid.code} | ${pidState.pid.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            if (pidState.isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = CyanPrimary
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
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        title = { Text("Saved Logs", fontWeight = FontWeight.Bold) },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    text = "No saved logs yet.\nStart recording to create a log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (sessions.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = RedError)
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
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
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "${session.dataPointCount} samples | ${durationSec}s | ${session.selectedPids.size} PIDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Row {
                IconButton(onClick = onReplay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Replay",
                        tint = CyanPrimary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = RedError.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
