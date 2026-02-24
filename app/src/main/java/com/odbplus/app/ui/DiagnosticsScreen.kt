package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.diagnostics.DiagnosticsUiState
import com.odbplus.app.diagnostics.DiagnosticsViewModel
import com.odbplus.app.ui.theme.*
import com.odbplus.core.protocol.DiagnosticTroubleCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CodesScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with buttons
            CodesHeader(
                uiState = uiState,
                onReadCodes = { viewModel.readCodes() },
                onClearCodes = { viewModel.clearCodes() }
            )

            HorizontalDivider(color = DarkBorder, thickness = 1.dp)

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
                        Text("Dismiss", color = Color.White)
                    }
                },
                containerColor = if (uiState.clearSuccess == true)
                    GreenSuccess
                else
                    RedError,
                contentColor = Color.White
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
            .background(DarkSurface)
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
                        .background(if (uiState.isConnected) GreenSuccess else TextTertiary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (uiState.isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isConnected) GreenSuccess else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Last read time
            if (uiState.lastReadTime != null) {
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Last read: ${timeFormat.format(Date(uiState.lastReadTime))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
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
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Read Codes", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onClearCodes,
                enabled = uiState.isConnected && !uiState.isLoading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (uiState.isConnected) RedError.copy(alpha = 0.5f) else DarkBorder
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RedError,
                    disabledContentColor = TextTertiary
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Erase Codes", fontWeight = FontWeight.Medium)
            }
        }

        // Code count summary
        if (uiState.storedCodes.isNotEmpty() || uiState.pendingCodes.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.storedCodes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RedError.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${uiState.storedCodes.size} stored",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = RedError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (uiState.storedCodes.isNotEmpty() && uiState.pendingCodes.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                }
                if (uiState.pendingCodes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberSecondary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${uiState.pendingCodes.size} pending",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Surface(
    shape: RoundedCornerShape,
    color: Color,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = shape,
        color = color,
        content = content
    )
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
        CircularProgressIndicator(color = CyanPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Reading codes...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AmberSecondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = AmberSecondary.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Diagnostic Trouble Codes",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isConnected)
                "Tap 'Read Codes' to scan for error codes"
            else
                "Connect to a vehicle first",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
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
                .background(GreenSuccess.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                fontSize = 28.sp,
                color = GreenSuccess,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No Codes Found",
            style = MaterialTheme.typography.headlineSmall,
            color = GreenSuccess,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No diagnostic trouble codes detected",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Stored codes section
        if (storedCodes.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RedError)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "STORED CODES",
                        style = MaterialTheme.typography.labelMedium,
                        color = RedError,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            items(storedCodes) { code ->
                DtcCodeCard(code = code, isPending = false)
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

        // Pending codes section
        if (pendingCodes.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AmberSecondary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PENDING CODES",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
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
    val accentColor = if (isPending) AmberSecondary else RedError

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) AmberContainer else RedContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Code display -- large monospace for that diagnostic feel
            Text(
                text = code.code,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            // System type badge
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, accentColor.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = code.system.displayName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPending) AmberOnContainer else RedOnContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
