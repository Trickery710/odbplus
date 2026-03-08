package com.odbplus.app.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odbplus.app.ai.VehicleInfoRepository
import com.odbplus.app.data.db.dao.TestResultDao
import com.odbplus.app.data.db.entity.TestResultEntity
import com.odbplus.app.diagnostic.model.DiagnosticResult
import com.odbplus.app.diagnostic.model.DiagnosticStatus
import com.odbplus.app.diagnostic.model.DiagTestStep
import com.odbplus.core.protocol.ObdPid
import com.odbplus.core.protocol.ObdService
import com.odbplus.core.transport.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class DiagnosticUiState {

    /** Test selection grid. */
    data class TestList(val isConnected: Boolean) : DiagnosticUiState()

    /** Detail view for a selected test before it starts. */
    data class TestDetail(
        val test: DiagnosticTest,
        val isConnected: Boolean,
    ) : DiagnosticUiState()

    /** Test is actively running — live HUD. */
    data class Running(
        val test: DiagnosticTest,
        val stepIndex: Int,
        val step: DiagTestStep,
        val remainingSeconds: Int,
        val liveValues: Map<ObdPid, Double?>,
        val totalSteps: Int,
    ) : DiagnosticUiState()

    /** Test finished — structured diagnostic result. */
    data class Results(
        val test: DiagnosticTest,
        val result: DiagnosticResult,
    ) : DiagnosticUiState()

    /** Unrecoverable test error. */
    data class Error(
        val message: String,
        val test: DiagnosticTest,
    ) : DiagnosticUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val obdService: ObdService,
    private val testResultDao: TestResultDao,
    private val vehicleInfoRepository: VehicleInfoRepository
) : ViewModel() {

    val availableTests: List<DiagnosticTest> = DiagnosticTestRegistry.tests

    private val _uiState = MutableStateFlow<DiagnosticUiState>(
        DiagnosticUiState.TestList(isConnected = false)
    )
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private val orchestrator = DiagnosticOrchestrator(obdService)
    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            obdService.connectionState.collect { state ->
                val connected = state == ConnectionState.CONNECTED
                when (val current = _uiState.value) {
                    is DiagnosticUiState.TestList ->
                        _uiState.value = current.copy(isConnected = connected)
                    is DiagnosticUiState.TestDetail ->
                        _uiState.value = current.copy(isConnected = connected)
                    else -> {}
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun selectTest(test: DiagnosticTest) {
        _uiState.value = DiagnosticUiState.TestDetail(
            test = test,
            isConnected = obdService.connectionState.value == ConnectionState.CONNECTED,
        )
    }

    fun backToList() {
        testJob?.cancel()
        _uiState.value = DiagnosticUiState.TestList(
            isConnected = obdService.connectionState.value == ConnectionState.CONNECTED,
        )
    }

    // ── Test execution ────────────────────────────────────────────────────────

    fun startTest(test: DiagnosticTest) {
        testJob?.cancel()
        val steps = test.getSteps()

        testJob = viewModelScope.launch {
            try {
                val samples = orchestrator.runTest(
                    test = test,
                    onStepStart = { stepIndex ->
                        _uiState.value = DiagnosticUiState.Running(
                            test = test,
                            stepIndex = stepIndex,
                            step = steps[stepIndex],
                            remainingSeconds = steps[stepIndex].durationSeconds,
                            liveValues = emptyMap(),
                            totalSteps = steps.size,
                        )
                    },
                    onProgress = { stepIndex, remainingSeconds, liveValues ->
                        _uiState.value = DiagnosticUiState.Running(
                            test = test,
                            stepIndex = stepIndex,
                            step = steps[stepIndex],
                            remainingSeconds = remainingSeconds,
                            liveValues = liveValues,
                            totalSteps = steps.size,
                        )
                    },
                )
                val result = test.analyze(samples)
                _uiState.value = DiagnosticUiState.Results(test = test, result = result)
                persistTestResult(test.name, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DiagnosticUiState.Error(
                    message = e.message ?: "Test failed unexpectedly",
                    test = test,
                )
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        _uiState.value = DiagnosticUiState.TestList(
            isConnected = obdService.connectionState.value == ConnectionState.CONNECTED,
        )
    }

    private fun persistTestResult(testName: String, result: DiagnosticResult) {
        val vin = vehicleInfoRepository.currentVehicle.value?.vin ?: return
        viewModelScope.launch {
            val resultStr = when (result.status) {
                DiagnosticStatus.PASS -> "PASS"
                DiagnosticStatus.WARNING -> "WARNING"
                DiagnosticStatus.FAIL -> "FAIL"
            }
            val notes = result.findings.joinToString("; ") { it.title }
            testResultDao.insert(
                TestResultEntity(
                    vin = vin,
                    testName = testName,
                    result = resultStr,
                    notes = notes.ifBlank { null }
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
