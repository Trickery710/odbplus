package com.odbplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.diagnostic.DiagnosticTest
import com.odbplus.app.diagnostic.DiagnosticUiState
import com.odbplus.app.diagnostic.DiagnosticViewModel
import com.odbplus.app.diagnostic.model.DiagnosticFinding
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.core.protocol.ObdPid
import com.odbplus.app.ui.theme.AmberContainer
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.CyanContainer
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenContainer
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedContainer
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticHudScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val topBarTitle = when (uiState) {
        is DiagnosticUiState.TestList -> "Diagnostics"
        is DiagnosticUiState.TestDetail -> (uiState as DiagnosticUiState.TestDetail).test.name
        is DiagnosticUiState.Running -> (uiState as DiagnosticUiState.Running).test.name
        is DiagnosticUiState.Results -> "Results"
        is DiagnosticUiState.Error -> "Error"
    }

    val onNavBack: () -> Unit = when (uiState) {
        is DiagnosticUiState.TestList -> onBack
        is DiagnosticUiState.Running -> { { viewModel.cancelTest() } }
        else -> { { viewModel.backToList() } }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        topBarTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyanPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface),
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is DiagnosticUiState.TestList ->
                TestListContent(
                    tests = viewModel.availableTests,
                    isConnected = state.isConnected,
                    onSelect = viewModel::selectTest,
                    modifier = Modifier.padding(padding),
                )

            is DiagnosticUiState.TestDetail ->
                TestDetailContent(
                    test = state.test,
                    isConnected = state.isConnected,
                    onStart = { viewModel.startTest(state.test) },
                    modifier = Modifier.padding(padding),
                )

            is DiagnosticUiState.Running ->
                RunningContent(
                    state = state,
                    onCancel = viewModel::cancelTest,
                    modifier = Modifier.padding(padding),
                )

            is DiagnosticUiState.Results ->
                ResultsContent(
                    state = state,
                    onRunAgain = { viewModel.startTest(state.test) },
                    onDone = viewModel::backToList,
                    modifier = Modifier.padding(padding),
                )

            is DiagnosticUiState.Error ->
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.startTest(state.test) },
                    onBack = viewModel::backToList,
                    modifier = Modifier.padding(padding),
                )
        }
    }
}

// ── Test list ─────────────────────────────────────────────────────────────────

@Composable
private fun TestListContent(
    tests: List<DiagnosticTest>,
    isConnected: Boolean,
    onSelect: (DiagnosticTest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Connection banner
        if (!isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmberContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, null, tint = AmberSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connect to a vehicle to run tests",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberSecondary,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tests.size) { index ->
                DiagnosticTestCard(
                    test = tests[index],
                    isEnabled = isConnected,
                    onClick = { onSelect(tests[index]) },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticTestCard(
    test: DiagnosticTest,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (isEnabled) CyanPrimary else TextTertiary
    val cardBg = DarkSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (isEnabled) Modifier.border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = if (isEnabled) 1f else 0.6f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(test.icon, null, modifier = Modifier.size(26.dp), tint = iconColor)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                test.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (isEnabled) TextPrimary else TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                test.description.split(".").first(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isEnabled) TextSecondary else TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            if (!isEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connect first",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberSecondary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Test detail ───────────────────────────────────────────────────────────────

@Composable
private fun TestDetailContent(
    test: DiagnosticTest,
    isConnected: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Description card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(test.icon, null, modifier = Modifier.size(22.dp), tint = CyanPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            test.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        test.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetaBadge("${test.getSteps().size} steps")
                        MetaBadge("${test.getRequiredPids().size} PIDs")
                        val totalSec = test.getSteps().sumOf { it.durationSeconds }
                        MetaBadge("~${totalSec}s")
                    }
                }
            }
        }

        // Steps list
        item {
            Text(
                "TEST STEPS",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        items(test.getSteps().size) { index ->
            val step = test.getSteps()[index]
            StepPreviewRow(stepNumber = index + 1, step = step)
        }

        // Start button
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = TextOnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextTertiary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Start Test", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (!isConnected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vehicle connection required to run this test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Box(
        modifier = Modifier
            .background(CyanContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
    }
}

@Composable
private fun StepPreviewRow(stepNumber: Int, step: DiagTestStep) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape)
                .background(DarkSurfaceHigh)
                .border(1.dp, DarkBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$stepNumber",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    step.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    "${step.durationSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                step.instruction,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
        }
    }
}

// ── Running HUD ───────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(
    state: DiagnosticUiState.Running,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(DarkBackground),
    ) {
        // Step progress strip
        StepProgressBar(
            currentStep = state.stepIndex,
            totalSteps = state.totalSteps,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Step instruction card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, CyanPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "STEP ${state.stepIndex + 1} OF ${state.totalSteps}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            CountdownChip(seconds = state.remainingSeconds)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.step.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.step.instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            // Target RPM card
            if (state.step.targetRpm != null) {
                item {
                    TargetRpmCard(
                        targetRpm = state.step.targetRpm,
                        currentRpm = state.liveValues[ObdPid.ENGINE_RPM]?.toInt(),
                    )
                }
            }

            // Live PID values
            if (state.liveValues.isNotEmpty()) {
                item {
                    Text(
                        "LIVE SENSOR DATA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                item {
                    LivePidGrid(liveValues = state.liveValues)
                }
            }

            // Cancel
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(RedError.copy(alpha = 0.5f))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                ) {
                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel Test", fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StepProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = CyanPrimary,
            trackColor = DarkSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(totalSteps) { index ->
                StepDot(index = index, currentStep = currentStep, total = totalSteps)
            }
        }
    }
}

@Composable
private fun StepDot(index: Int, currentStep: Int, total: Int) {
    val isCurrent = index == currentStep
    val isDone = index < currentStep
    val dotColor = when {
        isDone -> GreenSuccess
        isCurrent -> CyanPrimary
        else -> DarkSurfaceVariant
    }
    val borderColor = when {
        isDone -> GreenSuccess
        isCurrent -> CyanPrimary
        else -> DarkBorder
    }
    Box(
        modifier = Modifier
            .size(if (isCurrent) 12.dp else 10.dp)
            .clip(CircleShape)
            .background(dotColor)
            .border(1.dp, borderColor, CircleShape),
    )
}

@Composable
private fun CountdownChip(seconds: Int) {
    val color = when {
        seconds <= 3 -> RedError
        seconds <= 5 -> AmberSecondary
        else -> CyanPrimary
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            "${seconds}s",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

@Composable
private fun TargetRpmCard(targetRpm: Int, currentRpm: Int?) {
    val inRange = currentRpm != null &&
            currentRpm in (targetRpm - targetRpm / 5)..(targetRpm + targetRpm / 5)
    val indicatorColor = when {
        currentRpm == null -> TextTertiary
        inRange -> GreenSuccess
        else -> AmberSecondary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceHigh),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, indicatorColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "TARGET RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${targetRpm} RPM",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "CURRENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    currentRpm?.let { "${it} RPM" } ?: "---",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = indicatorColor,
                )
            }
        }
    }
}

@Composable
private fun LivePidGrid(liveValues: Map<ObdPid, Double?>) {
    val pidList = liveValues.entries.toList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pidList.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (pid, value) ->
                    LivePidCell(pid = pid, value = value, modifier = Modifier.weight(1f))
                }
                // Fill empty slot if odd count
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LivePidCell(pid: ObdPid, value: Double?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceHigh),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                pid.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value?.let { "%.1f".format(it) } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (value != null) CyanPrimary else TextTertiary,
                )
                if (value != null && pid.unit.isNotBlank()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        pid.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

// ── Results ───────────────────────────────────────────────────────────────────

@Composable
private fun ResultsContent(
    state: DiagnosticUiState.Results,
    onRunAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusBanner(result = state.result) }

        if (state.result.detectedIssues.isNotEmpty()) {
            item {
                SectionHeader("DETECTED ISSUES")
            }
            item {
                IssuesList(items = state.result.detectedIssues, color = RedError)
            }
        }

        item { SectionHeader("FINDINGS") }
        items(state.result.findings.size) { index ->
            FindingCard(finding = state.result.findings[index])
        }

        if (state.result.possibleCauses.isNotEmpty()) {
            item { SectionHeader("POSSIBLE CAUSES") }
            item { IssuesList(items = state.result.possibleCauses, color = AmberSecondary) }
        }

        item { SectionHeader("RECOMMENDED CHECKS") }
        item {
            ChecksList(items = state.result.recommendedChecks)
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                ) {
                    Text("Done")
                }
                Button(
                    onClick = onRunAgain,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Run Again", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusBanner(result: DiagnosticResult) {
    val (bgColor, borderColor, iconColor, label) = when (result.status) {
        DiagnosticStatus.PASS -> StatusColors(GreenContainer, GreenSuccess.copy(alpha = 0.4f), GreenSuccess, "PASS")
        DiagnosticStatus.WARNING -> StatusColors(AmberContainer, AmberSecondary.copy(alpha = 0.4f), AmberSecondary, "WARNING")
        DiagnosticStatus.FAIL -> StatusColors(RedContainer, RedError.copy(alpha = 0.4f), RedError, "FAULT")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (result.status) {
                    DiagnosticStatus.PASS -> Icons.Filled.CheckCircle
                    DiagnosticStatus.WARNING -> Icons.Filled.Warning
                    DiagnosticStatus.FAIL -> Icons.Filled.Warning
                },
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = iconColor,
                    letterSpacing = 2.sp,
                )
                Text(
                    result.testName,
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.weight(1f))
            ConfidenceBadge(score = result.confidenceScore)
        }
    }
}

private data class StatusColors(
    val bg: Color,
    val border: Color,
    val icon: Color,
    val label: String,
)

@Composable
private fun ConfidenceBadge(score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$score%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
        Text(
            "confidence",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 14.dp).background(CyanPrimary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun FindingCard(finding: DiagnosticFinding) {
    val accentColor = when (finding.status) {
        DiagnosticStatus.PASS -> GreenSuccess
        DiagnosticStatus.WARNING -> AmberSecondary
        DiagnosticStatus.FAIL -> RedError
    }
    val bgColor = when (finding.status) {
        DiagnosticStatus.PASS -> GreenContainer.copy(alpha = 0.6f)
        DiagnosticStatus.WARNING -> AmberContainer.copy(alpha = 0.6f)
        DiagnosticStatus.FAIL -> RedContainer.copy(alpha = 0.8f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .align(Alignment.Top)
                .padding(top = 5.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                finding.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                finding.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun IssuesList(items: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    item,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun ChecksList(items: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        lineHeight = 18.sp,
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(RedError.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, null, modifier = Modifier.size(36.dp), tint = RedError)
        }
        Spacer(Modifier.height(20.dp))
        Text("Test Error", style = MaterialTheme.typography.headlineSmall, color = RedError, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) {
                Text("Back", color = TextSecondary)
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = TextOnAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
