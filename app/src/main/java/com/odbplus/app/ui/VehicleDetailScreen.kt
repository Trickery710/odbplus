package com.odbplus.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.data.db.entity.DtcLogEntity
import com.odbplus.app.data.db.entity.EcuModuleEntity
import com.odbplus.app.data.db.entity.FreezeFrameEntity
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.vehicle.VehicleDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI

@Composable
fun VehicleDetailScreen(
    vin: String,
    onBack: () -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel()
) {
    val vehicle by viewModel.vehicle.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val dtcs by viewModel.dtcs.collectAsState()
    val freezeFrames by viewModel.freezeFrames.collectAsState()
    val ecuModules by viewModel.ecuModules.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = vehicle?.displayName ?: vin,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Health Score
            HealthScoreCard(healthScore)

            // Vehicle Info
            ExpandableSection(title = "Vehicle Info", initiallyExpanded = true) {
                InfoRow("VIN", vin)
                vehicle?.let { v ->
                    v.calibrationId?.let { InfoRow("Calibration ID", it) }
                    v.calibrationVerificationNumber?.let { InfoRow("CVN", it) }
                    v.ecuName?.let { InfoRow("ECU Name", it) }
                    InfoRow("First Seen", formatTimestamp(v.firstSeenTimestamp))
                    InfoRow("Last Seen", formatTimestamp(v.lastSeenTimestamp))
                }
            }

            // ECU Modules
            if (ecuModules.isNotEmpty()) {
                ExpandableSection(title = "ECU Modules (${ecuModules.size})") {
                    ecuModules.forEach { mod ->
                        InfoRow(mod.moduleName, mod.protocol ?: mod.moduleId)
                    }
                }
            }

            // Error Codes
            ExpandableSection(title = "Error Codes (${dtcs.size})", initiallyExpanded = dtcs.isNotEmpty()) {
                if (dtcs.isEmpty()) {
                    Text("No DTCs recorded", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    dtcs.forEach { dtc -> DtcRow(dtc) }
                }
            }

            // Freeze Frames
            if (freezeFrames.isNotEmpty()) {
                ExpandableSection(title = "Freeze Frames (${freezeFrames.size})") {
                    freezeFrames.forEach { frame -> FreezeFrameCard(frame) }
                }
            }

            // Test Results
            ExpandableSection(title = "Test Results (${testResults.size})") {
                if (testResults.isEmpty()) {
                    Text("No tests run yet", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    testResults.forEach { result -> TestResultRow(result) }
                }
            }

            // Sessions
            ExpandableSection(title = "Sessions (${sessions.size})") {
                if (sessions.isEmpty()) {
                    Text("No sessions recorded", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    sessions.forEach { session -> SessionRow(session) }
                }
            }
        }
    }
}

@Composable
private fun HealthScoreCard(score: Int) {
    val arcColor = when {
        score >= 67 -> GreenSuccess
        score >= 34 -> AmberSecondary
        else -> RedError
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Vehicle Health",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        val strokeWidth = 14.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)
                        val startAngle = 150f
                        val sweepFull = 240f

                        // Background arc
                        drawArc(
                            color = DarkBorder,
                            startAngle = startAngle,
                            sweepAngle = sweepFull,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                        // Filled arc
                        drawArc(
                            color = arcColor,
                            startAngle = startAngle,
                            sweepAngle = sweepFull * score / 100f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$score",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = arcColor
                    )
                    Text(
                        text = "/ 100",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DtcRow(dtc: DtcLogEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RedError.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                dtc.dtcCode,
                style = MaterialTheme.typography.labelMedium,
                color = RedError,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            dtc.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
            Text(
                formatTimestamp(dtc.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun FreezeFrameCard(frame: FreezeFrameEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "DTC ${frame.dtcCode} — ${formatTimestamp(frame.timestamp)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            frame.rpm?.let { InfoRow("RPM", "${it.toInt()}") }
            frame.speed?.let { InfoRow("Speed", "${it.toInt()} km/h") }
            frame.coolantTemp?.let { InfoRow("Coolant", "${it.toInt()} °C") }
            frame.throttle?.let { InfoRow("Throttle", "${"%.1f".format(it)} %") }
            frame.engineLoad?.let { InfoRow("Engine Load", "${"%.1f".format(it)} %") }
            frame.fuelTrimShortBank1?.let { InfoRow("Fuel Trim B1", "${"%.1f".format(it)} %") }
        }
    }
}

@Composable
private fun TestResultRow(result: TestResultEntity) {
    val color = when (result.result) {
        "PASS" -> GreenSuccess
        "WARNING" -> AmberSecondary
        else -> RedError
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(result.result, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.testName, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            result.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Text(formatTimestamp(result.timestamp), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SessionRow(session: VehicleSessionEntity) {
    val duration = session.timestampEnd?.let { it - session.timestampStart } ?: 0L
    val durationStr = if (duration > 0) {
        val min = TimeUnit.MILLISECONDS.toMinutes(duration)
        val sec = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        "${min}m ${sec}s"
    } else "Ongoing"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(formatTimestamp(session.timestampStart), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("Duration: $durationStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

private fun formatTimestamp(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(ts))
