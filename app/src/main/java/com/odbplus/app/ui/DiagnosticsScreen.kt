package com.odbplus.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.diagnostics.DiagnosticsUiState
import com.odbplus.app.diagnostics.DiagnosticsViewModel
import com.odbplus.app.expertdiag.model.KnowledgeBaseEntry
import com.odbplus.app.ui.theme.*
import com.odbplus.core.protocol.DiagnosticTroubleCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CodesScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val kbLookup: (String) -> KnowledgeBaseEntry? = { viewModel.kbEntry(it) }

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
                    pendingCodes = uiState.pendingCodes,
                    kbLookup = kbLookup,
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
    pendingCodes: List<DiagnosticTroubleCode>,
    kbLookup: (String) -> KnowledgeBaseEntry?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (storedCodes.isNotEmpty()) {
            item {
                CodeSectionHeader(label = "STORED CODES", color = RedError)
                Spacer(Modifier.height(8.dp))
            }
            items(storedCodes, key = { it.code }) { code ->
                DtcCodeCard(code = code, isPending = false, kbEntry = kbLookup(code.code))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (pendingCodes.isNotEmpty()) {
            item {
                CodeSectionHeader(label = "PENDING CODES", color = AmberSecondary)
                Spacer(Modifier.height(8.dp))
            }
            items(pendingCodes, key = { "p_${it.code}" }) { code ->
                DtcCodeCard(code = code, isPending = true, kbEntry = kbLookup(code.code))
            }
        }
    }
}

@Composable
private fun CodeSectionHeader(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DtcCodeCard(
    code: DiagnosticTroubleCode,
    isPending: Boolean,
    kbEntry: KnowledgeBaseEntry?,
) {
    val accentColor = if (isPending) AmberSecondary else RedError
    val containerColor = if (isPending) AmberContainer else RedContainer
    var expanded by rememberSaveable(code.code) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevron_${code.code}",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = code.code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    // System badge
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, accentColor.copy(alpha = 0.2f)
                        ),
                    ) {
                        Text(
                            text = code.system.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPending) AmberOnContainer else RedOnContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                // Description preview
                val desc = kbEntry?.description ?: code.description
                if (desc != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        // ── Expanded detail ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (kbEntry == null) {
                    Text(
                        "No additional information in knowledge base for ${code.code}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                } else {
                    // Common causes
                    if (kbEntry.commonCauses.isNotEmpty()) {
                        DetailSectionLabel("COMMON CAUSES")
                        Spacer(Modifier.height(5.dp))
                        kbEntry.commonCauses.forEach { cause ->
                            Row(
                                modifier = Modifier.padding(bottom = 3.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text("•", color = accentColor, fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 6.dp, top = 1.dp))
                                Text(
                                    cause,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                )
                            }
                        }
                    }

                    // Automatic tests
                    if (kbEntry.automaticTests.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailSectionLabel("AUTOMATIC TESTS")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "These run automatically when ECU connects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                        Spacer(Modifier.height(5.dp))
                        TestChipRow(chips = kbEntry.automaticTests, icon = Icons.Filled.AutoAwesome, color = CyanPrimary)
                    }

                    // Guided tests
                    if (kbEntry.guidedTests.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailSectionLabel("GUIDED TESTS")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Run from Expert Diagnostics for step-by-step instructions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                        Spacer(Modifier.height(5.dp))
                        TestChipRow(chips = kbEntry.guidedTests, icon = Icons.Filled.DirectionsRun, color = AmberSecondary)
                    }

                    // Monitored PIDs
                    if (kbEntry.monitoredPids.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailSectionLabel("MONITORED PIDS")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            kbEntry.monitoredPids.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun TestChipRow(
    chips: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { label ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.1f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}
