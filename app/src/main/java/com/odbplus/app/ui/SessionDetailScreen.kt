package com.odbplus.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.data.db.entity.SensorLogEntity
import com.odbplus.app.data.db.entity.VehicleSessionEntity
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.vehicle.SessionDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val SESSION_CHART_COLORS = listOf(
    Color(0xFF00D4FF), Color(0xFF00E676), Color(0xFFFF8C00),
    Color(0xFFE040FB), Color(0xFFFF5252), Color(0xFF40C4FF),
    Color(0xFFFFFF00), Color(0xFF69F0AE)
)

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = "Session Detail",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyanPrimary)
                }
            }

            state.logsByPid.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        state.session?.let { SessionInfoCard(it) }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "No sensor data recorded for this session.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.session?.let {
                        item { SessionInfoCard(it) }
                    }

                    // Summary stats per PID
                    item {
                        PidSummarySection(logsByPid = state.logsByPid)
                    }

                    // One chart per PID
                    val pids = state.logsByPid.keys.sorted()
                    items(pids.withIndex().toList(), key = { (_, pid) -> pid }) { (idx, pid) ->
                        val logs = state.logsByPid[pid] ?: return@items
                        PidHistoryCard(
                            pid = pid,
                            logs = logs,
                            color = SESSION_CHART_COLORS[idx % SESSION_CHART_COLORS.size]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionInfoCard(session: VehicleSessionEntity) {
    val duration = session.timestampEnd?.let { it - session.timestampStart } ?: 0L
    val durationStr = if (duration > 0) {
        val min = TimeUnit.MILLISECONDS.toMinutes(duration)
        val sec = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        "${min}m ${sec}s"
    } else "Ongoing"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formatTimestampFull(session.timestampStart),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text("Duration: $durationStr", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun PidSummarySection(logsByPid: Map<String, List<SensorLogEntity>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Summary — ${logsByPid.size} PIDs, ${logsByPid.values.sumOf { it.size }} readings",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            logsByPid.keys.sorted().forEachIndexed { idx, pid ->
                val values = logsByPid[pid]?.map { it.value } ?: return@forEachIndexed
                val color = SESSION_CHART_COLORS[idx % SESSION_CHART_COLORS.size]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pid,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "avg ${"%.1f".format(values.average())}  " +
                               "min ${"%.1f".format(values.min())}  " +
                               "max ${"%.1f".format(values.max())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (idx < logsByPid.size - 1) {
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun PidHistoryCard(pid: String, logs: List<SensorLogEntity>, color: Color) {
    val values = logs.map { it.value }
    val minVal = values.min()
    val maxVal = values.max()
    val avg    = values.average()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pid,
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${logs.size} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            // Canvas chart
            if (logs.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val pad = 6.dp.toPx()
                            val range = (maxVal - minVal).takeIf { it > 0 } ?: 1.0

                            onDrawBehind {
                                val path = Path()
                                logs.forEachIndexed { i, log ->
                                    val x = pad + (i.toFloat() / (logs.size - 1)) * (w - 2 * pad)
                                    val y = h - pad - ((log.value - minVal) / range * (h - 2 * pad)).toFloat()
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                ) {}
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("Min", "%.1f".format(minVal), color)
                StatChip("Avg", "%.1f".format(avg), color)
                StatChip("Max", "%.1f".format(maxVal), color)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun formatTimestampFull(ts: Long): String =
    SimpleDateFormat("EEE MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
