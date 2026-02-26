package com.odbplus.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.ai.data.VehicleInfo
import com.odbplus.app.live.LogSession
import com.odbplus.app.live.LogsViewModel
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class VehicleGroup(
    val vin: String?,
    val vehicleInfo: VehicleInfo?,
    val sessions: List<LogSession>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
    onReplaySession: (LogSession) -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsState()
    var showClearConfirmation by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<LogSession?>(null) }
    var sessionToReplay by remember { mutableStateOf<LogSession?>(null) }

    // Group by VIN, sort groups alphabetically (null/unknown last), sessions newest-first within
    val vehicleGroups = remember(sessions) {
        sessions
            .groupBy { it.vehicleInfo?.vin }
            .map { (vin, groupSessions) ->
                VehicleGroup(
                    vin = vin,
                    vehicleInfo = groupSessions.firstNotNullOfOrNull { it.vehicleInfo },
                    sessions = groupSessions.sortedByDescending { it.startTime }
                )
            }
            .sortedWith(compareBy(nullsLast(naturalOrder())) { it.vin })
    }

    // Per-group expanded state — default all expanded
    val expandedGroups = remember(vehicleGroups.map { it.vin }) {
        mutableStateMapOf<String?, Boolean>().also { map ->
            vehicleGroups.forEach { map[it.vin] = true }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Saved Logs", fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmation = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = RedError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyLogsState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vehicleGroups.forEach { group ->
                    item(key = "header_${group.vin ?: "unknown"}") {
                        VehicleGroupHeader(
                            group = group,
                            isExpanded = expandedGroups[group.vin] ?: true,
                            onToggle = {
                                expandedGroups[group.vin] = !(expandedGroups[group.vin] ?: true)
                            }
                        )
                    }
                    if (expandedGroups[group.vin] != false) {
                        items(group.sessions, key = { it.id }) { session ->
                            LogSessionCard(
                                session = session,
                                onReplay = { sessionToReplay = session },
                                onDelete = { sessionToDelete = session },
                                modifier = Modifier.padding(start = 14.dp)
                            )
                        }
                        item(key = "spacer_${group.vin ?: "unknown"}") {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Clear all confirmation
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            title = { Text("Clear All Logs?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete all ${sessions.size} saved log sessions. This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllSessions(); showClearConfirmation = false }) {
                    Text("Delete All", color = RedError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Delete single session
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            title = { Text("Delete Log?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete this log session with ${session.dataPointCount} data points?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(session); sessionToDelete = null }) {
                    Text("Delete", color = RedError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Replay confirmation
    sessionToReplay?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToReplay = null },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            title = { Text("Replay Log?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Replay this log session?", color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will switch to the Live tab and play back the recorded data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onReplaySession(session); sessionToReplay = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Replay", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToReplay = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VehicleGroupHeader(
    group: VehicleGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val displayName = group.vehicleInfo?.displayName ?: "Unknown Vehicle"
    val count = group.sessions.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isExpanded) CyanPrimary.copy(alpha = 0.35f) else DarkBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (group.vin != null) "VIN  ${group.vin}" else "VIN not recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            // Session count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanPrimary.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LogSessionCard(
    session: LogSession,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val durationSec = session.duration / 1000
    val durationMin = durationSec / 60
    val durationRemainingSec = durationSec % 60
    var showVehicleInfo by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clickable { onReplay() },
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Timestamp + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = timeFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onReplay, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Replay",
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = RedError.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    label = "Duration",
                    value = if (durationMin > 0) "${durationMin}m ${durationRemainingSec}s" else "${durationSec}s"
                )
                StatItem(label = "Samples", value = session.dataPointCount.toString())
                StatItem(label = "PIDs", value = session.selectedPids.size.toString())
            }

            // PIDs preview
            if (session.selectedPids.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = session.selectedPids.take(5).joinToString(", ") { it.description } +
                            if (session.selectedPids.size > 5) " +${session.selectedPids.size - 5} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 2
                )
            }

            // Vehicle info sub-dropdown (only if info is available)
            if (session.vehicleInfo != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVehicleInfo = !showVehicleInfo }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vehicle Info",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanPrimary
                    )
                    Icon(
                        imageVector = if (showVehicleInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                AnimatedVisibility(
                    visible = showVehicleInfo,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    VehicleInfoSection(session.vehicleInfo)
                }
            }
        }
    }
}

@Composable
private fun VehicleInfoSection(info: VehicleInfo) {
    val decoded = remember(info.vin) { info.decodeVin() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        VehicleInfoRow("VIN", info.vin)
        decoded["Manufacturer"]?.let { VehicleInfoRow("Manufacturer", it) }
        decoded["Model Year"]?.let { VehicleInfoRow("Model Year", it) }
        decoded["Plant Code"]?.let { VehicleInfoRow("Plant Code", it) }
        decoded["Serial Number"]?.let { VehicleInfoRow("Serial", it) }
        info.calibrationId?.let { VehicleInfoRow("Cal. ID", it) }
        info.calibrationVerificationNumber?.let { VehicleInfoRow("CVN", it) }
        info.ecuName?.let { VehicleInfoRow("ECU", it) }
    }
}

@Composable
private fun VehicleInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}

@Composable
private fun EmptyLogsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = CyanPrimary.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "No Saved Logs",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Record live data sessions from the\nLive tab to save them here",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}
