package com.odbplus.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.live.*
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.PidDiscoveryState

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiveScreen(vm: LiveDataViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()

    // Trigger PID cache resolution (or full discovery) once per connect.
    LaunchedEffect(state.isConnected) {
        if (state.isConnected) vm.resolveAndDiscoverPids()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top bar with connection + discovery status
        LiveTopBar(state, vm)

        // Display mode + sort controls
        LiveControlBar(state, vm)

        // Category tab strip (only when connected)
        if (state.isConnected && state.availablePids.isNotEmpty()) {
            CategoryTabRow(state, vm)
        }

        // Main content pane
        Box(Modifier.weight(1f)) {
            when (state.displayMode) {
                LiveDisplayMode.NUMERIC -> NumericListPane(state, vm)
                LiveDisplayMode.GAUGE   -> GaugeGridPane(state)
                LiveDisplayMode.GRAPH   -> GraphPane(state, vm)
                LiveDisplayMode.TILES   -> TilesPane(state)
            }
        }

        // Bottom control bar (poll / log / preset)
        LiveBottomBar(state, vm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveTopBar(state: LiveDataUiState, vm: LiveDataViewModel) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection indicator
                val dotColor by animateColorAsState(
                    if (state.isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    animationSpec = tween(300)
                )
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Source badge
                if (state.isConnected) {
                    SourceBadge(state.supportSource)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.rescanSupportedPids() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Rescan", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Discovery state
            when (state.pidDiscoveryState) {
                PidDiscoveryState.DISCOVERING -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Discovering supported sensors…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                PidDiscoveryState.COMPLETE -> {
                    Text(
                        "${state.availablePids.size} sensors supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                PidDiscoveryState.FAILED -> {
                    Text("Discovery failed — showing all sensors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
                }
                PidDiscoveryState.IDLE -> {}
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val (label, color) = when (source) {
        "cache_hit"       -> "Cached" to Color(0xFF2196F3)
        "validated_cache" -> "Validated" to Color(0xFF4CAF50)
        "discovery"       -> "Fresh scan" to Color(0xFF9C27B0)
        else              -> "Offline" to Color(0xFF9E9E9E)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Control bar (display mode + sort)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveControlBar(state: LiveDataUiState, vm: LiveDataViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Display mode chips
        LiveDisplayMode.values().forEach { mode ->
            val selected = state.displayMode == mode
            FilterChip(
                selected = selected,
                onClick = { vm.setDisplayMode(mode) },
                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Sort dropdown
        var sortExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Sort, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(state.sortOrder.label, style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                SortOrder.values().forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label) },
                        onClick = { vm.setSortOrder(order); sortExpanded = false }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category tab row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryTabRow(state: LiveDataUiState, vm: LiveDataViewModel) {
    // Only show categories that have at least one available PID.
    val activeCategories = remember(state.availablePids) {
        state.availablePids.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = state.activeCategory == null,
                onClick = { vm.setActiveCategory(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
        items(activeCategories) { cat ->
            FilterChip(
                selected = state.activeCategory == cat,
                onClick = { vm.setActiveCategory(if (state.activeCategory == cat) null else cat) },
                label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
    }

    // DTC filter strip
    if (state.activeDtcFilter.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(
                "DTC focus: ${state.activeDtcFilter.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.clearDtcFilter() }, contentPadding = PaddingValues(4.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Numeric list pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NumericListPane(state: LiveDataUiState, vm: LiveDataViewModel) {
    val sorted = remember(state.availablePids, state.sortOrder, state.activeCategory, state.activeDtcFilter) {
        vm.sortedFilteredPids(state)
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Derived metrics section
        if (state.derivedMetrics.isNotEmpty() && state.isConnected) {
            item {
                DerivedMetricsSection(state.derivedMetrics)
            }
        }

        // PID cards
        items(sorted, key = { it.pid.code }) { pidState ->
            PidNumericCard(pidState, vm)
        }

        // Sessions section when not connected
        if (!state.isConnected && state.savedSessions.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    "Saved Sessions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(state.savedSessions, key = { it.id }) { session ->
                SessionCard(session, onReplay = { vm.startReplay(session) }, onDelete = { vm.deleteSession(session) })
            }
        }
    }
}

@Composable
private fun DerivedMetricsSection(metrics: List<DerivedMetric>) {
    Text(
        "Derived Metrics",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(metrics, key = { it.id.name }) { metric ->
            DerivedMetricChip(metric)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DerivedMetricChip(metric: DerivedMetric) {
    val bgColor = when (metric.status) {
        SensorStatus.NORMAL   -> MaterialTheme.colorScheme.primaryContainer
        SensorStatus.WARNING  -> Color(0xFFFFF3E0)
        SensorStatus.CRITICAL -> Color(0xFFFFEBEE)
    }
    val textColor = when (metric.status) {
        SensorStatus.NORMAL   -> MaterialTheme.colorScheme.onPrimaryContainer
        SensorStatus.WARNING  -> Color(0xFFE65100)
        SensorStatus.CRITICAL -> Color(0xFFB71C1C)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            Text(metric.formattedValue, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
            metric.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
private fun PidNumericCard(pidState: PidDisplayState, vm: LiveDataViewModel) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        when (pidState.status) {
            SensorStatus.NORMAL   -> Color.Transparent
            SensorStatus.WARNING  -> Color(0xFFFFA726)
            SensorStatus.CRITICAL -> Color(0xFFEF5350)
        },
        animationSpec = tween(300)
    )
    val valueColor = when (pidState.status) {
        SensorStatus.NORMAL   -> MaterialTheme.colorScheme.primary
        SensorStatus.WARNING  -> Color(0xFFF57C00)
        SensorStatus.CRITICAL -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (pidState.status != SensorStatus.NORMAL) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Favorite star
                IconButton(
                    onClick = { vm.toggleFavorite(pidState.pid) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (pidState.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
                        Modifier.size(16.dp),
                        tint = if (pidState.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                Spacer(Modifier.width(6.dp))

                // PID label + category
                Column(Modifier.weight(1f)) {
                    Text(
                        pidState.definition?.name ?: pidState.pid.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pidState.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Value
                if (pidState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        pidState.formattedValue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Selection toggle
                Checkbox(
                    checked = pidState.isSelected,
                    onCheckedChange = { vm.togglePidSelection(pidState.pid) },
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded detail row
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailItem("PID", "01${pidState.pid.code}")
                    DetailItem("Unit", pidState.pid.unit)
                    pidState.definition?.let { def ->
                        DetailItem("Range", "${def.minValue.toInt()}–${def.maxValue.toInt()}")
                        if (def.dtcTags.isNotEmpty()) {
                            DetailItem("DTCs", def.dtcTags.take(3).joinToString(", "))
                        }
                    }
                }
                pidState.error?.let {
                    Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gauge grid pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GaugeGridPane(state: LiveDataUiState) {
    val gaugePids = remember(state.availablePids) {
        state.availablePids.filter { it.definition?.gaugeEligible == true || it.isSelected }
    }

    if (gaugePids.isEmpty()) {
        EmptyPane("Select PIDs to view gauges.\nGauge-eligible sensors: RPM, Speed, Load, Coolant, Throttle, Voltage")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(gaugePids, key = { it.pid.code }) { pidState ->
            GaugeCard(pidState)
        }
    }
}

@Composable
private fun GaugeCard(pidState: PidDisplayState) {
    val def = pidState.definition
    val fillFraction = if (def != null && pidState.value != null) {
        ((pidState.value - def.minValue) / (def.maxValue - def.minValue)).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    val fillColor = when (pidState.status) {
        SensorStatus.NORMAL   -> Color(0xFF4CAF50)
        SensorStatus.WARNING  -> Color(0xFFFF9800)
        SensorStatus.CRITICAL -> Color(0xFFF44336)
    }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                pidState.definition?.label ?: pidState.pid.code,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            // Arc gauge
            ArcGauge(fillFraction, fillColor, Modifier.size(80.dp))

            Text(
                pidState.formattedValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = fillColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ArcGauge(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.drawWithCache {
        onDrawBehind {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            val startAngle = 135f
            val sweepTotal = 270f
            // Background arc
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = startAngle, sweepAngle = sweepTotal,
                useCenter = false, style = stroke,
                size = size.copy(width = size.width - 8.dp.toPx(), height = size.height - 8.dp.toPx()),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx())
            )
            // Value arc
            drawArc(
                color = color,
                startAngle = startAngle, sweepAngle = sweepTotal * fraction,
                useCenter = false, style = stroke,
                size = size.copy(width = size.width - 8.dp.toPx(), height = size.height - 8.dp.toPx()),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx())
            )
        }
    })
}

// ─────────────────────────────────────────────────────────────────────────────
// Graph pane
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GraphPane(state: LiveDataUiState, vm: LiveDataViewModel) {
    val selectedWithData = remember(state.selectedPids, state.chartData) {
        state.selectedPids.filter { state.chartData[it]?.isNotEmpty() == true }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (selectedWithData.isEmpty()) {
            EmptyPane("Select PIDs and start polling to see live graphs")
            return@Column
        }

        selectedWithData.forEach { pid ->
            val points = state.chartData[pid] ?: return@forEach
            val pidState = state.pidValues[pid]

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            PidRegistry.get(pid)?.name ?: pid.description,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            pidState?.formattedValue ?: "--",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LineGraph(points, Modifier.fillMaxWidth().height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun LineGraph(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Box(modifier = modifier.drawWithCache {
        onDrawBehind {
            if (points.size < 2) return@onDrawBehind
            val minV = points.minOf { it.value }
            val maxV = points.maxOf { it.value }
            val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
            val w = size.width; val h = size.height

            // Draw 3 horizontal grid lines
            repeat(3) { i ->
                val y = h * i / 2f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
            }

            // Draw line
            val path = Path()
            points.forEachIndexed { idx, pt ->
                val x = w * idx / (points.size - 1).toFloat()
                val y = h - h * ((pt.value - minV) / range).toFloat()
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path, lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Fill under line
            val fillPath = Path().apply {
                addPath(path)
                val lastPt = points.last()
                val lastX = w
                val lastY = h - h * ((lastPt.value - minV) / range).toFloat()
                lineTo(lastX, h); lineTo(0f, h); close()
            }
            drawPath(fillPath, lineColor.copy(alpha = 0.08f))
        }
    })
}

// ─────────────────────────────────────────────────────────────────────────────
// Tiles pane (combined sensor tiles)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TilesPane(state: LiveDataUiState) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fuel Trim Tile
        val stftB1 = state.pidValues[ObdPid.SHORT_TERM_FUEL_TRIM_BANK1]
        val ltftB1 = state.pidValues[ObdPid.LONG_TERM_FUEL_TRIM_BANK1]
        if (stftB1 != null || ltftB1 != null) {
            item {
                CombinedTile(
                    "Fuel Trims — Bank 1",
                    listOf(stftB1 to "STFT", ltftB1 to "LTFT")
                )
            }
        }

        // Air Intake Tile
        val maf = state.pidValues[ObdPid.MAF_FLOW_RATE]
        val map = state.pidValues[ObdPid.INTAKE_MANIFOLD_PRESSURE]
        val iat = state.pidValues[ObdPid.INTAKE_AIR_TEMP]
        if (maf != null || map != null || iat != null) {
            item {
                CombinedTile(
                    "Air Intake",
                    listOf(maf to "MAF", map to "MAP", iat to "IAT")
                )
            }
        }

        // Engine Health Tile
        val rpm = state.pidValues[ObdPid.ENGINE_RPM]
        val load = state.pidValues[ObdPid.ENGINE_LOAD]
        val timing = state.pidValues[ObdPid.TIMING_ADVANCE]
        val coolant = state.pidValues[ObdPid.ENGINE_COOLANT_TEMP]
        if (rpm != null || load != null) {
            item {
                CombinedTile(
                    "Engine Health",
                    listOf(rpm to "RPM", load to "Load", timing to "Timing", coolant to "Coolant")
                )
            }
        }

        // O2 Tile
        val o2b1s1 = state.pidValues[ObdPid.O2_SENSOR_B1S1_VOLTAGE]
        val o2b1s2 = state.pidValues[ObdPid.O2_SENSOR_B1S2_VOLTAGE]
        if (o2b1s1 != null || o2b1s2 != null) {
            item {
                CombinedTile(
                    "O2 Sensors",
                    listOf(o2b1s1 to "B1S1", o2b1s2 to "B1S2 (Cat)")
                )
            }
        }

        // Electrical Tile
        val voltage = state.pidValues[ObdPid.CONTROL_MODULE_VOLTAGE]
        if (voltage != null) {
            item {
                CombinedTile("Electrical", listOf(voltage to "Battery"))
            }
        }

        // Derived metrics as tiles
        if (state.derivedMetrics.isNotEmpty()) {
            item {
                Text(
                    "Derived Metrics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(state.derivedMetrics, key = { it.id.name }) { metric ->
                DerivedMetricTile(metric)
            }
        }

        if (state.pidValues.isEmpty()) {
            item { EmptyPane("Start polling to see combined sensor tiles") }
        }
    }
}

@Composable
private fun CombinedTile(title: String, sensors: List<Pair<PidDisplayState?, String>>) {
    val worstStatus = sensors.mapNotNull { it.first }.maxByOrNull { it.status.ordinal }?.status ?: SensorStatus.NORMAL
    val tileBorder = when (worstStatus) {
        SensorStatus.NORMAL   -> Color.Transparent
        SensorStatus.WARNING  -> Color(0xFFFF9800)
        SensorStatus.CRITICAL -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, tileBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                sensors.forEach { (pidState, label) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(
                            pidState?.formattedValue ?: "--",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when (pidState?.status) {
                                SensorStatus.WARNING  -> Color(0xFFF57C00)
                                SensorStatus.CRITICAL -> Color(0xFFC62828)
                                else                  -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DerivedMetricTile(metric: DerivedMetric) {
    val bgColor = when (metric.status) {
        SensorStatus.NORMAL   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        SensorStatus.WARNING  -> Color(0xFFFFF3E0)
        SensorStatus.CRITICAL -> Color(0xFFFFEBEE)
    }
    val valueColor = when (metric.status) {
        SensorStatus.NORMAL   -> MaterialTheme.colorScheme.primary
        SensorStatus.WARNING  -> Color(0xFFE65100)
        SensorStatus.CRITICAL -> Color(0xFFB71C1C)
    }
    Card(colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(metric.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                metric.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
            }
            Text(
                metric.formattedValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar (polling controls + presets + logging)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveBottomBar(state: LiveDataUiState, vm: LiveDataViewModel) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // Poll speed slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Poll", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                Slider(
                    value = state.pollIntervalMs.toFloat(),
                    onValueChange = { vm.setPollInterval(it.toLong()) },
                    valueRange = 100f..2000f,
                    modifier = Modifier.weight(1f).height(24.dp)
                )
                Text("${state.pollIntervalMs}ms", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
            }

            Spacer(Modifier.height(6.dp))

            // Preset chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(PidPreset.values().toList(), key = { it.name }) { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.selectPreset(preset) },
                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Main action buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Poll toggle
                val pollEnabled = state.isConnected && !state.isReplaying && state.selectedPids.isNotEmpty()
                Button(
                    onClick = { vm.togglePolling() },
                    enabled = pollEnabled || state.isPolling,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isPolling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (state.isPolling) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null, Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isPolling) "Stop" else "Poll", style = MaterialTheme.typography.labelMedium)
                }

                // Log toggle
                OutlinedButton(
                    onClick = { vm.toggleLogging() },
                    enabled = state.isPolling || state.isLogging,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (state.isLogging) Icons.Default.FiberManualRecord else Icons.Default.FiberManualRecord,
                        null, Modifier.size(16.dp),
                        tint = if (state.isLogging) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isLogging) "Stop Log" else "Log", style = MaterialTheme.typography.labelMedium)
                }

                // Clear selection
                IconButton(onClick = { vm.clearSelection() }) {
                    Icon(Icons.Default.Clear, "Clear selection", Modifier.size(20.dp))
                }
            }

            // Logging status
            if (state.isLogging) {
                val session = state.currentLogSession
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Recording — ${session?.dataPointCount ?: 0} points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Replay status
            if (state.isReplaying) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Replay ${state.replaySession?.id?.take(8) ?: ""} — frame ${state.replayIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.stopReplay() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Stop Replay", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session card (for saved sessions list in offline mode)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(session: LogSession, onReplay: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Session ${session.id.take(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${session.dataPointCount} points · ${session.duration / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onReplay) {
                Icon(Icons.Default.PlayArrow, "Replay", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyPane(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
