package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.guidedtest.COMMON_SYMPTOMS
import com.odbplus.app.guidedtest.GuidedTestUiState
import com.odbplus.app.guidedtest.GuidedTestViewModel
import com.odbplus.app.guidedtest.TestStage
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanContainer
import com.odbplus.app.ui.theme.CyanOnContainer
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenContainer
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@Composable
fun GuidedRpmTestScreen(
    onBack: () -> Unit,
    viewModel: GuidedTestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = uiState) {
            is GuidedTestUiState.Idle -> IdleContent(
                isConnected = state.isConnected,
                onStart = { viewModel.startTest() },
                onBack = onBack,
            )
            is GuidedTestUiState.Running -> RunningContent(
                state = state,
                onSkipValidation = { viewModel.skipValidation() },
                onCancel = { viewModel.cancelTest() },
            )
            is GuidedTestUiState.ReadingDtcs -> LoadingContent(message = "Collecting trouble codes…")
            is GuidedTestUiState.SymptomsInput -> SymptomsContent(
                state = state,
                onToggleSymptom = { viewModel.toggleSymptom(it) },
                onFreeTextChange = { viewModel.updateFreeText(it) },
                onSubmit = { viewModel.submitSymptoms() },
                onBack = { viewModel.cancelTest() },
            )
            is GuidedTestUiState.Sending -> LoadingContent(message = state.message)
            is GuidedTestUiState.Done -> DoneContent(
                state = state,
                onRunAgain = { viewModel.reset() },
                onDone = onBack,
            )
            is GuidedTestUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = { viewModel.startTest() },
                onBack = onBack,
            )
            is GuidedTestUiState.Cancelled -> CancelledContent(
                onRetry = { viewModel.reset() },
                onBack = onBack,
            )
        }
    }
}

// ── Idle state ────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    isConnected: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Back", tint = TextSecondary)
            }
        }

        Spacer(Modifier.weight(1f))

        // Icon badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "⚙", fontSize = 36.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Guided RPM Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Idle → 1000 RPM → 2000 RPM (5 s each) + DTCs",
            style = MaterialTheme.typography.bodyMedium,
            color = CyanPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Stage summary cards
        TestStage.entries.forEachIndexed { index, stage ->
            StageSummaryRow(index = index + 1, stage = stage)
            if (index < TestStage.entries.size - 1) Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AmberSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        ) {
            Text(
                text = "⚠ Keep the vehicle stationary and engine running before starting.",
                style = MaterialTheme.typography.bodySmall,
                color = AmberSecondary,
                modifier = Modifier.padding(12.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        if (!isConnected) {
            Text(
                text = "Connect to an OBD adapter first",
                style = MaterialTheme.typography.bodySmall,
                color = RedError,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Button(
            onClick = onStart,
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = TextOnAccent,
                disabledContainerColor = DarkSurfaceHigh,
                disabledContentColor = TextTertiary,
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Start Test", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StageSummaryRow(index: Int, stage: TestStage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = CyanPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stage.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = "${stage.durationSec}s • ${stage.targetMin}–${stage.targetMax} RPM",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

// ── Running state ─────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(
    state: GuidedTestUiState.Running,
    onSkipValidation: () -> Unit,
    onCancel: () -> Unit,
) {
    val stages = TestStage.entries
    val currentIndex = stages.indexOf(state.stage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Step progress dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stages.forEachIndexed { index, stage ->
                val isDone = index < currentIndex
                val isCurrent = index == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> GreenSuccess
                                isCurrent -> CyanPrimary
                                else -> TextTertiary.copy(alpha = 0.4f)
                            }
                        ),
                )
                if (index < stages.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.width(24.dp),
                        color = if (isDone) GreenSuccess.copy(alpha = 0.4f) else DarkBorder,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stage name
        Text(
            text = "Stage: ${state.stage.label}",
            style = MaterialTheme.typography.titleMedium,
            color = CyanPrimary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = state.stage.instruction,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // Live RPM display
        Text(
            text = state.liveRpm?.toString() ?: "--",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                state.liveRpm == null -> TextTertiary
                state.inRange -> GreenSuccess
                else -> AmberSecondary
            },
            fontFamily = FontFamily.Default,
        )
        Text(
            text = "RPM",
            style = MaterialTheme.typography.labelLarge,
            color = TextTertiary,
        )

        Spacer(Modifier.height(16.dp))

        // Range indicator badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (state.inRange || state.skipValidation)
                GreenSuccess.copy(alpha = 0.12f)
            else
                AmberSecondary.copy(alpha = 0.12f),
        ) {
            Text(
                text = when {
                    state.skipValidation -> "Validation skipped"
                    state.inRange -> "In range ✓"
                    else -> "Adjust to ${state.stage.targetMin}–${state.stage.targetMax} RPM"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.inRange || state.skipValidation) GreenSuccess else AmberSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Time-in-range progress
        val progress = state.timeInRangeSec / state.stage.durationSec
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (state.inRange || state.skipValidation) GreenSuccess else DarkBorder,
            trackColor = DarkSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "%.1f / %ds in range".format(state.timeInRangeSec, state.stage.durationSec),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )

        Spacer(Modifier.weight(1f))

        // Action buttons
        if (!state.skipValidation) {
            OutlinedButton(
                state = state,
                onClick = onSkipValidation,
            )
            Spacer(Modifier.height(8.dp))
        }

        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = RedError),
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel Test", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun OutlinedButton(
    state: GuidedTestUiState.Running,
    onClick: () -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(TextTertiary.copy(alpha = 0.4f))
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
    ) {
        Text("Skip Validation", fontWeight = FontWeight.Medium)
    }
}

// ── Loading state (DTCs / Sending) ────────────────────────────────────────────

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = CyanPrimary,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Symptoms input ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomsContent(
    state: GuidedTestUiState.SymptomsInput,
    onToggleSymptom: (String) -> Unit,
    onFreeTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Test Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GreenSuccess,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextTertiary)
                }
            }
        }

        // DTC summary card
        if (state.storedDtcs.isNotEmpty() || state.pendingDtcs.isNotEmpty()) {
            item {
                DtcSummaryCard(storedDtcs = state.storedDtcs, pendingDtcs = state.pendingDtcs)
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GreenSuccess,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "No trouble codes found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GreenSuccess,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "What symptoms are you experiencing?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                COMMON_SYMPTOMS.forEach { symptom ->
                    val selected = symptom in state.selectedSymptoms
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleSymptom(symptom) },
                        label = { Text(symptom) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanContainer,
                            selectedLabelColor = CyanOnContainer,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = CyanPrimary.copy(alpha = 0.5f),
                            borderColor = DarkBorder,
                        ),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.freeText,
                onValueChange = onFreeTextChange,
                label = { Text("Additional notes (optional)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = CyanPrimary,
                    unfocusedLabelColor = TextTertiary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = CyanPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            )
        }

        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "Analyze with AI →",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sends test data + DTCs to your configured AI provider",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DtcSummaryCard(storedDtcs: List<String>, pendingDtcs: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RedError.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Trouble Codes Found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            if (storedDtcs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Stored: ${storedDtcs.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RedError,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (pendingDtcs.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pending: ${pendingDtcs.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Done state ────────────────────────────────────────────────────────────────

@Composable
private fun DoneContent(
    state: GuidedTestUiState.Done,
    onRunAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = if (state.hasApi) "AI Diagnosis" else "Test Complete",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (state.hasApi) CyanPrimary else TextPrimary,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
            ) {
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item {
            HorizontalDivider(color = DarkBorder)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Raw Payload",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 1.sp,
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E14)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.payloadJson,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanPrimary.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 10.sp,
                )
            }
        }

        item {
            Button(
                onClick = { clipboard.setText(AnnotatedString(state.payloadJson)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSurfaceHigh,
                    contentColor = TextPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Copy Raw Data", fontWeight = FontWeight.Medium)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onRunAgain,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = CyanPrimary),
                ) {
                    Text("Run Again", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Error / Cancelled states ──────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = RedError,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Test Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                Text("Back")
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = TextOnAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CancelledContent(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Test Cancelled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The guided test was stopped.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                Text("Back")
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = TextOnAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Try Again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
