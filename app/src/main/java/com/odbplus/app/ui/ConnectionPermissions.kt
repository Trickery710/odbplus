package com.odbplus.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary

/**
 * Returns a lambda that, when invoked, either:
 *  - calls [onGranted] immediately if all Bluetooth permissions are already granted, or
 *  - launches the system permission request dialog.
 *
 * A rationale dialog is shown automatically if the user permanently denies,
 * directing them to the app's system settings page to manually grant.
 *
 * Permissions requested:
 *  - API 31+  →  BLUETOOTH_CONNECT + BLUETOOTH_SCAN
 *  - API < 31 →  ACCESS_FINE_LOCATION  (required for BT scanning on older Android)
 */
@Composable
fun rememberBluetoothGuard(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onGranted()
        } else {
            showSettingsDialog = true
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            title = { Text("Bluetooth Permission Required") },
            text = {
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "ODBPlus needs Nearby Devices (Bluetooth) permission to connect to your OBD-II adapter. " +
                            "Please grant it in the app's system settings."
                    } else {
                        "ODBPlus needs Location permission to scan for Bluetooth devices on this Android version. " +
                            "Please grant it in the app's system settings."
                    },
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent,
                    ),
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    return {
        val allGranted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onGranted()
        } else {
            launcher.launch(requiredPermissions)
        }
    }
}
