package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.settings.SettingsViewModel
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
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
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Polling ─────────────────────────────────────────────────────────
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

            // ── Connection ──────────────────────────────────────────────────────
            SettingsSection(title = "Connection") {
                SettingsToggleRow(
                    label = "Auto-acquire VIN",
                    sublabel = "Automatically read VIN and calibration data when session starts",
                    checked = state.autoAcquireVin,
                    onCheckedChange = { viewModel.setAutoAcquireVin(it) }
                )

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

                // Last BT profile
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
                            text = if (hasBtProfile) (state.lastBtName ?: state.lastBtMac!!) else "No Bluetooth device saved",
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

                // Last WiFi profile
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
                            text = if (hasWifiProfile) "${state.lastWifiHost}:${state.lastWifiPort}" else "No Wi-Fi address saved",
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

            // ── About ───────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                SettingsLabel(label = "ODBplus", sublabel = "Version 1.0")
                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 6.dp))
                SettingsLabel(label = "Database", sublabel = "Room 2.7.1")
                SettingsLabel(label = "Protocol", sublabel = "UOAPL (Universal OBD-II Adapter Protocol Layer)")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = CyanPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

// 18 steps: 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 2000
// Slider value 0.0–1.0 → ms 100–2000
private fun pollIntervalToSlider(ms: Long): Float = ((ms - 100L).toFloat() / 1900f)
private fun sliderToPollInterval(v: Float): Long = (100L + (v * 1900).roundToInt()).coerceIn(100, 2000).toLong()
