package com.odbplus.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

// ── Bluetooth device picker ───────────────────────────────────────────────────

private data class BtDevice(val name: String, val address: String)

/**
 * Dialog that lists all **paired** Bluetooth devices from the system.
 * Permission guard (BLUETOOTH_CONNECT on API 31+) must have run before this is shown.
 */
@SuppressLint("MissingPermission") // permission checked by rememberBluetoothGuard before showing
@Composable
fun BluetoothDevicePickerDialog(
    onDeviceSelected: (address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val pairedDevices: List<BtDevice> = remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.bondedDevices
            ?.map { BtDevice(name = it.name ?: it.address, address = it.address) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        title = { Text("Paired Bluetooth Devices", fontWeight = FontWeight.Bold) },
        text = {
            if (pairedDevices.isEmpty()) {
                Text(
                    text = "No paired devices found.\n\nOpen Android Settings → Bluetooth and pair your OBD-II adapter first, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pairedDevices) { device ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device.address)
                                    onDismiss()
                                },
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextTertiary,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

// ── Wi-Fi / TCP connect dialog ────────────────────────────────────────────────

/**
 * Dialog for entering a Wi-Fi OBD adapter's IP address and port.
 */
@Composable
fun WifiConnectDialog(
    onConnect: (host: String, port: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("35000") }

    val portInt = port.toIntOrNull()
    val portValid = portInt != null && portInt in 1..65535
    val canConnect = host.isNotBlank() && portValid

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyanPrimary,
        unfocusedBorderColor = DarkBorder,
        focusedLabelColor = CyanPrimary,
        unfocusedLabelColor = TextTertiary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = CyanPrimary,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        title = { Text("Wi-Fi Connect", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the IP address and port of your Wi-Fi OBD-II adapter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                HorizontalDivider(color = DarkBorder)

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.0.10", color = TextTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Port") },
                    placeholder = { Text("35000", color = TextTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(10.dp),
                    isError = port.isNotBlank() && !portValid,
                    supportingText = if (port.isNotBlank() && !portValid) {
                        { Text("Must be 1–65535", color = MaterialTheme.colorScheme.error) }
                    } else null,
                )

                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (portInt != null) {
                        onConnect(host.trim(), portInt)
                        onDismiss()
                    }
                },
                enabled = canConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
