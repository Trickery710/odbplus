package com.odbplus.app.expertdiag.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.expertdiag.engine.GuidedTestManager
import com.odbplus.app.expertdiag.engine.GuidedTestSession
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.app.expertdiag.model.DtcDiagnosticState
import com.odbplus.app.expertdiag.model.DtcSeverity
import com.odbplus.app.expertdiag.model.displayDescription
import com.odbplus.app.expertdiag.model.overallStatus
import com.odbplus.app.expertdiag.model.severityLabel
import com.odbplus.app.ui.theme.AmberSecondary
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.GreenSuccess
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@Composable
fun DtcExpandableTile(
    state: DtcDiagnosticState,
    activeGuidedSession: GuidedTestSession?,
    completedGuidedResults: List<DiagnosticResult>,
    onToggleExpand: () -> Unit,
    onStartGuidedTest: (testId: String) -> Unit,
    onCancelGuidedTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = severityBorderColor(state.kbEntry?.severity ?: DtcSeverity.MEDIUM)
    val chevronRotation by animateFloatAsState(
        targetValue = if (state.isExpanded) 90f else 0f,
        label = "chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
    ) {
        // ── Header row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            StatusDot(status = state.overallStatus)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.dtc.code,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SeverityBadge(severity = state.kbEntry?.severity ?: DtcSeverity.MEDIUM)
                    Spacer(modifier = Modifier.width(4.dp))
                    SystemBadge(system = state.dtc.system.displayName)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.displayDescription,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = if (state.isExpanded) Int.MAX_VALUE else 2,
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (state.isExpanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        // ── Expanded content ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // Common causes from KB
                if (state.kbEntry?.commonCauses?.isNotEmpty() == true) {
                    Text(
                        "COMMON CAUSES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    state.kbEntry.commonCauses.forEach { cause ->
                        Text(
                            "• $cause",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Auto test results
                AutoTestResultsList(
                    results = state.autoTestResults,
                    running = state.autoTestsRunning,
                )

                // Root cause probabilities
                if (state.rootCauseProbabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RootCauseProbabilitySection(probabilities = state.rootCauseProbabilities)
                }

                // Guided tests
                Spacer(modifier = Modifier.height(12.dp))
                GuidedTestSection(
                    availableTestIds = state.availableGuidedTestIds,
                    activeSession = if (state.activeGuidedTestId != null) activeGuidedSession else null,
                    completedResults = completedGuidedResults,
                    onStart = onStartGuidedTest,
                    onCancel = onCancelGuidedTest,
                )
            }
        }
    }
}

// ── Small reusable components ─────────────────────────────────────────────────

@Composable
private fun StatusDot(status: AutoTestStatus) {
    val color = when (status) {
        AutoTestStatus.PASS    -> GreenSuccess
        AutoTestStatus.FAIL    -> RedError
        AutoTestStatus.WARN    -> AmberSecondary
        AutoTestStatus.RUNNING -> CyanPrimary
        else                   -> TextTertiary
    }
    Icon(
        Icons.Filled.Circle,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(10.dp),
    )
}

@Composable
private fun SeverityBadge(severity: DtcSeverity) {
    val color = when (severity) {
        DtcSeverity.CRITICAL -> RedError
        DtcSeverity.HIGH     -> AmberSecondary
        DtcSeverity.MEDIUM   -> CyanPrimary
        DtcSeverity.LOW      -> GreenSuccess
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            severity.label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun SystemBadge(system: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(DarkSurfaceHigh)
            .border(1.dp, DarkBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(system, fontSize = 9.sp, color = TextTertiary)
    }
}

private fun severityBorderColor(severity: DtcSeverity): Color = when (severity) {
    DtcSeverity.CRITICAL -> RedError
    DtcSeverity.HIGH     -> AmberSecondary
    DtcSeverity.MEDIUM   -> CyanPrimary
    DtcSeverity.LOW      -> GreenSuccess
}
