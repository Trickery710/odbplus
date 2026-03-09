package com.odbplus.app.expertdiag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.app.expertdiag.model.overallStatus
import com.odbplus.app.expertdiag.ui.components.DtcExpandableTile
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertDiagnosticScreen(
    onBack: () -> Unit,
    viewModel: ExpertDiagViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Expert Diagnostics",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "AI-Powered DTC Analysis",
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (state.isSessionActive) {
                        IconButton(onClick = viewModel::loadDtcsAndRunTests) {
                            Icon(Icons.Filled.Refresh, "Re-run diagnostics", tint = CyanPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                ),
            )
        },
    ) { padding ->

        if (state.dtcStates.isEmpty()) {
            EmptyStateScreen(
                message = state.emptyMessage,
                isSessionActive = state.isSessionActive,
                onReadDtcs = viewModel::loadDtcsAndRunTests,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SummaryHeader(state = state)
            }

            items(
                items = state.dtcStates,
                key = { it.dtc.code },
            ) { dtcState ->
                DtcExpandableTile(
                    state = dtcState,
                    activeGuidedSession = state.activeGuidedSession,
                    completedGuidedResults = dtcState.guidedTestResults,
                    onToggleExpand = { viewModel.toggleExpand(dtcState.dtc.code) },
                    onStartGuidedTest = { testId ->
                        viewModel.startGuidedTest(dtcState.dtc.code, testId)
                    },
                    onCancelGuidedTest = viewModel::cancelGuidedTest,
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SummaryHeader(state: ExpertDiagUiState) {
    val failCount = state.dtcStates.count { it.overallStatus == AutoTestStatus.FAIL }
    val warnCount = state.dtcStates.count { it.overallStatus == AutoTestStatus.WARN }
    val passCount = state.dtcStates.count { it.overallStatus == AutoTestStatus.PASS }
    val runningCount = state.dtcStates.count { it.autoTestsRunning }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryItem("CODES", state.dtcStates.size.toString(), TextPrimary)
        SummaryDivider()
        SummaryItem("CRITICAL", failCount.toString(), if (failCount > 0) RedError else TextTertiary)
        SummaryDivider()
        SummaryItem("WARNINGS", warnCount.toString(), if (warnCount > 0) AmberSecondary else TextTertiary)
        SummaryDivider()
        if (runningCount > 0) {
            SummaryItem("TESTING", runningCount.toString(), CyanPrimary)
        } else {
            SummaryItem("CLEAR", passCount.toString(), if (passCount > 0) GreenSuccess else TextTertiary)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = TextTertiary, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SummaryDivider() {
    Text("│", color = com.odbplus.app.ui.theme.DarkBorder, fontSize = 18.sp)
}

@Composable
private fun EmptyStateScreen(
    message: String,
    isSessionActive: Boolean,
    onReadDtcs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (isSessionActive) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = onReadDtcs,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = com.odbplus.app.ui.theme.TextOnAccent,
                    ),
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Read DTCs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = TextTertiary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Waiting for ECU connection…", fontSize = 12.sp, color = TextTertiary)
                }
            }
        }
    }
}
