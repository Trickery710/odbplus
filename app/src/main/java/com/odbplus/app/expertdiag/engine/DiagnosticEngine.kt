package com.odbplus.app.expertdiag.engine

import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.expertdiag.DiagnosticKnowledgeBase
import com.odbplus.app.expertdiag.model.AutoTestResult
import com.odbplus.app.expertdiag.model.AutoTestStatus
import com.odbplus.app.expertdiag.model.DtcDiagnosticState
import com.odbplus.core.protocol.DiagnosticTroubleCode
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level orchestrator for the Expert Diagnostic System.
 *
 * Responsibilities:
 *  - Maintain [DtcDiagnosticState] per active DTC
 *  - Dispatch [AutomaticTestRunner] when a session starts
 *  - Gate guided tests through [GuidedTestManager]
 *  - Trigger [DiagnosticResultAnalyzer] to produce probability scores
 *
 * Does NOT modify any existing OBD connection logic — works entirely via
 * [ObdService] which it receives by injection.
 */
@Singleton
class DiagnosticEngine @Inject constructor(
    private val knowledgeBase: DiagnosticKnowledgeBase,
    private val autoRunner: AutomaticTestRunner,
    private val guidedTestManager: GuidedTestManager,
    private val analyzer: DiagnosticResultAnalyzer,
    @AppScope private val appScope: CoroutineScope,
) {
    private val _dtcStates = MutableStateFlow<List<DtcDiagnosticState>>(emptyList())
    val dtcStates: StateFlow<List<DtcDiagnosticState>> = _dtcStates.asStateFlow()

    /** The guided test session in progress (null when idle). */
    val activeGuidedSession = guidedTestManager.activeSession

    private var autoTestJob: Job? = null

    // ── DTC lifecycle ─────────────────────────────────────────────────────────

    /**
     * Load a new set of active DTCs. Replaces any previous state.
     * Automatically dispatches automatic tests for each DTC.
     */
    fun loadDtcs(dtcs: List<DiagnosticTroubleCode>) {
        val newStates = dtcs.map { dtc ->
            val entry = knowledgeBase.lookup(dtc.code)
            DtcDiagnosticState(
                dtc = dtc,
                kbEntry = entry,
                availableGuidedTestIds = entry?.guidedTests ?: emptyList(),
            )
        }
        _dtcStates.value = newStates
        Timber.d("DiagnosticEngine: loaded ${dtcs.size} DTCs (${knowledgeBase.loadedCount} KB entries available)")
    }

    /**
     * Start automatic tests for all currently loaded DTCs.
     * Collects all unique test IDs across all DTCs and runs them once.
     */
    fun startAutomaticTests() {
        autoTestJob?.cancel()
        if (_dtcStates.value.isEmpty()) return

        // Collect unique auto test IDs across all DTC entries
        val testIds = _dtcStates.value
            .flatMap { it.kbEntry?.automaticTests ?: emptyList() }
            .distinct()

        if (testIds.isEmpty()) return

        // Mark all DTCs as running
        _dtcStates.value = _dtcStates.value.map { it.copy(autoTestsRunning = true) }

        autoTestJob = appScope.launch {
            val results = autoRunner.runAll(testIds)
            distributeAutoResults(results)
        }
    }

    /** Expand/collapse a DTC tile. */
    fun toggleExpand(dtcCode: String) {
        _dtcStates.value = _dtcStates.value.map { state ->
            if (state.dtc.code == dtcCode) state.copy(isExpanded = !state.isExpanded)
            else state
        }
    }

    /** Start a guided test for a specific DTC. */
    fun startGuidedTest(dtcCode: String, testId: String) {
        guidedTestManager.startTest(
            testId = testId,
            scope = appScope,
            onResult = { result -> onGuidedTestComplete(dtcCode, result) },
        )
        _dtcStates.value = _dtcStates.value.map { state ->
            if (state.dtc.code == dtcCode) state.copy(activeGuidedTestId = testId) else state
        }
    }

    fun cancelGuidedTest() {
        guidedTestManager.cancelCurrentTest()
        _dtcStates.value = _dtcStates.value.map { it.copy(activeGuidedTestId = null) }
    }

    /** Clear all state (e.g. on disconnect). */
    fun clear() {
        autoTestJob?.cancel()
        guidedTestManager.cancelCurrentTest()
        _dtcStates.value = emptyList()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Distribute auto test results back to each DTC that requested them,
     * then run the analyzer for each.
     */
    private fun distributeAutoResults(allResults: List<AutoTestResult>) {
        _dtcStates.value = _dtcStates.value.map { state ->
            val requestedIds = state.kbEntry?.automaticTests ?: emptyList()
            val relevant = allResults.filter { it.testId in requestedIds }
            val probabilities = state.kbEntry?.let {
                analyzer.analyze(it, relevant, state.guidedTestResults)
            } ?: emptyList()

            state.copy(
                autoTestsRunning = false,
                autoTestResults = relevant,
                rootCauseProbabilities = probabilities,
            )
        }
    }

    private fun onGuidedTestComplete(dtcCode: String, result: DiagnosticResult) {
        _dtcStates.value = _dtcStates.value.map { state ->
            if (state.dtc.code != dtcCode) return@map state
            val newGuidedResults = state.guidedTestResults + result
            val probabilities = state.kbEntry?.let {
                analyzer.analyze(it, state.autoTestResults, newGuidedResults)
            } ?: emptyList()

            state.copy(
                activeGuidedTestId = null,
                guidedTestResults = newGuidedResults,
                rootCauseProbabilities = probabilities,
            )
        }
    }
}
