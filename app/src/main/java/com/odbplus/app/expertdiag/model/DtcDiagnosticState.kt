package com.odbplus.app.expertdiag.model

import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.core.protocol.DiagnosticTroubleCode

/**
 * Full diagnostic state for a single active DTC tile.
 *
 * Immutable snapshot updated by [DiagnosticEngine] via StateFlow emissions.
 */
data class DtcDiagnosticState(
    val dtc: DiagnosticTroubleCode,
    val kbEntry: KnowledgeBaseEntry?,
    val isExpanded: Boolean = false,

    // ── Automatic tests ───────────────────────────────────────────────────
    val autoTestsRunning: Boolean = false,
    val autoTestResults: List<AutoTestResult> = emptyList(),

    // ── Guided tests ──────────────────────────────────────────────────────
    val availableGuidedTestIds: List<String> = emptyList(),
    val activeGuidedTestId: String? = null,
    val guidedTestResults: List<DiagnosticResult> = emptyList(),

    // ── Analysis ──────────────────────────────────────────────────────────
    val rootCauseProbabilities: List<RootCauseProbability> = emptyList(),
)

val DtcDiagnosticState.displayDescription: String
    get() = kbEntry?.description ?: dtc.description ?: "Unknown fault"

val DtcDiagnosticState.severityLabel: String
    get() = kbEntry?.severity?.label ?: "Unknown"

val DtcDiagnosticState.overallStatus: AutoTestStatus
    get() = when {
        autoTestsRunning -> AutoTestStatus.RUNNING
        autoTestResults.any { it.status == AutoTestStatus.FAIL } -> AutoTestStatus.FAIL
        autoTestResults.any { it.status == AutoTestStatus.WARN } -> AutoTestStatus.WARN
        autoTestResults.isNotEmpty() && autoTestResults.all { it.status == AutoTestStatus.PASS } -> AutoTestStatus.PASS
        else -> AutoTestStatus.SKIPPED
    }
