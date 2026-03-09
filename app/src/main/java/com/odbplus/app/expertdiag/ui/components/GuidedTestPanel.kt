package com.odbplus.app.expertdiag.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.expertdiag.engine.GuidedTestSession
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanContainer
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextOnAccent
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@Composable
fun GuidedTestSection(
    availableTestIds: List<String>,
    activeSession: GuidedTestSession?,
    completedResults: List<DiagnosticResult>,
    onStart: (testId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availableTestIds.isEmpty() && completedResults.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "GUIDED TESTS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Active session panel
        AnimatedVisibility(
            visible = activeSession != null,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            if (activeSession != null) {
                ActiveSessionPanel(session = activeSession, onCancel = onCancel)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Start buttons for available tests (when no test running)
        if (activeSession == null) {
            availableTestIds.forEach { testId ->
                val prettyName = guidedTestDisplayName(testId)
                GuidedTestButton(
                    name = prettyName,
                    isRunning = false,
                    onClick = { onStart(testId) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Completed results
        completedResults.forEach { result ->
            GuidedResultSummaryCard(result = result)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ActiveSessionPanel(
    session: GuidedTestSession,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CyanContainer)
            .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CyanPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = session.testName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Step progress
        Text(
            text = "Step ${session.currentStepIndex + 1} / ${session.totalSteps}: ${session.currentStepName}",
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Step instruction
        Text(
            text = session.currentInstruction,
            fontSize = 12.sp,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Countdown
        Text(
            text = "⏱ ${session.remainingSeconds}s remaining",
            fontSize = 11.sp,
            color = AmberSecondary,
            fontWeight = FontWeight.Medium,
        )

        LinearProgressIndicator(
            progress = { 1f - (session.remainingSeconds.toFloat() / 60f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = CyanPrimary,
            trackColor = DarkBorder,
        )

        // Live PID values
        if (session.liveValues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("LIVE", fontSize = 9.sp, color = TextTertiary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            session.liveValues.entries.chunked(2).forEach { chunk ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 9.sp, color = TextTertiary)
                            Text(value, fontSize = 12.sp, color = CyanPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
            border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cancel Test", fontSize = 12.sp)
        }
    }
}

@Composable
private fun GuidedTestButton(
    name: String,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isRunning,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkSurfaceHigh,
            contentColor = CyanPrimary,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GuidedResultSummaryCard(result: DiagnosticResult) {
    val (color, label) = when (result.status) {
        DiagnosticStatus.PASS -> GreenSuccess to "PASS"
        DiagnosticStatus.FAIL -> RedError to "FAIL"
        DiagnosticStatus.WARNING -> AmberSecondary to "WARN"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(result.testName, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${result.confidenceScore}% conf",
                fontSize = 10.sp,
                color = TextTertiary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun guidedTestDisplayName(id: String): String = when (id) {
    "RPMSweepTest"          -> "RPM Sweep Test"
    "ThrottleSnapTest"      -> "Throttle Snap Test"
    "FuelPressureTest"      -> "Fuel Pressure Test"
    "EVAPLeakTest"          -> "EVAP Leak Test"
    "CylinderContributionTest" -> "Cylinder Contribution Test"
    else -> id.replace("([A-Z])".toRegex(), " $1").trim()
}
