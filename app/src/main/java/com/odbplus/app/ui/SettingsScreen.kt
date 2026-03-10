package com.odbplus.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.settings.OwnedTool
import com.odbplus.app.settings.ProfessionalLevel
import com.odbplus.app.settings.SettingsViewModel
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Professional Level ────────────────────────────────────────────
            item {
                SettingsSection(title = "Professional Level", icon = Icons.Filled.Person) {
                    Text(
                        text = "Tailors diagnostic guidance, test suggestions, and visible technical detail to your experience level.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(14.dp))

                    // Level selector — four buttons in a 2×2 grid feel
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfessionalLevel.entries.forEach { level ->
                            val selected = state.professionalLevel == level
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) CyanPrimary.copy(alpha = 0.6f)
                                                else DarkBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) CyanPrimary.copy(alpha = 0.10f)
                                        else DarkSurfaceHigh,
                                onClick = { viewModel.setProfessionalLevel(level) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = level.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) CyanPrimary else TextPrimary
                                        )
                                        Text(
                                            text = level.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (selected) {
                                        Spacer(Modifier.width(12.dp))
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Owned Tools ───────────────────────────────────────────────────
            item {
                SettingsSection(title = "Owned Tools", icon = Icons.Filled.Build) {
                    Text(
                        text = "Mark the tools you have available. The app uses this to recommend relevant tests and procedures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    // Group by category
                    OwnedTool.ToolCategory.entries.forEach { category ->
                        val toolsInCategory = OwnedTool.entries.filter { it.category == category }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = category.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(
                                0.8f,
                                androidx.compose.ui.unit.TextUnitType.Sp
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            toolsInCategory.forEach { tool ->
                                val owned = tool.id in state.ownedToolIds
                                FilterChip(
                                    selected = owned,
                                    onClick = { viewModel.toggleOwnedTool(tool.id) },
                                    label = {
                                        Text(
                                            text = tool.displayName,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = if (owned) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPrimary.copy(alpha = 0.15f),
                                        selectedLabelColor = CyanPrimary,
                                        selectedLeadingIconColor = CyanPrimary,
                                        containerColor = DarkSurfaceHigh,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = owned,
                                        selectedBorderColor = CyanPrimary.copy(alpha = 0.5f),
                                        borderColor = DarkBorder,
                                        selectedBorderWidth = 1.dp,
                                        borderWidth = 1.dp
                                    )
                                )
                            }
                        }
                    }

                    if (state.ownedToolIds.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "${state.ownedToolIds.size} tool${if (state.ownedToolIds.size != 1) "s" else ""} selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenSuccess
                        )
                    }
                }
            }

            // ── Polling ───────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Polling") {
                    SettingsLabel(
                        label = "Default Poll Interval",
                        sublabel = "${state.defaultPollIntervalMs} ms"
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = pollIntervalToSlider(state.defaultPollIntervalMs),
                        onValueChange = { viewModel.setPollInterval(sliderToPollInterval(it)) },
                        steps = 17,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("100 ms", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("2000 ms", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            // ── Connection ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Connection") {
                    SettingsToggleRow(
                        label = "Auto-acquire VIN",
                        sublabel = "Automatically read VIN and calibration data when session starts",
                        checked = state.autoAcquireVin,
                        onCheckedChange = { viewModel.setAutoAcquireVin(it) }
                    )

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

                    val hasBtProfile = !state.lastBtMac.isNullOrBlank()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = if (hasBtProfile) CyanPrimary else TextSecondary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasBtProfile) (state.lastBtName ?: state.lastBtMac!!)
                                       else "No Bluetooth device saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            if (hasBtProfile) {
                                Text(
                                    text = state.lastBtMac!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    val hasWifiProfile = !state.lastWifiHost.isNullOrBlank()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = if (hasWifiProfile) CyanPrimary else TextSecondary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasWifiProfile) "${state.lastWifiHost}:${state.lastWifiPort}"
                                       else "No Wi-Fi address saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    if (hasBtProfile || hasWifiProfile) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.clearConnectionProfile() }) {
                            Text("Clear saved connections", color = TextSecondary)
                        }
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "About") {
                    SettingsLabel(label = "ODBplus", sublabel = "Version 1.0")
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsLabel(label = "Database", sublabel = "Room 2.7.1")
                    SettingsLabel(label = "Protocol", sublabel = "UOAPL (Universal OBD-II Adapter Protocol Layer)")
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 0.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsLabel(label: String, sublabel: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
        Text(sublabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GreenSuccess,
                checkedTrackColor = GreenSuccess.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkBorder
            )
        )
    }
}

private fun pollIntervalToSlider(ms: Long): Float = ((ms - 100L).toFloat() / 1900f)
private fun sliderToPollInterval(v: Float): Long = (100L + (v * 1900).roundToInt()).coerceIn(100, 2000).toLong()
