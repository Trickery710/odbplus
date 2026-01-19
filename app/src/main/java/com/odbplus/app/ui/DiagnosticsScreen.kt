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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.diagnostics.DiagnosticsUiState
import com.odbplus.app.diagnostics.DiagnosticsViewModel
import com.odbplus.core.protocol.DiagnosticTroubleCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CodesScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with buttons
            CodesHeader(
                uiState = uiState,
                onReadCodes = { viewModel.readCodes() },
                onClearCodes = { viewModel.clearCodes() }
            )

            HorizontalDivider()

            // Content
            if (uiState.isLoading) {
                LoadingState()
            } else if (uiState.storedCodes.isEmpty() && uiState.pendingCodes.isEmpty()) {
                if (uiState.lastReadTime != null) {
                    NoCodesState()
                } else {
                    InitialState(isConnected = uiState.isConnected)
                }
            } else {
                CodesList(
                    storedCodes = uiState.storedCodes,
                    pendingCodes = uiState.pendingCodes
                )
            }
        }

        // Snackbar for messages
        if (uiState.errorMessage != null || uiState.clearSuccess != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissMessage() }) {
                        Text("Dismiss")
                    }
                },
                containerColor = if (uiState.clearSuccess == true)
                    Color(0xFF4CAF50)
                else
                    MaterialTheme.colorScheme.error
            ) {
                Text(
                    text = uiState.errorMessage
                        ?: if (uiState.clearSuccess == true) "Codes cleared successfully" else "Failed to clear codes"
                )
            }
        }
    }
}

@Composable
private fun CodesHeader(
    uiState: DiagnosticsUiState,
    onReadCodes: () -> Unit,
    onClearCodes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Connection status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isConnected) Color(0xFF4CAF50) else Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (uiState.isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Last read time
            if (uiState.lastReadTime != null) {
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Last read: ${timeFormat.format(Date(uiState.lastReadTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onReadCodes,
                enabled = uiState.isConnected && !uiState.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Read Codes")
            }

            OutlinedButton(
                onClick = onClearCodes,
                enabled = uiState.isConnected && !uiState.isLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Erase Codes")
            }
        }

        // Code count summary
        if (uiState.storedCodes.isNotEmpty() || uiState.pendingCodes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.storedCodes.isNotEmpty()) {
                    Text(
                        text = "${uiState.storedCodes.size} stored",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (uiState.storedCodes.isNotEmpty() && uiState.pendingCodes.isNotEmpty()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.pendingCodes.isNotEmpty()) {
                    Text(
                        text = "${uiState.pendingCodes.size} pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFA000)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Reading codes...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InitialState(isConnected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Diagnostic Trouble Codes",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isConnected)
                "Tap 'Read Codes' to scan for error codes"
            else
                "Connect to a vehicle first",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoCodesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                fontSize = 40.sp,
                color = Color(0xFF4CAF50)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No Codes Found",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No diagnostic trouble codes detected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CodesList(
    storedCodes: List<DiagnosticTroubleCode>,
    pendingCodes: List<DiagnosticTroubleCode>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stored codes section
        if (storedCodes.isNotEmpty()) {
            item {
                Text(
                    text = "STORED CODES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }

            items(storedCodes) { code ->
                DtcCodeCard(code = code, isPending = false)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Pending codes section
        if (pendingCodes.isNotEmpty()) {
            item {
                Text(
                    text = "PENDING CODES",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFA000),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }

            items(pendingCodes) { code ->
                DtcCodeCard(code = code, isPending = true)
            }
        }
    }
}

@Composable
private fun DtcCodeCard(
    code: DiagnosticTroubleCode,
    isPending: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending)
                Color(0xFFFFA000).copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Code display
            Text(
                text = code.code,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isPending) Color(0xFFFFA000) else MaterialTheme.colorScheme.error
            )

            // System type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = code.system.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
