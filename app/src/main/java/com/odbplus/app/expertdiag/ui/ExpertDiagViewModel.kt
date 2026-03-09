package com.odbplus.app.expertdiag.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.expertdiag.engine.DiagnosticEngine
import com.odbplus.app.expertdiag.engine.GuidedTestSession
import com.odbplus.app.expertdiag.model.DtcDiagnosticState
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.protocol.adapter.ProtocolSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpertDiagUiState(
    val dtcStates: List<DtcDiagnosticState> = emptyList(),
    val activeGuidedSession: GuidedTestSession? = null,
    val isSessionActive: Boolean = false,
    val isLoading: Boolean = false,
    val emptyMessage: String = "Connect to ECU and read DTCs to begin diagnostics",
)

@HiltViewModel
class ExpertDiagViewModel @Inject constructor(
    private val engine: DiagnosticEngine,
    private val obdService: ObdService,
) : ViewModel() {

    val uiState: StateFlow<ExpertDiagUiState> = combine(
        engine.dtcStates,
        engine.activeGuidedSession,
        obdService.sessionState,
    ) { dtcStates, guidedSession, sessionState ->
        val sessionActive = sessionState == ProtocolSessionState.SESSION_ACTIVE ||
                            sessionState == ProtocolSessionState.STREAMING
        ExpertDiagUiState(
            dtcStates = dtcStates,
            activeGuidedSession = guidedSession,
            isSessionActive = sessionActive,
            emptyMessage = when {
                !sessionActive -> "Connect to ECU to enable automatic diagnostics"
                dtcStates.isEmpty() -> "No DTCs stored — vehicle systems appear healthy"
                else -> ""
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpertDiagUiState(),
    )

    init {
        // Whenever session becomes active, load DTCs and run auto tests
        viewModelScope.launch {
            obdService.sessionState.collect { state ->
                    if (state == ProtocolSessionState.SESSION_ACTIVE) {
                        loadDtcsAndRunTests()
                    }
                }
        }
    }

    fun loadDtcsAndRunTests() {
        viewModelScope.launch {
            try {
                val stored = obdService.readStoredDtcs()
                val pending = obdService.readPendingDtcs()
                val all = (stored + pending).distinctBy { it.code }
                engine.loadDtcs(all)
                if (all.isNotEmpty()) {
                    engine.startAutomaticTests()
                }
            } catch (e: Exception) {
                // Not connected — silently ignore
            }
        }
    }

    fun rerunAutomaticTests() {
        engine.startAutomaticTests()
    }

    fun toggleExpand(dtcCode: String) {
        engine.toggleExpand(dtcCode)
    }

    fun startGuidedTest(dtcCode: String, testId: String) {
        engine.startGuidedTest(dtcCode, testId)
    }

    fun cancelGuidedTest() {
        engine.cancelGuidedTest()
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancelGuidedTest()
    }
}
